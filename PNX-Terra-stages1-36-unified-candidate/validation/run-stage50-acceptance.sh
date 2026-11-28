#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CANDIDATE=""
TERRA_CONFIG=""
PNX_ROOT=""
INVENTORY=""
BOUNDS_CATALOG=""
BOUNDS_SUMMARY=""
OUTPUT=""

while (($#)); do
  case "$1" in
    --candidate) CANDIDATE="${2:-}"; shift 2 ;;
    --terra-config) TERRA_CONFIG="${2:-}"; shift 2 ;;
    --pnx) PNX_ROOT="${2:-}"; shift 2 ;;
    --inventory) INVENTORY="${2:-}"; shift 2 ;;
    --bounds-catalog) BOUNDS_CATALOG="${2:-}"; shift 2 ;;
    --bounds-summary) BOUNDS_SUMMARY="${2:-}"; shift 2 ;;
    --output) OUTPUT="${2:-}"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

for value in "$CANDIDATE" "$TERRA_CONFIG" "$PNX_ROOT" "$INVENTORY" "$BOUNDS_CATALOG" "$BOUNDS_SUMMARY" "$OUTPUT"; do
  [[ -n "$value" ]] || { echo "Missing required Stage 50 argument" >&2; exit 2; }
done
[[ -d "$CANDIDATE" && -d "$TERRA_CONFIG" && -d "$PNX_ROOT" ]] || { echo "Stage 50 checkout directory missing" >&2; exit 2; }

echo "== Stage 50: content and bounds acceptance =="
python3 "$ROOT/validation/stage50_acceptance.py" \
  --candidate "$CANDIDATE" \
  --terra-config "$TERRA_CONFIG" \
  --inventory "$INVENTORY" \
  --bounds-catalog "$BOUNDS_CATALOG" \
  --bounds-summary "$BOUNDS_SUMMARY" \
  --output "$OUTPUT"

echo "== Stage 50: source and patch acceptance =="
bash -n "$ROOT/content-pack/apply-fantasy-pack.sh"
bash -n "$ROOT/validation/run-core-tests.sh"
bash -n "$ROOT/validation/run-delegate-contract-compile.sh"
bash -n "$ROOT/validation/run-entity-spawn-contract-compile.sh"
bash -n "$ROOT/validation/run-pnx-be-contract-compile.sh"
bash -n "$ROOT/validation/run-runtime-contract-compile.sh"
bash "$ROOT/validation/check-stage37-42-patch.sh"
PNX_ROOT="$PNX_ROOT" bash "$ROOT/validation/run-stage36-pnx-api-patch-test.sh"

echo "== Stage 50: Java 21 core regression suites =="
bash "$ROOT/validation/run-core-tests.sh"

echo "== Stage 50: adapter and PNX contract suites =="
bash "$ROOT/validation/run-delegate-contract-compile.sh"
bash "$ROOT/validation/run-entity-spawn-contract-compile.sh"
bash "$ROOT/validation/run-pnx-be-contract-compile.sh"
bash "$ROOT/validation/run-runtime-contract-compile.sh"

echo "STAGE50_ACCEPTANCE=PASS"
