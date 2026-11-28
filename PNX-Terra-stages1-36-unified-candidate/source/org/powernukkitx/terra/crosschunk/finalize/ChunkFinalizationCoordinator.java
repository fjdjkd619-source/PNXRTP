package org.powernukkitx.terra.crosschunk.finalize;

import org.powernukkitx.terra.crosschunk.journal.ChunkPos;
import org.powernukkitx.terra.crosschunk.journal.PlacementDigest;
import org.powernukkitx.terra.crosschunk.journal.PlacementJournal;
import org.powernukkitx.terra.crosschunk.journal.PlacementProjection;
import org.powernukkitx.terra.crosschunk.journal.PlacementRecord;

import java.io.IOException;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Two-phase target finalization. prepare() applies a deterministic projection after the dependency
 * barrier; persist() saves the chunk before writing the durable applied-digest acknowledgement.
 */
public final class ChunkFinalizationCoordinator<C extends ChunkFinalizationCoordinator.ChunkHandle> {
    private final String worldKey;
    private final PlacementJournal journal;
    private final Executor executor;
    private final Executor intentExecutor;

    public ChunkFinalizationCoordinator(String worldKey, PlacementJournal journal, Executor executor) {
        this(worldKey, journal, executor, executor);
    }

    /** Separate the block projection worker from PNX-owned intent application. */
    public ChunkFinalizationCoordinator(String worldKey, PlacementJournal journal, Executor executor,
                                        Executor intentExecutor) {
        if (worldKey == null || worldKey.isBlank()) throw new IllegalArgumentException("worldKey is blank");
        this.worldKey = worldKey;
        this.journal = Objects.requireNonNull(journal, "journal");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.intentExecutor = Objects.requireNonNull(intentExecutor, "intentExecutor");
    }

