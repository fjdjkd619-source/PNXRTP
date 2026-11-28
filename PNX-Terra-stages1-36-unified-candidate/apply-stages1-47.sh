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

bash "$ROOT/apply-stages1-46.sh" "$@"

PNX_PATCH="$ROOT/patches/47-pnx-entity-generation-state-query.patch"
ADAPTER_PATCH="$ROOT/patches/47-transactional-terra-entity-capture.patch"

git -C "$PNX" apply --check "$PNX_PATCH"
git -C "$ADAPTER" apply --check "$ADAPTER_PATCH"
git -C "$PNX" apply "$PNX_PATCH"
git -C "$ADAPTER" apply "$ADAPTER_PATCH"
git -C "$PNX" diff --check
git -C "$ADAPTER" diff --check

echo "Stages 1–47 source patches applied. Entity replay, build and runtime certification remain deferred until Stage 50."
