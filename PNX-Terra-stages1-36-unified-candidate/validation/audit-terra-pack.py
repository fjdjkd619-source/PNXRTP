#!/usr/bin/env python3
"""Build a source-pinned Terra ConfigPack and PNX block-state inventory."""
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
BLOCK_LITERAL = re.compile(r"minecraft:[a-z0-9_./-]+(?:\[[A-Za-z0-9_./-]+=[A-Za-z0-9_./-]+(?:,[A-Za-z0-9_./-]+=[A-Za-z0-9_./-]+)*\])?")
DYNAMIC_BLOCK_STATE = re.compile(r"minecraft:[a-z0-9_./-]+\[[^]\r\n]*[\"']\s*\+")
ENTITY_LITERAL_CONTEXT = re.compile(r"entity_data|type=minecraft:")
BLOCK_CALL_LINE = re.compile(r"\bblock\s*\(")

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
        match = re.search(r"&([A-Za-z0-9_-]+)(?:[ \t]+#.*)?$", line)
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
                for mapped_id in anchor_maps[alias]:
                    if mapped_id.startswith("BLOCK:"):
                        block_targets.add(mapped_id)
                    elif mapped_id != "blank":
                        roots.add(mapped_id)
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
                elif item_id != "blank":
                    roots.add(item_id)
            block_targets.update(BLOCK_TARGET.findall(child))

    return roots, block_targets, unresolved or (saw_structures_key and not roots and not block_targets)


def parse_block_state(value: str) -> tuple[str, dict[str, str] | None]:
    if "[" not in value:
        return value, {}
    base, raw = value.split("[", 1)
    if not raw.endswith("]"):
        return base, None
    properties: dict[str, str] = {}
    for item in raw[:-1].split(","):
        if "=" not in item:
            return base, None
        name, property_value = item.split("=", 1)
        properties[name] = property_value
    return base, properties


