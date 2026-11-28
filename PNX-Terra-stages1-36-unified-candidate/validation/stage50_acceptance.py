#!/usr/bin/env python3
"""Stage 50 static acceptance checks for the pinned Terra fantasy overlay."""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

import yaml

EXPECTED = {
    "FANTASY_FLOATING_ISLANDS": "fantasy_floating_island",
    "FANTASY_CRYSTAL_CLUSTERS": "fantasy_crystal_cluster",
    "FANTASY_RUINED_WATCHTOWERS": "fantasy_ruined_watchtower",
    "FANTASY_CASTLE_KEEPS": "fantasy_castle_keep",
    "FANTASY_HOMESTEADS": "fantasy_homestead",
    "FANTASY_UNDERGROUND_CRYPTS": "fantasy_underground_crypt",
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit("STAGE50 FAIL: " + message)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--candidate", required=True, type=Path)
    ap.add_argument("--terra-config", required=True, type=Path)
    ap.add_argument("--inventory", required=True, type=Path)
    ap.add_argument("--bounds-catalog", required=True, type=Path)
    ap.add_argument("--bounds-summary", required=True, type=Path)
    ap.add_argument("--output", required=True, type=Path)
    args = ap.parse_args()

    candidate = args.candidate.resolve()
    config = args.terra_config.resolve()
    pack = candidate / "content-pack" / "fantasy"
    manifest = json.loads((pack / "manifest.json").read_text(encoding="utf-8"))
    inventory = json.loads(args.inventory.read_text(encoding="utf-8"))
    summary = json.loads(args.bounds_summary.read_text(encoding="utf-8"))
    catalog = args.bounds_catalog.read_text(encoding="utf-8")

    require(manifest.get("status") == "partial-stage49-content-slice",
            "manifest status unexpectedly claims a complete content pack")
    require(set(manifest.get("features", [])) == set(EXPECTED),
            "manifest feature inventory differs from the six implemented features")
    require(set(manifest.get("structures", [])) == set(EXPECTED.values()),
            "manifest structure inventory differs from the six implemented structures")

    feature_records = {
        Path(item["path"]).stem: item for item in inventory.get("features", [])
    }
    script_records = {
        item.get("id"): item for item in inventory.get("scripts", [])
    }
    catalog_ids = set(re.findall(r'Map\.entry\("([^"]+)", profile\("', catalog))

    checks = []
    for feature_id, structure_id in EXPECTED.items():
        feature_path = config / "features" / "fantasy" / (feature_id.lower().removeprefix("fantasy_") + ".yml")
        # Feature filenames use descriptive suffixes; resolve by declared YAML id.
        candidates = sorted((config / "features" / "fantasy").glob("*.yml"))
        matched = []
        for path in candidates:
            data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
            if data.get("id") == feature_id:
                matched.append((path, data))
        require(len(matched) == 1, f"{feature_id}: expected exactly one YAML feature definition")
        path, data = matched[0]
        require(data.get("type") == "FEATURE", f"{feature_id}: type must be FEATURE")
        require(data.get("structures") == structure_id,
                f"{feature_id}: structure target must be {structure_id}")
        require(structure_id in script_records, f"{structure_id}: script missing from audit inventory")
        require(script_records[structure_id].get("has_root_feature_reference") is True,
                f"{structure_id}: no root feature reference recorded")
        require(structure_id in catalog_ids, f"{structure_id}: no statically certified bounds profile")
        script_path = config / "structures" / "fantasy" / (structure_id + ".tesf")
        require(script_path.is_file(), f"{structure_id}: TerraScript source missing")
        require(script_path.stat().st_size > 0, f"{structure_id}: TerraScript source is empty")
        checks.append({
            "feature": feature_id,
            "structure": structure_id,
            "feature_file": path.relative_to(config).as_posix(),
            "script_file": script_path.relative_to(config).as_posix(),
            "static_bounds_profile": True,
        })

    require(summary.get("static_profiles", 0) >= len(EXPECTED),
            "fewer than six static bounds profiles were generated")
    require(summary.get("yaml_parse_errors", 0) == 0,
            "bounds generator reported YAML definition errors")
    require(summary.get("profiles_generated", 0) ==
            summary.get("static_profiles", 0) + summary.get("unverified_deny_all_profiles", 0),
            "profile accounting does not balance")
    require("UNVERIFIED_DENY_ALL; static bounds proof required" in catalog,
            "catalog must retain the deny-all fallback")
    require('Map.entry("<unverified-deny-all>"' not in catalog,
            "deny-all fallback must never be admitted as a verified root")
    require(inventory.get("summary", {}).get("duplicate_script_id_count", 0) == 0,
            "duplicate TerraScript IDs found")
    require(not inventory.get("unmatched_feature_root_ids"),
            "feature roots reference missing scripts/definitions")
    require(not inventory.get("unresolved_feature_yaml"),
            "unresolved feature YAML found")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    result = {
        "stage": 50,
        "status": "PASS",
        "checks": checks,
        "bounds": {
            "feature_roots": summary.get("feature_roots"),
            "static_profiles": summary.get("static_profiles"),
            "unverified_deny_all_profiles": summary.get("unverified_deny_all_profiles"),
        },
        "scope": "static and contract acceptance; live world generation/restart certification remains separate",
    }
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
