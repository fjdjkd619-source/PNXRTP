#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
PNX=""
ADAPTER=""

args=("$@")
for ((i=0; i<${#args[@]}; i++)); do
  case "${args[$i]}" in
    --pnx=*) PNX="${args[$i]#--pnx=}" ;;
    --adapter=*) ADAPTER="${args[$i]#--adapter=}" ;;
    --pnx)
      (( i + 1 < ${#args[@]} )) || { echo "Missing value for --pnx" >&2; exit 2; }
      PNX="${args[$((i+1))]}"
      ;;
    --adapter)
      (( i + 1 < ${#args[@]} )) || { echo "Missing value for --adapter" >&2; exit 2; }
      ADAPTER="${args[$((i+1))]}"
      ;;
  esac
done

[[ -n "$PNX" ]] || { echo "Missing --pnx directory" >&2; exit 2; }
[[ -n "$ADAPTER" ]] || { echo "Missing --adapter directory" >&2; exit 2; }
[[ -d "$PNX/.git" ]] || { echo "PNX directory is not a git checkout: $PNX" >&2; exit 2; }
[[ -d "$ADAPTER/.git" ]] || { echo "Adapter directory is not a git checkout: $ADAPTER" >&2; exit 2; }

bash "$ROOT/apply-stages1-47.sh" "$@"

install -D "$ROOT/source/org/powernukkitx/terra/crosschunk/bounds/OrigenBoundsCatalog.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/crosschunk/bounds/OrigenBoundsCatalog.java"
install -D "$ROOT/source/org/powernukkitx/terra/crosschunk/validation/BoundsCatalogValidator.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/crosschunk/validation/BoundsCatalogValidator.java"

cmp "$ROOT/source/org/powernukkitx/terra/crosschunk/bounds/OrigenBoundsCatalog.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/crosschunk/bounds/OrigenBoundsCatalog.java"
cmp "$ROOT/source/org/powernukkitx/terra/crosschunk/validation/BoundsCatalogValidator.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/crosschunk/validation/BoundsCatalogValidator.java"
git -C "$PNX" diff --check
git -C "$ADAPTER" diff --check

echo "Stages 1–48 source overlays applied. Unknown structures are deny-all; full manifest certification remains open."
