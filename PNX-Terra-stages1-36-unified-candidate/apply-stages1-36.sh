#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
"$ROOT/apply-stages1-35.sh" "$@"
PNX=""; ADAPTER=""
while (($#)); do
  case "$1" in
    --pnx) PNX="${2:-}"; shift 2 ;;
    --adapter) ADAPTER="${2:-}"; shift 2 ;;
    *) shift ;;
  esac
done
[[ -n "$PNX" && -n "$ADAPTER" ]] || { echo "Missing --pnx or --adapter directory" >&2; exit 2; }
for spec in "$PNX:patches/36-pnx-deferred-entity-api.patch" "$ADAPTER:patches/36-stage36-deferred-entity-lifecycle.patch"; do
  IFS=: read -r dir relative <<< "$spec"
  patch="$ROOT/$relative"
  git -C "$dir" apply --check "$patch"
  git -C "$dir" apply "$patch"
  git -C "$dir" diff --check
done
echo "Stage 1–36 source patches applied. Full upstream build and runtime certification are still required."
