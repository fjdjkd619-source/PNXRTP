#!/usr/bin/env python3
"""Build a source-pinned Terra ConfigPack inventory; does not claim bounds are certified."""
from __future__ import annotations

import argparse
import json
import re
import subprocess
from collections import Counter, defaultdict
from pathlib import Path

STRUCTURE_SCALAR = re.compile(r"(?m)^[ \t]*structures:[ \t]*([A-Za-z0-9_.:/-]+)[ \t]*(?:#.*)?$")
NESTED_LITERAL = re.compile(r"""\b(?:structure|placeStructure|place_structure)\s*\(\s*(['"])(.*?)\1""")
NESTED_CALL = re.compile(r"\b(?:structure|placeStructure|place_structure)\s*\(")
BLOCK_WRITE = re.compile(r"\bblock\s*\(")
BLOCK_READ = re.compile(r"\b(?:getBlock|blockState|check)\s*\(")
LOOP = re.compile(r"\bfor\s*\(")


def git_head(root: Path) -> str:
    try:
        return subprocess.check_output(
            ["git", "-C", str(root), "rev-parse", "HEAD"], text=True
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    if not root.is_dir():
        raise SystemExit(f"ConfigPack root does not exist: {root}")

    scripts = sorted(root.rglob("*.tesf"))
    features = sorted(
        p for p in root.rglob("*.yml")
        if "features" in p.relative_to(root).parts
    )
    scripts_by_id: dict[str, list[str]] = defaultdict(list)
    script_records = []
    for path in scripts:
        rel = path.relative_to(root).as_posix()
        script_id = path.stem
        scripts_by_id[script_id].append(rel)
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
            "dynamic_nested_target": calls > len(nested_ids),
            "loop_count": len(LOOP.findall(source)),
            "block_write_call_count": len(BLOCK_WRITE.findall(source)),
            "block_read_or_check_call_count": len(BLOCK_READ.findall(source)),
            "uses_random_or_math": bool(re.search(r"\b(random|randomInt|sampler|pow|sqrt)\b", source)),
            "notes": "Inventory only. No static bounds are certified by this script.",
        })

    root_refs = defaultdict(list)
    unresolved_features = []
    feature_records = []
    for path in features:
        rel = path.relative_to(root).as_posix()
        source = path.read_text(encoding="utf-8")
        ids = sorted(set(STRUCTURE_SCALAR.findall(source)))
        if not ids and re.search(r"(?m)^[ \t]*structures:", source):
            unresolved_features.append(rel)
        feature_records.append({"path": rel, "structure_ids": ids})
        for script_id in ids:
            root_refs[script_id].append(rel)

    for record in script_records:
        record["root_feature_references"] = sorted(root_refs.get(record["id"], []))
        record["has_root_feature_reference"] = bool(record["root_feature_references"])

    missing = sorted(script_id for script_id in root_refs if script_id not in scripts_by_id)
    duplicate_ids = {
        script_id: paths for script_id, paths in sorted(scripts_by_id.items()) if len(paths) > 1
    }
    status_counts = Counter(record["status"] for record in script_records)
    summary = {
        "config_pack": "PolyhedralDev/TerraOverworldConfig",
        "git_head": git_head(root),
        "inventory_policy": "Inventory only; every script remains UNKNOWN until separately audited.",
        "terra_script_count": len(scripts),
        "feature_yaml_count": len(features),
        "feature_structure_reference_count": sum(len(v) for v in root_refs.values()),
        "unique_feature_root_ids": len(root_refs),
        "matched_feature_root_ids": sum(1 for key in root_refs if key in scripts_by_id),
        "unmatched_feature_root_ids": len(missing),
        "unresolved_feature_yaml_count": len(unresolved_features),
        "duplicate_script_id_count": len(duplicate_ids),
        "dynamic_nested_target_script_count": sum(1 for r in script_records if r["dynamic_nested_target"]),
        "status_counts": dict(sorted(status_counts.items())),
    }
    output = {
        "format": "terra-configpack-source-inventory",
        "version": 1,
        "summary": summary,
        "scripts": script_records,
        "features": feature_records,
        "unmatched_feature_root_ids": missing,
        "unresolved_feature_yaml": unresolved_features,
        "duplicate_script_ids": duplicate_ids,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(summary, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
