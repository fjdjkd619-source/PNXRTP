package org.powernukkitx.terra.crosschunk;

import org.powernukkitx.terra.crosschunk.journal.*;
import org.powernukkitx.terra.crosschunk.scheduler.CrossChunkDependencyScheduler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Stage19RecoveryTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("stage19-recovery-");
        Path wal = dir.resolve("recovery.wal");
        String world = "recover-world";
        ChunkPos anchor = new ChunkPos(-3, 7);
        PlacementRecord record = new PlacementRecord(
                new PlacementKey(world, 42L, anchor, "recover-feature", "recover:structure", -48, 20, 112, 2, 0),
                List.of(new BlockWrite(-49, 21, 113, 1, new byte[]{1, 2, 3})));
        try (PlacementJournal journal = new PlacementJournal(wal)) {
            CrossChunkDependencyScheduler scheduler = new CrossChunkDependencyScheduler(world, journal);
            var session = scheduler.openAnchor(anchor);
            check(journal.isAnchorStarted(world, anchor), "anchor start is durable before generation work");
            var ticket = session.beginPlacement();
            ticket.commit(record);
            check(!journal.isAnchorCompleted(world, anchor), "in-flight population has no completion marker");
            // Simulate process death before finishPopulation: close only the journal, leave no completed marker.
        }
        try (PlacementJournal recovered = new PlacementJournal(wal)) {
            check(journalContains(recovered, record), "committed placement survives restart");
            check(recovered.incompleteAnchors(world).contains(anchor), "incomplete anchor is identified after restart");
            CrossChunkDependencyScheduler replayScheduler = new CrossChunkDependencyScheduler(world, recovered);
            var replay = replayScheduler.openAnchor(anchor);
            var replayTicket = replay.beginPlacement();
            check(replayTicket.ordinal() == 0L, "incomplete anchor replay restarts deterministic placement ordinals at zero");
            long sequence = replayTicket.commit(record);
            check(sequence > 0, "replayed identical root placement is idempotent");
            replay.finishPopulation();
            check(recovered.isAnchorCompleted(world, anchor), "replayed anchor writes durable completion marker");
            check(recovered.incompleteAnchors(world).isEmpty(), "completed anchor leaves recovery queue");

            String digest = PlacementDigest.compute(recovered.placementsFor(ChunkPos.fromBlock(-49, 113)), ChunkPos.fromBlock(-49, 113));
            recovered.appendChunkApplied(world, ChunkPos.fromBlock(-49, 113), digest);
        }
        try (PlacementJournal reopened = new PlacementJournal(wal)) {
            ChunkPos target = ChunkPos.fromBlock(-49, 113);
            String digest = PlacementDigest.compute(reopened.placementsFor(target), target);
            check(reopened.isChunkApplied(world, target, digest), "applied digest survives restart");
            check(reopened.isAnchorCompleted(world, anchor), "anchor completion survives restart");
        }
        // Torn trailing frame recovery: append an incomplete frame header and verify valid prefix remains.
        long validSize = Files.size(wal);
        Files.write(wal, new byte[]{0x50, 0x58, 0x4A, 0x31, 0, 0, 0, 64, 1, 2}, java.nio.file.StandardOpenOption.APPEND);
        try (PlacementJournal truncated = new PlacementJournal(wal)) {
            check(Files.size(wal) == validSize, "torn WAL tail is truncated to last valid frame");
            check(truncated.isAnchorCompleted(world, anchor), "valid prefix remains readable after tail recovery");
        }
        System.out.println("Stage19RecoveryTest: PASS (" + assertions + " assertions)");
    }

    private static boolean journalContains(PlacementJournal journal, PlacementRecord expected) {
        return journal.placementsFor(expected.targetChunks().iterator().next()).contains(expected);
    }
    private static void check(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
}
