#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO_ROOT="$(git -C "$ROOT" rev-parse --show-toplevel)"
BASELINE_COMMIT="cac756841677e4b44e9cfcc7be55b7443c8ac5a7"
PATCH="$ROOT/patches/37-42-wal-hardening.patch"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

SOURCE_PATH="PNX-Terra-stages1-36-unified-candidate/source/org/powernukkitx/terra/crosschunk/journal/PlacementJournal.java"
TEST16_PATH="PNX-Terra-stages1-36-unified-candidate/tests/org/powernukkitx/terra/crosschunk/Stage16JournalTest.java"
TARGET_SOURCE="src/main/java/org/powernukkitx/terra/crosschunk/journal/PlacementJournal.java"
TARGET_TEST16="src/test/java/org/powernukkitx/terra/crosschunk/Stage16JournalTest.java"

mkdir -p "$TMP/$(dirname "$TARGET_SOURCE")" "$TMP/$(dirname "$TARGET_TEST16")"
git -C "$REPO_ROOT" show "$BASELINE_COMMIT:$SOURCE_PATH" > "$TMP/$TARGET_SOURCE"
git -C "$REPO_ROOT" show "$BASELINE_COMMIT:$TEST16_PATH" > "$TMP/$TARGET_TEST16"
git -C "$TMP" init -q

git -C "$TMP" apply --check "$PATCH"
git -C "$TMP" apply "$PATCH"
git -C "$TMP" diff --check

cmp "$TMP/$TARGET_SOURCE" "$ROOT/source/org/powernukkitx/terra/crosschunk/journal/PlacementJournal.java"
cmp "$TMP/$TARGET_TEST16" "$ROOT/tests/org/powernukkitx/terra/crosschunk/Stage16JournalTest.java"
cmp "$TMP/src/test/java/org/powernukkitx/terra/crosschunk/Stage37WalRecoveryTest.java" \
    "$ROOT/tests/org/powernukkitx/terra/crosschunk/Stage37WalRecoveryTest.java"
cmp "$TMP/src/test/java/org/powernukkitx/terra/crosschunk/journal/PlacementJournalEncodingLimitTest.java" \
    "$ROOT/tests/org/powernukkitx/terra/crosschunk/journal/PlacementJournalEncodingLimitTest.java"

echo "Stage 37–42 adapter patch: exact baseline apply and overlay byte comparison PASS."
