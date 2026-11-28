#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO_ROOT="$(git -C "$ROOT" rev-parse --show-toplevel)"
BASELINE_COMMIT="cac756841677e4b44e9cfcc7be55b7443c8ac5a7"
PATCH="$ROOT/patches/37-42-wal-hardening.patch"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

SOURCE_PATH="PNX-Terra-stages1-36-unified-candidate/source/org/powernukkitx/terra/crosschunk/journal/PlacementJournal.java"
TARGET_SOURCE="src/main/java/org/powernukkitx/terra/crosschunk/journal/PlacementJournal.java"

mkdir -p "$TMP/$(dirname "$TARGET_SOURCE")"
git -C "$REPO_ROOT" show "$BASELINE_COMMIT:$SOURCE_PATH" > "$TMP/$TARGET_SOURCE"
git -C "$TMP" init -q

git -C "$TMP" apply --check "$PATCH"
git -C "$TMP" apply "$PATCH"
git -C "$TMP" diff --check

# Stages 43–44 extend PlacementJournal with checkpoint/compaction fields, so a
# whole-file byte comparison against the Stage 36 baseline is no longer valid.
# Verify the Stage 37–42 safety/index/health symbols in both the exact patch result
# and the later integrated source while retaining a clean exact-baseline apply check.
python3 - "$TMP/$TARGET_SOURCE" "$ROOT/source/org/powernukkitx/terra/crosschunk/journal/PlacementJournal.java" <<'PY'
from pathlib import Path
import sys

required = (
    "placementSequences",
    "startedAnchorSequences",
    "completedAnchorSequences",
    "appliedSequences",
    "recoveryDurationNanos",
    "recoveredTailBytes",
    "isPartialMagicPrefix",
    "preserveTornTail",
    "BoundedOutputStream",
    "sequenceOfPlacement",
    "sequenceOfAnchor",
    "sequenceOfAnchorStarted",
    "sequenceOfApplied",
    "JournalStats",
    "ensureOpen",
    "poisoned",
)
for label, path in (("patch result", Path(sys.argv[1])), ("integrated source", Path(sys.argv[2]))):
    source = path.read_text(encoding="utf-8")
    missing = [symbol for symbol in required if symbol not in source]
    if missing:
        raise SystemExit(f"{label} is missing Stage 37–42 symbols: {', '.join(missing)}")
print("Stage 37–42 WAL hardening symbols: PASS")
PY

# Regression tests are kept in the validation overlay and compiled by run-core-tests.sh;
# the upstream patch intentionally carries only production source.

echo "Stage 37–42 adapter patch: exact baseline apply, clean diff and integrated symbol parity PASS."
