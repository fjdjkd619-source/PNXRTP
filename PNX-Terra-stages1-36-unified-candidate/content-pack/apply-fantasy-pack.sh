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
import sys
path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
feature = "    - FANTASY_RUINED_WATCHTOWERS"
if feature not in text:
    old = "  structures:\n    - MOB_ROOMS"
    if old not in text:
        raise SystemExit("Unexpected base biome structures list; refusing to patch")
    text = text.replace(old, old + "\n" + feature, 1)
landforms = """  landforms:
    - FANTASY_FLOATING_ISLANDS
    - FANTASY_CRYSTAL_CLUSTERS
"""
if "  landforms:\n" not in text:
    text = text.rstrip() + "\n" + landforms
else:
    for item in ("    - FANTASY_FLOATING_ISLANDS", "    - FANTASY_CRYSTAL_CLUSTERS"):
        if item not in text:
            text = text.rstrip() + "\n" + item + "\n"
path.write_text(text, encoding="utf-8")
PY
