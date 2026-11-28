#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
"$ROOT/apply-stages1-34.sh" "$@"
PNX=""; ADAPTER=""; PNX_REF="${PNX_EXPECTED_REF:-c57499365737654eca32df008751ef6bca5af142}"
while (($#)); do
  case "$1" in
    --pnx) PNX="${2:-}"; shift 2 ;;
    --adapter) ADAPTER="${2:-}"; shift 2 ;;
    --pnx-ref) PNX_REF="${2:-}"; shift 2 ;;
    *) shift ;;
  esac
done
[[ -n "$PNX" && -n "$ADAPTER" ]] || { echo "Missing --pnx or --adapter directory" >&2; exit 2; }
for spec in "$PNX:patches/35-pnx-entity-rollback-api.patch" "$ADAPTER:patches/35-stage35-entity-spawn-adapter.patch"; do
  IFS=: read -r dir relative <<< "$spec"
  patch="$ROOT/$relative"
  git -C "$dir" apply --check "$patch"
  git -C "$dir" apply "$patch"
  git -C "$dir" diff --check
done
echo "Stage 1–35 source patches applied. Full upstream build and runtime certification are still required."
