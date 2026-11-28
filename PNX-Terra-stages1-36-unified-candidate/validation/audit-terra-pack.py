#!/usr/bin/env python3
"""Build a source-pinned Terra ConfigPack inventory; does not claim bounds are certified."""
from __future__ import annotations

import argparse
import json
import re
import subprocess
from collections import Counter, defaultdict
from pathlib import Path

STRUCTURES_KEY = re.compile(r"^(?P<indent>[ \t]*)structures:[ \t]*(?P<value>.*?)\s*$")
STRUCTURE_LIST_ITEM = re.compile(r"^[ \t]+-\s*([A-Za-z0-9_.:/-]+)\s*:")
ANCHOR_SCALAR = re.compile(r"&([A-Za-z0-9_-]+)[ \t]+([A-Za-z0-9_.:/-]+)")
NESTED_LITERAL = re.compile(r"""\b(?:structure|placeStructure|place_structure)\s*\(\s*(['"])(.*?)\1""")
NESTED_CALL = re.compile(r"\b(?:structure|placeStructure|place_structure)\s*\(")
BLOCK_WRITE = re.compile(r"\bblock\s*\(")
BLOCK_READ = re.compile(r"\b(?:getBlock|blockState|check)\s*\(")
LOOP = re.compile(r"\bfor\s*\(")
BLOCK_TARGET = re.compile(r"BLOCK:[A-Za-z0-9_.:/-]+(?:\[[^]]+\])?")
YAML_FIELD = {
    "id": re.compile(r"(?m)^id:\s*([A-Za-z0-9_.:/-]+)\s*$"),
    "type": re.compile(r"(?m)^type:\s*([A-Za-z0-9_.:/-]+)\s*$"),
    "extends": re.compile(r"(?m)^extends:\s*([A-Za-z0-9_.:/-]+)\s*$"),
    "size": re.compile(r"(?m)^size:\s*([0-9]+)\s*$"),
}


