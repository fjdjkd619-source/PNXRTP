package org.powernukkitx.terra.crosschunk.journal;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PlacementJournalCompactionTest {
    public static void main(String[] args) throws Exception {
        compactsWalAndReplaysNewerEvents();
        finishesCompactionInterruptedAfterMarkerPublication();
        replaysEventsAppendedAfterStandaloneCheckpoint();
        rejectsCorruptCompactionMarkerWithoutTruncatingWal();
        System.out.println("PlacementJournalCompactionTest: PASS");
    }

    private static void compactsWalAndReplaysNewerEvents() throws Exception {
        Path directory = Files.createTempDirectory("journal-compaction");
        Path wal = directory.resolve("world.wal");
        Path checkpoint = directory.resolve("world.wal.checkpoint");
        ChunkPos first = new ChunkPos(4, -7);
        ChunkPos second = new ChunkPos(-9, 12);
        try (PlacementJournal journal = new PlacementJournal(wal)) {
            journal.appendAnchorStarted("world", first);
            journal.appendAnchorCompleted("world", first);
            journal.compact();
            require(Files.exists(checkpoint), "checkpoint was not published");
            require(Files.size(wal) == 0L, "WAL was not truncated after checkpoint publication");
            require(journal.isAnchorCompleted("world", first), "compaction lost completed anchor state");
            journal.appendAnchorStarted("world", second);
        }
        try (PlacementJournal restored = new PlacementJournal(wal)) {
            require(restored.isAnchorCompleted("world", first), "checkpoint state was not recovered");
            require(restored.isAnchorStarted("world", second), "post-checkpoint WAL event was not replayed");
            require(restored.lastSequence() == 3L, "recovered sequence watermark is incorrect");
        }
    }

    private static void finishesCompactionInterruptedAfterMarkerPublication() throws Exception {
        Path directory = Files.createTempDirectory("journal-compaction-crash");
        Path wal = directory.resolve("world.wal");
        Path checkpoint = directory.resolve("world.wal.checkpoint");
        Path marker = directory.resolve("world.wal.compact");
        ChunkPos anchor = new ChunkPos(2, 5);
        try (PlacementJournal journal = new PlacementJournal(wal)) {
            journal.appendAnchorStarted("world", anchor);
            journal.appendAnchorCompleted("world", anchor);
            JournalCheckpoint.Snapshot snapshot = journal.checkpointSnapshot();
            JournalCheckpoint.writeAtomically(checkpoint, snapshot);
            JournalCompactionMarker.writeAtomically(marker, snapshot.lastSequence());
            require(Files.size(wal) > 0L, "crash fixture must leave the original WAL intact");
        }
        try (PlacementJournal restored = new PlacementJournal(wal)) {
            require(restored.isAnchorCompleted("world", anchor), "interrupted compaction lost checkpoint state");
            require(Files.size(wal) == 0L, "recovery did not finish the marked WAL truncate");
            require(!Files.exists(marker), "recovery did not remove the compaction marker");
        }
    }

    private static void replaysEventsAppendedAfterStandaloneCheckpoint() throws Exception {
        Path directory = Files.createTempDirectory("journal-checkpoint-tail");
        Path wal = directory.resolve("world.wal");
        Path checkpoint = directory.resolve("world.wal.checkpoint");
        ChunkPos first = new ChunkPos(1, 1);
        ChunkPos second = new ChunkPos(2, 2);
        try (PlacementJournal journal = new PlacementJournal(wal)) {
            journal.appendAnchorStarted("world", first);
            journal.writeCheckpoint(checkpoint);
            journal.appendAnchorStarted("world", second);
        }
        try (PlacementJournal restored = new PlacementJournal(wal)) {
            require(restored.isAnchorStarted("world", first), "checkpoint-covered event was lost");
            require(restored.isAnchorStarted("world", second), "newer WAL event was not replayed after checkpoint");
            require(restored.lastSequence() == 2L, "checkpoint plus WAL sequence was not preserved");
        }
    }

    private static void rejectsCorruptCompactionMarkerWithoutTruncatingWal() throws Exception {
        Path directory = Files.createTempDirectory("journal-compaction-corrupt-marker");
        Path wal = directory.resolve("world.wal");
        Path checkpoint = directory.resolve("world.wal.checkpoint");
        Path marker = directory.resolve("world.wal.compact");
        try (PlacementJournal journal = new PlacementJournal(wal)) {
            journal.appendAnchorStarted("world", new ChunkPos(0, 0));
            JournalCheckpoint.Snapshot snapshot = journal.checkpointSnapshot();
            JournalCheckpoint.writeAtomically(checkpoint, snapshot);
            JournalCompactionMarker.writeAtomically(marker, snapshot.lastSequence());
        }
        byte[] markerBytes = Files.readAllBytes(marker);
        markerBytes[markerBytes.length - 1] ^= 0x01;
        Files.write(marker, markerBytes);
        byte[] walBefore = Files.readAllBytes(wal);
        expectIOException(() -> new PlacementJournal(wal));
        require(java.util.Arrays.equals(walBefore, Files.readAllBytes(wal)),
                "failed marker validation modified the WAL");
    }

    private static void expectIOException(IoAction action) throws Exception {
        try {
            action.run();
            throw new AssertionError("expected IOException");
        } catch (java.io.IOException expected) {
            // Expected fail-closed behavior.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws Exception;
    }
}
