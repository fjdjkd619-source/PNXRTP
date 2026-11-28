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

"$ROOT/apply-stages1-45.sh" "$@"

PNX_PATCH="$ROOT/patches/46-pnx-blockmanager-layer-state-access.patch"
ADAPTER_PATCH="$ROOT/patches/46-bedrock-two-layer-water-policy.patch"

git -C "$PNX" apply --check "$PNX_PATCH"
git -C "$ADAPTER" apply --check "$ADAPTER_PATCH"
git -C "$PNX" apply "$PNX_PATCH"
git -C "$ADAPTER" apply "$ADAPTER_PATCH"
git -C "$PNX" diff --check
git -C "$ADAPTER" diff --check

echo "Stages 1–46 source patches applied. Build, runtime and regression certification remain deferred until Stage 50."