def normalize_block_state(value: str) -> str:
    if "[" not in value:
        return value
    base, state = value.split("[", 1)
    if not state.endswith("]"):
        return value
    properties = state[:-1].split(",")
    if not all("=" in item for item in properties):
        return value
    return base + "[" + ",".join(sorted(properties, key=lambda item: item.split("=", 1)[0])) + "]"


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
    parser.add_argument("--expected-head", required=True)
    parser.add_argument("--pnx-root", required=True, type=Path)
    parser.add_argument("--pnx-expected-head", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    pnx_root = args.pnx_root.resolve()
    if not root.is_dir() or not pnx_root.is_dir():
        raise SystemExit("ConfigPack and PNX checkout directories must exist")

    actual_head = git_head(root)
    if actual_head != args.expected_head:
        raise SystemExit(f"ConfigPack HEAD mismatch: expected {args.expected_head}, got {actual_head}")
    pnx_head = git_head(pnx_root)
    if pnx_head != args.pnx_expected_head:
        raise SystemExit(f"PNX HEAD mismatch: expected {args.pnx_expected_head}, got {pnx_head}")

    mapping_path = pnx_root / "src/main/resources/mappings/blocks.json"
    if not mapping_path.is_file():
        raise SystemExit(f"Pnx block mapping registry missing: {mapping_path}")
    pnx_mapping = json.loads(mapping_path.read_text(encoding="utf-8"))
    pnx_state_keys = set(pnx_mapping)
    pnx_normalized_keys = {normalize_block_state(value) for value in pnx_state_keys}
    pnx_block_ids = {value.split("[", 1)[0] for value in pnx_state_keys}

    scripts = sorted(root.rglob("*.tesf"))
    all_yamls = sorted(root.rglob("*.yml"))
    structure_yamls = sorted(root.glob("structures/**/*.yml"))
    palette_yamls = sorted(root.glob("palettes/**/*.yml"))
    features = sorted(p for p in all_yamls if "features" in p.relative_to(root).parts)
    scripts_by_id: dict[str, list[str]] = defaultdict(list)
    script_records = []
    block_sources: dict[str, set[str]] = defaultdict(set)
    entity_literals: set[str] = set()
    block_call_literals: set[str] = set()
    dynamic_block_sources: dict[str, set[str]] = defaultdict(set)

    def record_block_literals(path: Path, source: str) -> None:
        rel = path.relative_to(root).as_posix()
        for line in source.splitlines():
            if line.lstrip().startswith("//") or line.lstrip().startswith("#"):
                continue
            for state in BLOCK_LITERAL.findall(line):
                block_sources[state].add(rel)
            if ENTITY_LITERAL_CONTEXT.search(line):
                entity_literals.update(re.findall(r"minecraft:[a-z0-9_./-]+", line))
            if BLOCK_CALL_LINE.search(line):
                block_call_literals.update(BLOCK_LITERAL.findall(line))
            for state in DYNAMIC_BLOCK_STATE.findall(line):
                dynamic_block_sources[state].add(rel)

    for path in scripts:
        rel = path.relative_to(root).as_posix()
        script_id = path.stem
        scripts_by_id[script_id.lower()].append(rel)
        source = path.read_text(encoding="utf-8")
        record_block_literals(path, source)
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

    for path in palette_yamls:
        record_block_literals(path, path.read_text(encoding="utf-8"))

    structure_definitions = []
    for path in structure_yamls:
        source = path.read_text(encoding="utf-8")
        record_block_literals(path, source)
        record = parse_structure_yaml(path, root)
        if record is not None:
            structure_definitions.append(record)

    config_ids = {record["id"].lower(): record for record in structure_definitions}
    root_refs = defaultdict(list)
    unresolved_features = []
    block_state_targets = set()
    feature_records = []
    for path in features:
        rel = path.relative_to(root).as_posix()
        source = path.read_text(encoding="utf-8")
        record_block_literals(path, source)
        ids, blocks, unresolved = extract_feature_targets(source)
        is_abstract = bool(re.search(r"(?m)^abstract:\\s*true\\s*$", source))
        if unresolved and not is_abstract:
            unresolved_features.append(rel)
        block_state_targets.update(blocks)
        feature_records.append({
            "path": rel,
            "abstract": is_abstract,
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

    entity_only_literals = entity_literals - block_call_literals
    block_candidates = set(block_sources) - entity_only_literals
    exact_mapped = sorted(state for state in block_candidates if normalize_block_state(state) in pnx_normalized_keys)
    order_only_matches = sorted(
        state for state in block_candidates
        if state not in pnx_state_keys and normalize_block_state(state) in pnx_normalized_keys
    )
    pnx_states_by_block = defaultdict(list)
    for pnx_state in pnx_state_keys:
        pnx_states_by_block[pnx_state.split("[", 1)[0]].append(pnx_state)
    subset_compatible = []
    unmatched_property_variants = []
    for state in block_candidates:
        base, properties = parse_block_state(state)
        candidates = pnx_states_by_block.get(base, [])
        compatible = False
        if properties is not None:
            for candidate in candidates:
                _, candidate_properties = parse_block_state(candidate)
                if candidate_properties is not None and all(
                    candidate_properties.get(name) == value
                    for name, value in properties.items()
                ):
                    compatible = True
                    break
        if compatible:
            subset_compatible.append(state)
        elif "[" in state:
            unmatched_property_variants.append(state)
    missing_block_id = sorted(
        state for state in block_candidates
        if state.split("[", 1)[0] not in pnx_block_ids
    )
    status_counts = Counter(record["status"] for record in script_records)
    summary = {
        "config_pack": "PolyhedralDev/TerraOverworldConfig",
        "config_pack_head": actual_head,
        "pnx_head": pnx_head,
        "terra_script_count": len(scripts),
        "structure_yaml_definition_count": len(structure_definitions),
        "feature_yaml_count": len(features),
        "feature_structure_reference_count": sum(len(v) for v in root_refs.values()),
        "unique_feature_root_ids": len(root_refs),
        "matched_script_roots": script_matches,
        "matched_yaml_structure_roots": config_matches,
        "unmatched_feature_root_ids": len(missing),
        "block_state_literal_count": len(block_candidates),
        "block_state_exact_matches": len(exact_mapped),
        "block_state_property_order_only_matches": len(order_only_matches),
        "entity_data_identifier_count": len(entity_only_literals),
        "dynamic_block_state_expression_count": len(dynamic_block_sources),
        "block_state_property_subset_matches": len(subset_compatible),
        "block_state_unmatched_property_variants": len(unmatched_property_variants),
        "block_identifier_mismatches": len(missing_block_id),
        "feature_BLOCK_palette_target_count": len(block_state_targets),
        "unresolved_feature_yaml_count": len(unresolved_features),
        "duplicate_script_id_count": len(duplicate_ids),
        "dynamic_nested_target_script_count": sum(1 for r in script_records if r["dynamic_nested_target"]),
        "status_counts": dict(sorted(status_counts.items())),
    }
    used_block_ids = sorted({state.split("[", 1)[0] for state in block_candidates})
    pnx_states_by_block = {
        block_id: sorted(
            state for state in pnx_state_keys if state.split("[", 1)[0] == block_id
        )
        for block_id in used_block_ids
    }
    block_mapping_audit = {
        "exact_or_normalized_matches": exact_mapped,
        "property_order_only_matches": order_only_matches,
        "entity_data_identifiers_excluded": sorted(entity_only_literals),
        "dynamic_block_state_expressions": {
            state: sorted(paths) for state, paths in dynamic_block_sources.items()
        },
        "property_subset_compatible_states": sorted(subset_compatible),
        "unmatched_property_variants": {
            state: sorted(block_sources[state]) for state in unmatched_property_variants
        },
        "missing_block_identifiers": {
            state: sorted(block_sources[state]) for state in missing_block_id
        },
        "pnx_known_states_by_block": pnx_states_by_block,
        "pnx_mapping_registry": "src/main/resources/mappings/blocks.json",
    }
    output = {
        "format": "terra-configpack-pnx-block-inventory",
        "version": 3,
        "summary": summary,
        "scripts": script_records,
        "structure_definitions": structure_definitions,
        "features": feature_records,
        "unmatched_feature_root_ids": missing,
        "block_state_targets": sorted(block_state_targets),
        "block_mapping_audit": block_mapping_audit,
        "unresolved_feature_yaml": unresolved_features,
        "duplicate_script_ids": duplicate_ids,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(summary, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
