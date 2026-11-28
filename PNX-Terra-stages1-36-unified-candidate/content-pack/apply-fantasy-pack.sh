#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
PACK="$ROOT/fantasy"
TERRA_CONFIG=""
while (($#)); do
  case "$1" in
    --terra-config) TERRA_CONFIG="${2:-}"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done
[[ -n "$TERRA_CONFIG" && -d "$TERRA_CONFIG" ]] || { echo "Usage: $0 --terra-config DIR" >&2; exit 2; }
[[ -f "$TERRA_CONFIG/pack.yml" && -f "$TERRA_CONFIG/biomes/abstract/base.yml" ]] || {
  echo "Target is not the pinned TerraOverworldConfig checkout" >&2; exit 1;
}
mkdir -p "$TERRA_CONFIG/features/fantasy" "$TERRA_CONFIG/structures/fantasy"
cp "$PACK/features/fantasy/"*.yml "$TERRA_CONFIG/features/fantasy/"
cp "$PACK/structures/fantasy/"*.tesf "$TERRA_CONFIG/structures/fantasy/"
python3 - "$TERRA_CONFIG/biomes/abstract/base.yml" <<'PY'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

def add_feature_list(stage: str, feature_ids: list[str]) -> None:
    global text
    match = re.search(rf"(?m)^  {re.escape(stage)}:\n((?:    - [A-Z0-9_]+(?:\n|$))+)", text)
    if not match:
        if stage != "landforms":
            raise SystemExit(f"Expected base biome feature list missing: {stage}")
        text = text.rstrip() + f"\n  {stage}:\n" + "".join(f"    - {feature}\n" for feature in feature_ids)
        return
    existing = match.group(1)
    existing_ids = set(re.findall(r"(?m)^    - ([A-Z0-9_]+)$", existing))
    missing = [feature for feature in feature_ids if feature not in existing_ids]
    if missing:
        separator = "" if text[:match.end()].endswith("\n") else "\n"
        text = text[:match.end()] + separator + "".join(f"    - {feature}\n" for feature in missing) + text[match.end():]

add_feature_list("structures", [
    "FANTASY_RUINED_WATCHTOWERS",
    "FANTASY_CASTLE_KEEPS",
    "FANTASY_HOMESTEADS",
    "FANTASY_UNDERGROUND_CRYPTS",
    "FANTASY_ANCIENT_SANCTUARIES",
    "FANTASY_WATER_BRIDGES",
    "FANTASY_UNDERGROUND_HALLS",
])
add_feature_list("landforms", [
    "FANTASY_FLOATING_ISLANDS",
    "FANTASY_CRYSTAL_CLUSTERS",
])
path.write_text(text, encoding="utf-8")
PY
