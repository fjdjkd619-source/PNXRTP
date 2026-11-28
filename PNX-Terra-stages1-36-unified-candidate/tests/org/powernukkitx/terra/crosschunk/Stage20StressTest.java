package org.powernukkitx.terra.crosschunk;

import org.powernukkitx.terra.crosschunk.journal.*;
import org.powernukkitx.terra.crosschunk.scheduler.CrossChunkDependencyScheduler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

public final class Stage20StressTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("stage20-stress-");
        String world = "stress-world";
        ChunkPos target = new ChunkPos(-17, -29);
        try (PlacementJournal journal = new PlacementJournal(dir.resolve("stress.wal"))) {
            int writers = 32;
            ExecutorService pool = Executors.newFixedThreadPool(8);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(writers);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            List<PlacementRecord> expected = Collections.synchronizedList(new ArrayList<>());
            for (int i = 0; i < writers; i++) {
                final int id = i;
                PlacementRecord record = new PlacementRecord(
                        new PlacementKey(world, 2026L, new ChunkPos(-17 + (id % 3), -29), "stress-feature-" + id,
                                "stress:structure", target.x() * 16 + (id % 16), id, target.z() * 16 + (id / 16), id % 4, id),
                        List.of(new BlockWrite(target.x() * 16 + (id % 16), id, target.z() * 16 + (id / 16), id % 2,
                                new byte[]{(byte) id, (byte) (id >>> 8)})));
                expected.add(record);
                pool.execute(() -> {
                    try { start.await(); journal.appendPlacement(record); }
                    catch (Throwable t) { failure.compareAndSet(null, t); }
                    finally { done.countDown(); }
                });
            }
            start.countDown();
            check(done.await(30, TimeUnit.SECONDS), "concurrent WAL writers complete");
            pool.shutdown();
            check(pool.awaitTermination(30, TimeUnit.SECONDS), "writer pool terminates");
            if (failure.get() != null) throw new AssertionError("Concurrent append failed", failure.get());
            check(journal.placementsFor(target).size() == writers, "all concurrent placements are durable");
            var projection = PlacementProjection.resolve(journal.placementsFor(target), target);
            var reversed = new ArrayList<>(journal.placementsFor(target));
            Collections.reverse(reversed);
            check(projection.equals(PlacementProjection.resolve(reversed, target)), "projection independent of input order");

            // Explicit same-cell conflict: lexicographically lowest stable placement ID wins.
            BlockWrite sharedA = new BlockWrite(target.x() * 16 + 1, 120, target.z() * 16 + 1, 0, new byte[]{101});
            BlockWrite sharedB = new BlockWrite(target.x() * 16 + 1, 120, target.z() * 16 + 1, 0, new byte[]{102});
            PlacementRecord conflictA = new PlacementRecord(new PlacementKey(world, 1, new ChunkPos(-18, -29), "conflict-a", "s:a", 0, 0, 0, 0, 1), List.of(sharedA));
            PlacementRecord conflictB = new PlacementRecord(new PlacementKey(world, 1, new ChunkPos(-16, -29), "conflict-b", "s:b", 0, 0, 0, 0, 1), List.of(sharedB));
            journal.appendPlacement(conflictB);
            journal.appendPlacement(conflictA);
            var winners = PlacementProjection.resolve(journal.placementsFor(target), target);
            var position = new PlacementProjection.Position(sharedA.x(), sharedA.y(), sharedA.z(), sharedA.layer());
            String expectedWinner = conflictA.key().stableId().compareTo(conflictB.key().stableId()) < 0
                    ? conflictA.key().stableId() : conflictB.key().stableId();
            check(winners.get(position).placementId().equals(expectedWinner), "same-cell winner is stable-key deterministic");

            // Per-target journal index must isolate a target without scanning unrelated placements.
            for (int i = 0; i < 128; i++) {
                ChunkPos isolated = new ChunkPos(i - 64, 100 + i);
                int bx = isolated.x() * 16 + 2, bz = isolated.z() * 16 + 2;
                PlacementRecord unrelated = new PlacementRecord(
                        new PlacementKey(world, 2026L, isolated, "indexed-" + i, "index:structure", bx, 70, bz, 0, i),
                        List.of(new BlockWrite(bx, 70, bz, 0, new byte[]{(byte) i})));
                journal.appendPlacement(unrelated);
            }
            check(journal.placementsFor(new ChunkPos(5, 169)).size() == 1, "target index returns only records for that chunk");
            check(journal.placementsFor(new ChunkPos(5, 170)).isEmpty(), "target index excludes adjacent unrelated chunk");

            // A negative-coordinate dependency barrier opens only when every required anchor is durable.
            Set<ChunkPos> anchors = CrossChunkDependencyScheduler.expectedAnchors(target, 16, 16);
            CrossChunkDependencyScheduler scheduler = new CrossChunkDependencyScheduler(world, journal);
            var barrier = scheduler.registerTarget(target, 16, 16, false);
            check(!barrier.isDone(), "target remains blocked before source anchors complete");
            for (ChunkPos anchor : anchors) {
                var session = scheduler.openAnchor(anchor);
                session.finishPopulation();
            }
            check(barrier.isDone() && !barrier.isCompletedExceptionally(), "target opens after every source anchor completes");
            check(scheduler.activateTarget(target).isDone(), "activation consumes the completed dependency barrier");
            check(scheduler.registerTarget(target, 16, 16, false).isDone(), "completed barrier can be reconstructed after cleanup");
            check(anchors.stream().anyMatch(a -> a.x() < 0 && a.z() < 0), "negative chunk coordinates included in dependency set");
        }
        Path retryWal = Files.createTempDirectory("stage20-retry-").resolve("retry.wal");
        try (PlacementJournal journal = new PlacementJournal(retryWal)) {
            AtomicInteger attempts = new AtomicInteger();
            CrossChunkDependencyScheduler retryScheduler = new CrossChunkDependencyScheduler("retry-world", journal,
                    anchor -> attempts.incrementAndGet() > 1);
            var retryBarrier = retryScheduler.registerTarget(new ChunkPos(0, 0), 0, 0, true);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (attempts.get() < 2 && System.nanoTime() < deadline) Thread.sleep(10);
            check(attempts.get() >= 2, "queue-rejected source anchor is retried asynchronously");
            var retryAnchor = retryScheduler.openAnchor(new ChunkPos(0, 0));
            retryAnchor.finishPopulation();
            check(retryBarrier.isDone(), "retried source anchor releases target barrier");
        }
        System.out.println("Stage20StressTest: PASS (" + assertions + " assertions)");
    }

    private static void check(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
}
