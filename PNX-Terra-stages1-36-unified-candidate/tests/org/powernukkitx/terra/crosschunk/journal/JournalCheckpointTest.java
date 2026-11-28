package org.powernukkitx.terra.crosschunk.journal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public final class JournalCheckpointTest {
    public static void main(String[] args) throws Exception {
        roundTripsAllEventKindsAndIntentPayloads();
        rejectsPayloadTamperingWithoutModifyingCheckpoint();
        rejectsTruncatedCheckpoint();
        atomicallyReplacesExistingCheckpoint();
        System.out.println("JournalCheckpointTest: PASS");
    }

    private static void roundTripsAllEventKindsAndIntentPayloads() throws Exception {
        Path directory = Files.createTempDirectory("journal-checkpoint-roundtrip");
        Path file = directory.resolve("world.pnxterra.checkpoint");
        PlacementKey key = new PlacementKey("world|seed=77|dimension=0", 77L,
                new ChunkPos(-2, 3), "terra:oak", "terra:ancient_oak",
                -31, 68, 49, 2, 9L);
        EntitySpawnIntent spawn = new EntitySpawnIntent(UUID.fromString("3c7b7e3a-60db-5f6e-8f3e-2b8d9f75b901"),
                "minecraft:villager", -30.5, 69.0, 50.25, 90.0f, 0.0f, new byte[]{4, 5, 6});
        PlacementRecord record = new PlacementRecord(key,
                List.of(new BlockWrite(-31, 68, 49, 0, new byte[]{1, 2}),
                        new BlockWrite(-31, 68, 49, 1, new byte[]{3})),
                List.of(new BlockEntityIntent(-31, 68, 49, "minecraft:chest", new byte[]{7, 8})),
                List.of(new PhysicsIntent(-31, 68, 49, true)),
                List.of(spawn));
        String digest = "a".repeat(64);
        JournalCheckpoint.Snapshot snapshot = new JournalCheckpoint.Snapshot(4L, List.of(
                new JournalEvent.PlacementCommitted(1L, record),
                new JournalEvent.AnchorStarted(2L, "world|seed=77|dimension=0", new ChunkPos(-2, 3)),
                new JournalEvent.AnchorCompleted(3L, "world|seed=77|dimension=0", new ChunkPos(-2, 3)),
                new JournalEvent.ChunkApplied(4L, "world|seed=77|dimension=0", new ChunkPos(-1, 3), digest)));

        JournalCheckpoint.writeAtomically(file, snapshot);
        JournalCheckpoint.Snapshot restored = JournalCheckpoint.read(file);
        require(snapshot.equals(restored), "checkpoint round-trip changed logical state");
        require(Files.list(directory).count() == 1L, "temporary checkpoint file was left behind");
    }

    private static void rejectsPayloadTamperingWithoutModifyingCheckpoint() throws Exception {
        Path directory = Files.createTempDirectory("journal-checkpoint-tamper");
        Path file = directory.resolve("checkpoint.bin");
        JournalCheckpoint.writeAtomically(file, new JournalCheckpoint.Snapshot(0L, List.of()));
        byte[] original = Files.readAllBytes(file);
        byte[] changed = original.clone();
        changed[changed.length - 1] ^= 0x01;
        Files.write(file, changed);
        byte[] corrupted = Files.readAllBytes(file);
        expectIOException(() -> JournalCheckpoint.read(file));
        require(java.util.Arrays.equals(corrupted, Files.readAllBytes(file)),
                "failed checkpoint read modified source file");
    }

    private static void rejectsTruncatedCheckpoint() throws Exception {
        Path directory = Files.createTempDirectory("journal-checkpoint-truncated");
        Path file = directory.resolve("checkpoint.bin");
        Files.write(file, new byte[]{0x50, 0x58, 0x43, 0x50, 0, 0, 0, 1});
        expectIOException(() -> JournalCheckpoint.read(file));
    }

    private static void atomicallyReplacesExistingCheckpoint() throws Exception {
        Path directory = Files.createTempDirectory("journal-checkpoint-replace");
        Path file = directory.resolve("checkpoint.bin");
        JournalCheckpoint.writeAtomically(file, new JournalCheckpoint.Snapshot(0L, List.of()));
        JournalCheckpoint.Snapshot next = new JournalCheckpoint.Snapshot(2L, List.of(
                new JournalEvent.AnchorStarted(2L, "world", new ChunkPos(1, 1))));
        JournalCheckpoint.writeAtomically(file, next);
        require(next.equals(JournalCheckpoint.read(file)), "atomic replacement did not publish new snapshot");
    }

    private static void expectIOException(IoAction action) throws Exception {
        try {
            action.run();
            throw new AssertionError("expected IOException");
        } catch (IOException expected) {
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