    public CompletableFuture<Prepared<C>> prepare(ChunkPos target, CompletableFuture<Void> dependencyBarrier,
                                                   Supplier<C> loadedOnlyLookup) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(dependencyBarrier, "dependencyBarrier");
        Objects.requireNonNull(loadedOnlyLookup, "loadedOnlyLookup");
        return dependencyBarrier.thenApplyAsync(ignored -> {
            C chunk = loadedOnlyLookup.get();
            validate(target, chunk);
            List<PlacementRecord> records = journal.placementsFor(target);
            String digest = PlacementDigest.compute(records, target);
            boolean alreadyApplied = journal.isChunkApplied(worldKey, target, digest);
            if (!alreadyApplied) {
                NavigableMap<PlacementProjection.Position, PlacementProjection.Winner> projection =
                        PlacementProjection.resolve(records, target);
                try {
                    chunk.applyProjection(projection);
                } catch (Exception failure) {
                    throw new FinalizationException("Could not apply block projection to " + target, failure);
                }
            }
            return new Pending<>(worldKey, target, digest, chunk, alreadyApplied, records);
        }, executor).thenApplyAsync(pending -> {
            if (!pending.alreadyApplied) {
                try {
                    NavigableMap<PlacementProjection.Position, PlacementProjection.EntityWinner> blockEntities =
                            PlacementProjection.resolveBlockEntities(pending.records, target);
                    pending.chunk.applyBlockEntityIntents(blockEntities);
                    pending.chunk.applyPhysicsIntents(PlacementProjection.resolvePhysics(pending.records, target));
                    List<org.powernukkitx.terra.crosschunk.journal.EntitySpawnIntent> entitySpawns =
                            PlacementProjection.resolveEntitySpawns(pending.records, target);
                    pending.chunk.applyEntitySpawnIntents(entitySpawns);
                    if (!entitySpawns.isEmpty()) pending.chunk.prepareEntitySpawnIntentsForSave();
                    if (!blockEntities.isEmpty() || !entitySpawns.isEmpty()) {
                        // Some PNX BlockEntity constructors schedule their own tick immediately.
                        // Persist in the same level-scheduler task before that heartbeat can run them.
                        pending.chunk.saveDurably();
                        pending.chunk.confirmEntitySpawnIntents();
                        pending.chunk.confirmBlockEntityIntents();
                    }
                } catch (Exception failure) {
                    pending.chunk.rollbackEntitySpawnIntents();
                    pending.chunk.rollbackBlockEntityIntents();
                    throw new FinalizationException("Could not apply and durably save complete journal intents to " + target, failure);
                }
            }
            return new Prepared<>(pending.worldKey, pending.target, pending.digest, pending.chunk, pending.alreadyApplied);
        }, intentExecutor);
    }

    public void persist(Prepared<C> prepared, Supplier<C> loadedOnlyLookup) throws IOException {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(loadedOnlyLookup, "loadedOnlyLookup");
        if (!worldKey.equals(prepared.worldKey)) throw new IllegalArgumentException("Prepared target belongs to another world");
        C current = loadedOnlyLookup.get();
        validate(prepared.target, current);
        if (current != prepared.chunk) throw new IOException("Target chunk instance changed before persistence: " + prepared.target);
        String currentDigest = PlacementDigest.compute(journal.placementsFor(prepared.target), prepared.target);
        if (!currentDigest.equals(prepared.digest))
            throw new IOException("Projection changed after prepare; refusing to publish stale target " + prepared.target);
        if (journal.isChunkApplied(worldKey, prepared.target, prepared.digest)) return;
        try {
            current.saveDurably();
        } catch (Exception failure) {
            throw new IOException("Target chunk save failed; applied acknowledgement withheld for " + prepared.target, failure);
        }
        // This method rechecks the digest under the journal lock to close the apply/save/ack race.
        journal.appendChunkApplied(worldKey, prepared.target, prepared.digest);
    }

    private static void validate(ChunkPos target, ChunkHandle chunk) {
        if (chunk == null) throw new FinalizationException("Target chunk is not loaded; refusing to force-load " + target);
        if (!target.equals(chunk.position())) throw new FinalizationException("Loaded chunk lookup returned the wrong coordinates");
        if (!chunk.isLoaded() || !chunk.isGenerated() || !chunk.isInitiated())
            throw new FinalizationException("Target chunk is not loaded/generated/initiated: " + target);
    }

    public interface ChunkHandle {
        ChunkPos position();
        boolean isLoaded();
        boolean isGenerated();
        boolean isInitiated();
        void applyProjection(NavigableMap<PlacementProjection.Position, PlacementProjection.Winner> projection) throws Exception;
        default void applyBlockEntityIntents(NavigableMap<PlacementProjection.Position, PlacementProjection.EntityWinner> intents) throws Exception {
            if (!intents.isEmpty()) throw new UnsupportedOperationException("Block entity intents are not supported by this chunk adapter");
        }
        default void rollbackBlockEntityIntents() { }
        default void confirmBlockEntityIntents() { }
        default void applyEntitySpawnIntents(List<org.powernukkitx.terra.crosschunk.journal.EntitySpawnIntent> intents) throws Exception {
            if (!intents.isEmpty()) throw new UnsupportedOperationException("Entity spawn intents are not supported by this chunk adapter");
        }
        default void rollbackEntitySpawnIntents() { }
        default void prepareEntitySpawnIntentsForSave() { }
        default void confirmEntitySpawnIntents() { }
        default void applyPhysicsIntents(List<org.powernukkitx.terra.crosschunk.journal.PhysicsIntent> intents) throws Exception {
            if (intents.stream().anyMatch(org.powernukkitx.terra.crosschunk.journal.PhysicsIntent::applyPhysics))
                throw new UnsupportedOperationException("Physics intents are not supported by this chunk adapter");
        }
        void saveDurably() throws Exception;
    }

    private record Pending<C extends ChunkHandle>(String worldKey, ChunkPos target, String digest,
                                                   C chunk, boolean alreadyApplied, List<PlacementRecord> records) { }

    public static final class Prepared<C extends ChunkHandle> {
        private final String worldKey;
        private final ChunkPos target;
        private final String digest;
        private final C chunk;
        private final boolean alreadyApplied;
        private Prepared(String worldKey, ChunkPos target, String digest, C chunk, boolean alreadyApplied) {
            this.worldKey = worldKey; this.target = target; this.digest = digest;
            this.chunk = chunk; this.alreadyApplied = alreadyApplied;
        }
        public ChunkPos target() { return target; }
        public String digest() { return digest; }
        public boolean alreadyApplied() { return alreadyApplied; }
    }

    public static final class FinalizationException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public FinalizationException(String message) { super(message); }
        public FinalizationException(String message, Throwable cause) { super(message, cause); }
    }
}
