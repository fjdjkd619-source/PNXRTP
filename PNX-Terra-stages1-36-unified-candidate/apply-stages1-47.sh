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

git -C "$PNX" apply --check "$PNX_PATCH"
git -C "$PNX" apply "$PNX_PATCH"

# The candidate source tree is the complete post-Stage-47 adapter overlay.
# Stages 1-46 modify several of these files, so a patch based on the pristine
# upstream adapter cannot safely be applied after those transformations.
# Copy the audited final source files into the pinned adapter checkout instead.
install -D "$ROOT/source/org/powernukkitx/terra/delegate/PNXServerWorld.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/delegate/PNXServerWorld.java"
install -D "$ROOT/source/org/powernukkitx/terra/delegate/PNXBlockEntityDelegate.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/delegate/PNXBlockEntityDelegate.java"
install -D "$ROOT/source/org/powernukkitx/terra/delegate/PNXStagedBlockEntityDelegate.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/delegate/PNXStagedBlockEntityDelegate.java"
install -D "$ROOT/source/org/powernukkitx/terra/TerraGenerator.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/TerraGenerator.java"
install -D "$ROOT/source/org/powernukkitx/terra/crosschunk/finalize/PnxChunkHandle.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/crosschunk/finalize/PnxChunkHandle.java"
install -D "$ROOT/source/org/powernukkitx/terra/crosschunk/journal/pnx/PnxBlockEntityIntentApplier.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/crosschunk/journal/pnx/PnxBlockEntityIntentApplier.java"
install -D "$ROOT/source/org/powernukkitx/terra/delegate/PNXStagedSignDelegate.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/delegate/PNXStagedSignDelegate.java"
install -D "$ROOT/source/org/powernukkitx/terra/crosschunk/journal/pnx/PnxPlacementCommitter.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/crosschunk/journal/pnx/PnxPlacementCommitter.java"
install -D "$ROOT/source/org/powernukkitx/terra/crosschunk/journal/pnx/PnxPlacementRecordFactory.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/crosschunk/journal/pnx/PnxPlacementRecordFactory.java"
install -D "$ROOT/source/org/powernukkitx/terra/crosschunk/bounds/PlacementRuntimeGuard.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/crosschunk/bounds/PlacementRuntimeGuard.java"
install -D "$ROOT/source/org/powernukkitx/terra/delegate/PNXStagedEntityDelegate.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/delegate/PNXStagedEntityDelegate.java"

cmp "$ROOT/source/org/powernukkitx/terra/delegate/PNXServerWorld.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/delegate/PNXServerWorld.java"
cmp "$ROOT/source/org/powernukkitx/terra/delegate/PNXBlockEntityDelegate.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/delegate/PNXBlockEntityDelegate.java"
cmp "$ROOT/source/org/powernukkitx/terra/delegate/PNXStagedBlockEntityDelegate.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/delegate/PNXStagedBlockEntityDelegate.java"
cmp "$ROOT/source/org/powernukkitx/terra/TerraGenerator.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/TerraGenerator.java"
cmp "$ROOT/source/org/powernukkitx/terra/crosschunk/finalize/PnxChunkHandle.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/crosschunk/finalize/PnxChunkHandle.java"
cmp "$ROOT/source/org/powernukkitx/terra/crosschunk/journal/pnx/PnxBlockEntityIntentApplier.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/crosschunk/journal/pnx/PnxBlockEntityIntentApplier.java"
cmp "$ROOT/source/org/powernukkitx/terra/delegate/PNXStagedSignDelegate.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/delegate/PNXStagedSignDelegate.java"
cmp "$ROOT/source/org/powernukkitx/terra/crosschunk/journal/pnx/PnxPlacementCommitter.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/crosschunk/journal/pnx/PnxPlacementCommitter.java"
cmp "$ROOT/source/org/powernukkitx/terra/crosschunk/journal/pnx/PnxPlacementRecordFactory.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/crosschunk/journal/pnx/PnxPlacementRecordFactory.java"

# Stage 48 bounded catalog and its runtime guard dependencies.
for file in \
  "crosschunk/bounds/Bounds3i.java" \
  "crosschunk/bounds/BoundsViolationException.java" \
  "crosschunk/bounds/HorizontalFootprintProjection.java" \
  "crosschunk/bounds/OrigenBoundsCatalog.java" \
  "crosschunk/bounds/PlacementGuardContext.java" \
  "crosschunk/bounds/PlacementRuntimeGuard.java" \
  "crosschunk/bounds/ScriptBoundsProfile.java" \
  "crosschunk/bounds/StructureFootprint.java" \
  "crosschunk/validation/BoundsCatalogValidator.java"; do
  install -D "$ROOT/source/org/powernukkitx/terra/$file" \
    "$ADAPTER/src/main/java/org/powernukkitx/terra/$file"
done

cmp "$ROOT/source/org/powernukkitx/terra/crosschunk/bounds/OrigenBoundsCatalog.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/crosschunk/bounds/OrigenBoundsCatalog.java"
cmp "$ROOT/source/org/powernukkitx/terra/crosschunk/validation/BoundsCatalogValidator.java" \
  "$ADAPTER/src/main/java/org/powernukkitx/terra/crosschunk/validation/BoundsCatalogValidator.java"

git -C "$PNX" diff --check
git -C "$ADAPTER" diff --check

echo "Stages 1–47 source patches applied. Entity replay, build and runtime certification remain deferred until Stage 50."
