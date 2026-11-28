package org.powernukkitx.terra.crosschunk;

import org.powernukkitx.terra.crosschunk.finalize.ChunkFinalizationCoordinator;
import org.powernukkitx.terra.crosschunk.journal.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public final class Stage18FinalizationTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("stage18-finalize-");
        Path wal = dir.resolve("journal.wal");
        ChunkPos target = new ChunkPos(-2, 3);
        String world = "test-world";
        try (PlacementJournal journal = new PlacementJournal(wal)) {
            PlacementRecord first = record(world, new ChunkPos(-1, 3), "feature-a", -28, 8, 48, (byte) 11);
            journal.appendAnchorStarted(world, first.key().anchor());
            journal.appendPlacement(first);
            journal.appendAnchorCompleted(world, first.key().anchor());

            FakeChunk chunk = new FakeChunk(target);
            ChunkFinalizationCoordinator<FakeChunk> coordinator = new ChunkFinalizationCoordinator<>(world, journal, Runnable::run);
            CompletableFuture<Void> barrier = new CompletableFuture<>();
            AtomicInteger lookups = new AtomicInteger();
            CompletableFuture<ChunkFinalizationCoordinator.Prepared<FakeChunk>> preparedFuture = coordinator.prepare(
                    target, barrier, () -> { lookups.incrementAndGet(); return chunk; });
            check(!preparedFuture.isDone(), "target waits for dependency barrier");
            check(chunk.applyCount == 0, "no writes before barrier");
            barrier.complete(null);
            var prepared = preparedFuture.join();
            check(chunk.applyCount == 1, "projection applied after barrier");
            check(!journal.isChunkApplied(world, target, prepared.digest()), "no acknowledgement before save");
            coordinator.persist(prepared, () -> chunk);
            check(chunk.saveCount == 1, "target saved exactly once");
            check(journal.isChunkApplied(world, target, prepared.digest()), "acknowledgement follows save");

            var replay = coordinator.prepare(target, CompletableFuture.completedFuture(null), () -> chunk).join();
            check(replay.alreadyApplied(), "recovered matching digest skips redundant writes");
            int applies = chunk.applyCount;
            coordinator.persist(replay, () -> chunk);
            check(chunk.applyCount == applies && chunk.saveCount == 1, "matching digest is idempotent");

            PlacementRecord late = record(world, new ChunkPos(-1, 3), "feature-b", -27, 9, 49, (byte) 22);
            journal.appendPlacement(late);
            var changed = coordinator.prepare(target, CompletableFuture.completedFuture(null), () -> chunk).join();
            check(!changed.alreadyApplied(), "new placement invalidates prior digest");
            check(chunk.applyCount == applies + 1, "new projection is reapplied");
            coordinator.persist(changed, () -> chunk);
            check(journal.isChunkApplied(world, target, changed.digest()), "new digest acknowledged after save");

            FakeChunk failing = new FakeChunk(new ChunkPos(10, -10));
            failing.failSave = true;
            PlacementRecord third = record(world, new ChunkPos(10, -10), "feature-c", 160, 4, -160, (byte) 33);
            journal.appendAnchorStarted(world, third.key().anchor());
            journal.appendPlacement(third);
            journal.appendAnchorCompleted(world, third.key().anchor());
            var failedPrepared = coordinator.prepare(new ChunkPos(10, -10), CompletableFuture.completedFuture(null), () -> failing).join();
            boolean failed = false;
            try { coordinator.persist(failedPrepared, () -> failing); } catch (IOException expected) { failed = true; }
            check(failed, "save failure propagates");
            check(!journal.isChunkApplied(world, new ChunkPos(10, -10), failedPrepared.digest()), "save failure withholds acknowledgement");

            FakeChunk missing = null;
            AtomicInteger missingLookups = new AtomicInteger();
            boolean refused = false;
            try {
                coordinator.prepare(new ChunkPos(50, 50), CompletableFuture.completedFuture(null), () -> {
                    missingLookups.incrementAndGet(); return missing;
                }).join();
            } catch (RuntimeException expected) { refused = true; }
            check(refused && missingLookups.get() == 1, "missing chunk is rejected without any load API");

            var stale = coordinator.prepare(target, CompletableFuture.completedFuture(null), () -> chunk).join();
            journal.appendPlacement(record(world, new ChunkPos(-1, 3), "feature-d", -26, 9, 50, (byte) 44));
            boolean staleRejected = false;
            try { coordinator.persist(stale, () -> chunk); } catch (IOException expected) { staleRejected = true; }
            check(staleRejected, "late journal append closes the apply-save-ack race");

            var preparedBeforeUnload = coordinator.prepare(target, CompletableFuture.completedFuture(null), () -> chunk).join();
            FakeChunk replacementInstance = new FakeChunk(target);
            boolean unloadRaceRejected = false;
            try { coordinator.persist(preparedBeforeUnload, () -> replacementInstance); }
            catch (IOException expected) { unloadRaceRejected = true; }
            check(unloadRaceRejected, "unloaded/replaced chunk instance cannot receive stale acknowledgement");
        }
        System.out.println("Stage18FinalizationTest: PASS (" + assertions + " assertions)");
    }

    private static PlacementRecord record(String world, ChunkPos anchor, String feature, int x, int y, int z, byte state) {
        PlacementKey key = new PlacementKey(world, 987654321L, anchor, feature, "test:structure", x, y, z, 0, 1);
        return new PlacementRecord(key, List.of(new BlockWrite(x, y, z, 0, new byte[]{state})));
    }
    private static void check(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
    private static final class FakeChunk implements ChunkFinalizationCoordinator.ChunkHandle {
        private final ChunkPos position;
        private int applyCount;
        private int saveCount;
        private boolean failSave;
        private NavigableMap<PlacementProjection.Position, PlacementProjection.Winner> lastProjection;
        FakeChunk(ChunkPos position) { this.position = position; }
        @Override public ChunkPos position() { return position; }
        @Override public boolean isLoaded() { return true; }
        @Override public boolean isGenerated() { return true; }
        @Override public boolean isInitiated() { return true; }
        @Override public void applyProjection(NavigableMap<PlacementProjection.Position, PlacementProjection.Winner> projection) {
            applyCount++; lastProjection = projection;
        }
        @Override public void saveDurably() throws IOException {
            saveCount++; if (failSave) throw new IOException("simulated storage failure");
        }
    }
}
