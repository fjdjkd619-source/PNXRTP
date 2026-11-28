#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="${BUILD_DIR:-$ROOT/validation/runtime-contract-build}"
STUB="$BUILD/stubs"
rm -rf "$BUILD"
mkdir -p "$STUB/org/powernukkitx/level/format" "$STUB/org/powernukkitx/level"
cat > "$STUB/org/powernukkitx/level/format/ChunkState.java" <<'JAVA'
package org.powernukkitx.level.format;
public enum ChunkState { NEW, STARTED, GENERATED, POPULATED, FINISHED; public boolean canSend(){return this==FINISHED;} }
JAVA
cat > "$STUB/org/powernukkitx/level/format/IChunk.java" <<'JAVA'
package org.powernukkitx.level.format;
public interface IChunk { int getX(); int getZ(); ChunkState getChunkState(); void setChunkState(ChunkState state); void setChanged(); }
JAVA
cat > "$STUB/org/powernukkitx/level/Level.java" <<'JAVA'
package org.powernukkitx.level;
import org.powernukkitx.level.format.IChunk;
public class Level {
  public String getName(){return "test";} public long getSeed(){return 1L;}
  public IChunk getChunkIfLoaded(int x,int z){return null;}
  public boolean isChunkGenerating(int x,int z){return false;}
  public boolean generateChunkToStage(int x,int z,String stage,boolean recover){return true;}
}
JAVA
mapfile -t SOURCES < <(find "$ROOT/source" "$ROOT/tests" "$STUB" -name '*.java' \
  | grep -v '/TerraGenerator.java$' \
  | grep -v '/delegate/PNXServerWorld.java$' \
  | grep -v '/delegate/PNXBlockStateDelegate.java$' \
  | grep -v '/delegate/PNXChunkDelegate.java$' \
  | grep -v '/delegate/PNXBlockEntityDelegate.java$' \
  | grep -v '/finalize/PnxChunkHandle.java$' \
  | grep -v '/finalize/PnxCrossChunkFinalizer.java$' \
  | grep -v '/journal/pnx/' \
  | grep -v '/runtime/PnxLevelExecutor.java$' \
  | grep -v '/delegate/PNXStagedBlockEntityDelegate.java$' \
  | grep -v '/delegate/PNXStagedSignDelegate.java$' \
  | grep -v '/delegate/PNXStagedMobSpawnerDelegate.java$' \
  | grep -v '/delegate/PNXStagedContainerDelegate.java$' \
  | grep -v '/delegate/PNXStagedInventory.java$' )
javac --release 21 -Xlint:all -Werror -d "$BUILD/classes" "${SOURCES[@]}"
echo "PnxGenerationJournalRuntime Java 21 contract compile: PASS (Level.isChunkGenerating API)"