def git_head(root: Path) -> str:
    try:
        return subprocess.check_output(
            ["git", "-C", str(root), "rev-parse", "HEAD"], text=True
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def extract_feature_targets(source: str) -> tuple[set[str], set[str], bool]:
    anchor_scalars = {name: value for name, value in ANCHOR_SCALAR.findall(source)}
    anchor_maps: dict[str, set[str]] = {}
    lines = source.splitlines()
    for index, line in enumerate(lines):
        match = re.search(r"&([A-Za-z0-9_-]+)[ \t]*$", line)
        if not match:
            continue
        name = match.group(1)
        base_indent = len(line) - len(line.lstrip(" \t"))
        values: set[str] = set()
        for child in lines[index + 1:]:
            if not child.strip() or child.lstrip().startswith("#"):
                continue
            child_indent = len(child) - len(child.lstrip(" \t"))
            if child_indent <= base_indent:
                break
            item = re.match(r"^[ \t]+([A-Za-z0-9_.:/-]+):[ \t]*[^#]+$", child)
            if item:
                values.add(item.group(1))
        if values:
            anchor_maps[name] = values

    roots: set[str] = set()
    block_targets: set[str] = set()
    saw_structures_key = False
    unresolved = False

    def add_scalar(raw: str) -> None:
        nonlocal unresolved
        value = raw.split("#", 1)[0].strip()
        if not value:
            return
        if value.startswith("*"):
            alias = value[1:]
            if alias in anchor_maps:
                roots.update(anchor_maps[alias])
                return
            value = anchor_scalars.get(alias, "")
            if not value:
                unresolved = True
                return
        if value.startswith("BLOCK:"):
            block_targets.add(value)
        elif re.fullmatch(r"[A-Za-z0-9_.:/-]+", value):
            roots.add(value)
        else:
            unresolved = True

    for index, line in enumerate(lines):
        match = STRUCTURES_KEY.match(line)
        if not match:
            continue
        saw_structures_key = True
        indent = len(match.group("indent").expandtabs(8))
        value = match.group("value").strip()
        if value:
            add_scalar(value)
            continue

        for child in lines[index + 1:]:
            if child.strip() and not child.lstrip().startswith("#"):
                child_indent = len(child) - len(child.lstrip(" \t"))
                if child_indent <= indent:
                    break
            nested_key = STRUCTURES_KEY.match(child)
            if nested_key and nested_key.group("value").strip():
                add_scalar(nested_key.group("value").strip())
            item = STRUCTURE_LIST_ITEM.match(child)
            if item:
                item_id = item.group(1)
                if item_id.startswith("BLOCK:"):
                    block_targets.update(BLOCK_TARGET.findall(child))
                else:
                    roots.add(item_id)
            block_targets.update(BLOCK_TARGET.findall(child))

    return roots, block_targets, unresolved or (saw_structures_key and not roots and not block_targets)


def parse_structure_yaml(path: Path, root: Path) -> dict | None:
    source = path.read_text(encoding="utf-8")
    record = {"path": path.relative_to(root).as_posix(), "status": "UNKNOWN"}
    for field, pattern in YAML_FIELD.items():
        match = pattern.search(source)
        if match:
            record[field] = match.group(1)
    if "id" not in record:
        return None
    record["abstract"] = bool(re.search(r"(?m)^abstract:\s*true\s*$", source))
    return record


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--expected-head")
    args = parser.parse_args()
    root = args.root.resolve()
    if not root.is_dir():
        raise SystemExit(f"ConfigPack root does not exist: {root}")

    actual_head = git_head(root)
    if args.expected_head and actual_head != args.expected_head:
        raise SystemExit(f"ConfigPack HEAD mismatch: expected {args.expected_head}, got {actual_head}")

    scripts = sorted(root.rglob("*.tesf"))
    structure_yamls = sorted(root.glob("structures/**/*.yml"))
    features = sorted(p for p in root.rglob("*.yml") if "features" in p.relative_to(root).parts)

    scripts_by_id: dict[str, list[str]] = defaultdict(list)
    script_records = []
    for path in scripts:
        rel = path.relative_to(root).as_posix()
        script_id = path.stem
        scripts_by_id[script_id.lower()].append(rel)
        source = path.read_text(encoding="utf-8")
        nested = NESTED_LITERAL.findall(source)
        nested_ids = sorted({value for _, value in nested})
        calls = len(NESTED_CALL.findall(source))
        script_records.append({
            "id": script_id,
            "path": rel,
            "status": "UNKNOWN",
            "bounds": None,
            "root_feature_references": [],
            "nested_static_ids": nested_ids,
            "nested_call_count": calls,
            "dynamic_nested_target": calls > len(nested),
            "loop_count": len(LOOP.findall(source)),
            "block_write_call_count": len(BLOCK_WRITE.findall(source)),
            "block_read_or_check_call_count": len(BLOCK_READ.findall(source)),
            "uses_random_or_math": bool(re.search(r"\b(random|randomInt|sampler|pow|sqrt)\b", source)),
            "notes": "Inventory only. No static bounds are certified by this script.",
        })

    structure_definitions = [
        record for path in structure_yamls
        if (record := parse_structure_yaml(path, root)) is not None
    ]
    config_ids = {record["id"].lower(): record for record in structure_definitions}

    root_refs = defaultdict(list)
    unresolved_features = []
    block_state_targets = set()
    feature_records = []
    for path in features:
        rel = path.relative_to(root).as_posix()
        source = path.read_text(encoding="utf-8")
        ids, blocks, unresolved = extract_feature_targets(source)
        if unresolved:
            unresolved_features.append(rel)
        block_state_targets.update(blocks)
        feature_records.append({
            "path": rel,
            "structure_ids": sorted(ids),
            "block_state_targets": sorted(blocks),
        })
        for structure_id in ids:
            root_refs[structure_id].append(rel)

    root_refs_lower = defaultdict(list)
    for structure_id, paths in root_refs.items():
        root_refs_lower[structure_id.lower()].extend(paths)
    for record in script_records:
        record["root_feature_references"] = sorted(set(root_refs_lower.get(record["id"].lower(), [])))
        record["has_root_feature_reference"] = bool(record["root_feature_references"])
    for record in structure_definitions:
        record["root_feature_references"] = sorted(set(root_refs_lower.get(record["id"].lower(), [])))
        record["has_root_feature_reference"] = bool(record["root_feature_references"])

    script_matches = sum(1 for key in root_refs if key.lower() in scripts_by_id)
    config_matches = sum(1 for key in root_refs if key.lower() in config_ids)
    missing = sorted(
        key for key in root_refs
        if key.lower() not in scripts_by_id and key.lower() not in config_ids
    )
    duplicate_ids = {
        script_id: paths for script_id, paths in sorted(scripts_by_id.items()) if len(paths) > 1
    }
    status_counts = Counter(record["status"] for record in script_records)
    summary = {
        "config_pack": "PolyhedralDev/TerraOverworldConfig",
        "git_head": actual_head,
        "inventory_policy": "Inventory only; every TerraScript and YAML structure remains UNKNOWN until separately audited.",
        "terra_script_count": len(scripts),
        "structure_yaml_definition_count": len(structure_definitions),
        "feature_yaml_count": len(features),
        "feature_structure_reference_count": sum(len(v) for v in root_refs.values()),
        "unique_feature_root_ids": len(root_refs),
        "matched_script_roots": script_matches,
        "matched_yaml_structure_roots": config_matches,
        "unmatched_feature_root_ids": len(missing),
        "block_state_target_count": len(block_state_targets),
        "unresolved_feature_yaml_count": len(unresolved_features),
        "duplicate_script_id_count": len(duplicate_ids),
        "dynamic_nested_target_script_count": sum(1 for r in script_records if r["dynamic_nested_target"]),
        "status_counts": dict(sorted(status_counts.items())),
    }
    output = {
        "format": "terra-configpack-source-inventory",
        "version": 2,
        "summary": summary,
        "scripts": script_records,
        "structure_definitions": structure_definitions,
        "features": feature_records,
        "unmatched_feature_root_ids": missing,
        "block_state_targets": sorted(block_state_targets),
        "unresolved_feature_yaml": unresolved_features,
        "duplicate_script_ids": duplicate_ids,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(summary, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
