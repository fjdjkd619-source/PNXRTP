#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="${BUILD_DIR:-$ROOT/validation/build}"
rm -rf "$BUILD"
mkdir -p "$BUILD"
mapfile -t SOURCES < <(find "$ROOT/source" "$ROOT/tests" -name '*.java' \
  | grep -v '/TerraGenerator.java$' \
  | grep -v '/delegate/PNXServerWorld.java$' \
  | grep -v '/delegate/PNXBlockStateDelegate.java$' \
  | grep -v '/delegate/PNXChunkDelegate.java$' \
  | grep -v '/delegate/PNXBlockEntityDelegate.java$' \
  | grep -v '/finalize/PnxChunkHandle.java$' \
  | grep -v '/finalize/PnxCrossChunkFinalizer.java$' \
  | grep -v '/journal/pnx/' \
  | grep -v '/runtime/PnxGenerationJournalRuntime.java$' \
  | grep -v '/runtime/PnxLevelExecutor.java$' \
  | grep -v '/delegate/PNXStagedBlockEntityDelegate.java$' \
  | grep -v '/delegate/PNXStagedSignDelegate.java$' \
  | grep -v '/delegate/PNXStagedMobSpawnerDelegate.java$' \
  | grep -v '/delegate/PNXStagedContainerDelegate.java
javac --release 21 -Xlint:all -Werror -d "$BUILD" "${SOURCES[@]}"
for test in $(find "$ROOT/tests" -name '*Test.java' | sort); do
  package="$(sed -n 's/^package \(.*\);/\1/p' "$test" | head -n 1)"
  class="$(basename "$test" .java)"
  java -cp "$BUILD" "$package.$class"
done
 \
  | grep -v '/delegate/PNXStagedEntityDelegate.java
javac --release 21 -Xlint:all -Werror -d "$BUILD" "${SOURCES[@]}"
for test in $(find "$ROOT/tests" -name '*Test.java' | sort); do
  package="$(sed -n 's/^package \(.*\);/\1/p' "$test" | head -n 1)"
  class="$(basename "$test" .java)"
  java -cp "$BUILD" "$package.$class"
done
 \
  | grep -v '/delegate/PNXProtoChunk.java
javac --release 21 -Xlint:all -Werror -d "$BUILD" "${SOURCES[@]}"
for test in $(find "$ROOT/tests" -name '*Test.java' | sort); do
  package="$(sed -n 's/^package \(.*\);/\1/p' "$test" | head -n 1)"
  class="$(basename "$test" .java)"
  java -cp "$BUILD" "$package.$class"
done
 \
  | grep -v '/delegate/PNXStagedInventory.java
javac --release 21 -Xlint:all -Werror -d "$BUILD" "${SOURCES[@]}"
for test in $(find "$ROOT/tests" -name '*Test.java' | sort); do
  package="$(sed -n 's/^package \(.*\);/\1/p' "$test" | head -n 1)"
  class="$(basename "$test" .java)"
  java -cp "$BUILD" "$package.$class"
done
)
javac --release 21 -Xlint:all -Werror -d "$BUILD" "${SOURCES[@]}"
for test in $(find "$ROOT/tests" -name '*Test.java' | sort); do
  package="$(sed -n 's/^package \(.*\);/\1/p' "$test" | head -n 1)"
  class="$(basename "$test" .java)"
  java -cp "$BUILD" "$package.$class"
done
