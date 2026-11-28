#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PNX_ROOT="${PNX_ROOT:-$ROOT/../pnx-pinned}"
ENTITY="$PNX_ROOT/src/main/java/org/powernukkitx/entity/Entity.java"

[[ -f "$ENTITY" ]] || {
  echo "Pinned PNX Entity.java not found at $ENTITY; set PNX_ROOT to the exact patched PNX checkout." >&2
  exit 2
}

for symbol in \
  GENERATION_ENTITY_CREATION \
  createEntityForGeneration \
  generationActivationPending \
  generationRegisteredForSave \
  registerForGenerationSave \
  activateAfterGenerationCommit \
  closeForGenerationRollback; do
  grep -Fq "$symbol" "$ENTITY" || {
    echo "Missing Stage 35/36 PNX lifecycle symbol: $symbol" >&2
    exit 1
  }
done

# The staged creation path must validate the spawn event before any chunk/Level registration.
python3 - "$ENTITY" <<'PY'
from pathlib import Path
import sys

source = Path(sys.argv[1]).read_text()
start = source.index("protected final void init(IChunk chunk, CompoundTag nbt)")
end = source.index("public final boolean registerForGenerationSave()", start)
init = source[start:end]
staged = init.index("if (this.generationActivationPending)")
event = init.index("this.server.getPluginManager().callEvent(event)", staged)
chunk_registration = init.index("this.chunk.addEntity(this)", event)
level_registration = init.index("this.level.addEntity(this)", event)
assert staged < event < chunk_registration < level_registration
print("Stage 36 lifecycle ordering: PASS")
PY

echo "PNX Stage 35/36 exact-source API verification: PASS"
