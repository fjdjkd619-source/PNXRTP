package org.powernukkitx.terra.crosschunk.journal.pnx;

import org.powernukkitx.entity.Entity;
import org.powernukkitx.level.format.IChunk;
import org.powernukkitx.math.Vector3;
import org.powernukkitx.nbt.tag.CompoundTag;
import org.powernukkitx.terra.crosschunk.journal.ChunkPos;
import org.powernukkitx.terra.crosschunk.journal.EntitySpawnIntent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Idempotent PNX entity creation for a single already-loaded target chunk.
 * New entities are kept invisible until the chunk's synchronous durable save succeeds.
 */
public final class PnxEntitySpawnIntentApplier {
    private PnxEntitySpawnIntentApplier() { }

    public static Batch apply(IChunk chunk, List<EntitySpawnIntent> intents) throws IOException {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(intents, "intents");
        if (intents.isEmpty()) return new Batch(List.of(), List.of());
        if (!chunk.isLoaded() || !chunk.isGenerated() || !chunk.isInitiated()) {
            throw new IOException("Entity spawn intents require a loaded, generated, initiated target chunk");
        }
        if (chunk.getProvider() == null || chunk.getProvider().getLevel() == null) {
            throw new IOException("Entity target chunk has no live PNX level");
        }

        Map<UUID, Entity> existingByUuid = new HashMap<>();
        for (Entity existing : chunk.getEntities().values()) {
            if (existing == null || existing.isClosed() || existing.getUniqueId() == null) continue;
            Entity duplicate = existingByUuid.putIfAbsent(existing.getUniqueId(), existing);
            if (duplicate != null && duplicate != existing) {
                throw new IOException("Target chunk already contains duplicate entity UUID " + existing.getUniqueId());
            }
        }

        List<Entity> all = new ArrayList<>(intents.size());
        List<Entity> created = new ArrayList<>(intents.size());
        try {
            for (EntitySpawnIntent intent : intents) {
                validateTarget(chunk, intent);
                if (intent.payload().length != 0) {
                    throw new IOException("PNX entity NBT payload decoding is not enabled; refusing opaque entity payload");
                }
                Entity existing = existingByUuid.get(intent.stableUuid());
                if (existing != null) {
                    verifyExisting(existing, intent);
                    all.add(existing);
                    continue;
                }

                CompoundTag nbt = Entity.getDefaultNBT(
                        new Vector3(intent.x(), intent.y(), intent.z()), null, intent.yaw(), intent.pitch())
                        .putString("uuid", intent.stableUuid().toString());
                Entity entity = Entity.createEntityForGeneration(intent.type(), chunk, nbt);
                if (entity == null) throw new IOException("PNX entity registry has no factory for " + intent.type());
                created.add(entity);
                if (entity.isClosed()) throw new IOException("PNX cancelled or rejected entity spawn " + intent.stableUuid());
                verifyExisting(entity, intent);
                all.add(entity);
                existingByUuid.put(intent.stableUuid(), entity);
            }
            return new Batch(created, all);
        } catch (Throwable failure) {
            rollbackCreated(created, failure);
            if (failure instanceof IOException ioFailure) throw ioFailure;
            if (failure instanceof Error error) throw error;
            throw new IOException("Could not apply PNX entity spawn intents; created entities were rolled back", failure);
        }
    }

    private static void validateTarget(IChunk chunk, EntitySpawnIntent intent) throws IOException {
        if (!new ChunkPos(chunk.getX(), chunk.getZ()).equals(intent.chunk())) {
            throw new IOException("Entity spawn intent targets a different chunk: " + intent.stableUuid());
        }
        if (intent.y() < chunk.getDimensionData().getMinHeight()
                || intent.y() >= chunk.getDimensionData().getMaxHeight()) {
            throw new IOException("Entity spawn intent is outside world height: " + intent.stableUuid());
        }
    }

    private static void verifyExisting(Entity entity, EntitySpawnIntent intent) throws IOException {
        if (!intent.stableUuid().equals(entity.getUniqueId()) || !intent.type().equals(entity.getIdentifier())
                || !near(entity.getX(), intent.x()) || !near(entity.getY(), intent.y()) || !near(entity.getZ(), intent.z())
                || !near(entity.getYaw(), intent.yaw()) || !near(entity.getPitch(), intent.pitch())) {
            throw new IOException("Existing PNX entity conflicts with durable spawn intent " + intent.stableUuid());
        }
    }

    private static boolean near(double left, double right) {
        return Math.abs(left - right) <= 1.0e-5;
    }

    /** Add staged entities to the chunk's save set without registering them in the ticking Level. */
    public static void prepareForDurableSave(Batch batch) {
        Objects.requireNonNull(batch, "batch");
        for (Entity entity : batch.created()) {
            if (!entity.registerForGenerationSave()) {
                throw new IllegalStateException("Could not stage generation entity for durable save " + entity.getUniqueId());
            }
        }
    }

    /** Activate and publish only after the synchronous chunk save has completed successfully. */
    public static void confirm(Batch batch) {
        Objects.requireNonNull(batch, "batch");
        for (Entity entity : batch.created()) {
            if (!entity.activateAfterGenerationCommit()) {
                throw new IllegalStateException("Could not activate staged generation entity " + entity.getUniqueId());
            }
        }
        for (Entity entity : batch.created()) entity.spawnToAll();
    }

    public static void rollback(Batch batch) {
        Objects.requireNonNull(batch, "batch");
        rollbackCreated(batch.created(), null);
    }

    private static void rollbackCreated(List<Entity> created, Throwable originalFailure) {
        for (int i = created.size() - 1; i >= 0; i--) {
            try {
                created.get(i).closeForGenerationRollback();
            } catch (Throwable rollbackFailure) {
                if (originalFailure != null) originalFailure.addSuppressed(rollbackFailure);
            }
        }
    }

    public record Batch(List<Entity> created, List<Entity> all) {
        public Batch {
            created = List.copyOf(created);
            all = List.copyOf(all);
        }
    }
}
