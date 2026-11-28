#!/usr/bin/env bash
set -euo pipefail

# Apply the active Stage 1–26 patch chain to three clean, exact upstream checkouts.
# This script changes source checkouts only; it never touches a running server/world.

usage() {
  echo "Usage: $0 --pnx DIR --terra DIR --adapter DIR [--pnx-ref SHA]" >&2
  exit 2
}
PNX=""; TERRA=""; ADAPTER=""; PNX_EXPECTED_REF="${PNX_EXPECTED_REF:-c57499365737654eca32df008751ef6bca5af142}"
while (($#)); do
  case "$1" in
    --pnx) PNX="${2:-}"; shift 2 ;;
    --terra) TERRA="${2:-}"; shift 2 ;;
    --adapter) ADAPTER="${2:-}"; shift 2 ;;
    --pnx-ref) PNX_EXPECTED_REF="${2:-}"; shift 2 ;;
    *) usage ;;
  esac
done
[[ -n "$PNX" && -n "$TERRA" && -n "$ADAPTER" ]] || usage
ROOT="$(cd "$(dirname "$0")" && pwd)"
PATCH="$ROOT/patches"

check_repo() {
  local dir="$1" expected="$2" name="$3"
  [[ -d "$dir/.git" ]] || { echo "$name is not a Git checkout: $dir" >&2; exit 1; }
  local actual
  actual="$(git -C "$dir" rev-parse HEAD)"
  [[ "$actual" == "$expected" ]] || {
    echo "$name revision mismatch: expected $expected, found $actual" >&2
    exit 1
  }
  [[ -z "$(git -C "$dir" status --porcelain)" ]] || {
    echo "$name working tree is not clean: $dir" >&2
    exit 1
  }
}

check_repo "$PNX" "$PNX_EXPECTED_REF" "PowerNukkitX"
check_repo "$TERRA" "05ddc8b8a66e17412670647cce78ef837b091256" "Terra"
check_repo "$ADAPTER" "74318b4a1eae95322550f12d5eb8a76d92a93731" "PNX-Terra"

apply_patch() {
  local dir="$1" file="$2"
  echo "Applying $(basename "$file")"
  git -C "$dir" apply --check "$file"
  git -C "$dir" apply "$file"
}

# Stage 1: Terra placement-scope API.
apply_patch "$TERRA" "$PATCH/01-stage1-terra-placement-scope.patch"

# Stage 2's ThreadLocal adapter transaction prototype is retained in patches/history
# but intentionally not applied: Stage 10 supersedes it with the durable
# PlacementTransactionCoordinator and the integrated PNXServerWorld implementation.

# Stages 3–6 are preserved as historical prototypes. Their final responsibilities are
# superseded by the consolidated Stage 7 PNX async pipeline and Stage 8 adapter core.

# Stages 7–11: async PNX pipeline, journal adapter and durable storage primitive.
apply_patch "$PNX" "$PATCH/07-stage7-core.patch"
apply_patch "$ADAPTER" "$PATCH/08-stage8-adapter.patch"
apply_patch "$ADAPTER" "$PATCH/09-stage9-adapter.patch"
apply_patch "$ADAPTER" "$PATCH/10-stage10-adapter.patch"
apply_patch "$PNX" "$PATCH/11-stage11-pnx-durable-storage.patch"
apply_patch "$ADAPTER" "$PATCH/11-stage11-adapter.patch"

# Stages 12–15: bounds/footprints are incorporated into the Stage 15 Terra + adapter patches.
apply_patch "$TERRA" "$PATCH/15-stage15-terra-core.patch"
apply_patch "$ADAPTER" "$PATCH/15-stage15-adapter.patch"

# Stages 16–20: journal runtime, dependency scheduler and async finalization.
apply_patch "$ADAPTER" "$PATCH/16-17-stage16-17-adapter.patch"
apply_patch "$ADAPTER" "$PATCH/16-17-stage16-17-terra-generator.patch"
apply_patch "$PNX" "$PATCH/18-stage18-pnx-core.patch"
apply_patch "$PNX" "$PATCH/22-stage22-pnx-spawner-delay.patch"
apply_patch "$ADAPTER" "$PATCH/18-20-stage18-20-adapter.patch"

# Stages 21–25 plus reviewed hardening fixes required for one coherent replayable chain.
apply_patch "$ADAPTER" "$PATCH/21-25-stage21-25-adapter.patch"
apply_patch "$ADAPTER" "$PATCH/supplemental-stages15-25-runtime-hardening.patch"
apply_patch "$ADAPTER" "$PATCH/22-stage22-be-nbt-codec-and-level-executor.patch"
apply_patch "$ADAPTER" "$PATCH/22-stage22-be-intent-applier-integration.patch"
apply_patch "$ADAPTER" "$PATCH/22-stage22-be-finalizer-wiring.patch"
apply_patch "$ADAPTER" "$PATCH/22-stage22-terra-block-entity-capture.patch"

# Stage 26 deterministic per-anchor ordinals are already included in the reviewed
# supplemental hardening patch above (scheduler ticket + PlacementKey + recovery tests).
# The standalone Stage 26 patch is retained for history and must not be applied twice.

for dir in "$PNX" "$TERRA" "$ADAPTER"; do
  git -C "$dir" diff --check
 done

echo "Stage 1–26 patch application completed. This is not a build or runtime certification."
echo "Next: compile Terra, publish it to Maven Local; compile PNX; then compile PNX-Terra."
echo "Do not deploy until the full modified-source build and runtime/recovery tests pass."
