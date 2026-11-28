package org.powernukkitx.terra.crosschunk.journal.pnx;

import org.powernukkitx.blockentity.BlockEntity;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.format.IChunk;
import org.powernukkitx.nbt.tag.CompoundTag;
import org.powernukkitx.registry.Registries;
import org.powernukkitx.terra.crosschunk.journal.BlockEntityIntent;
import org.powernukkitx.terra.crosschunk.journal.ChunkPos;
import org.powernukkitx.terra.crosschunk.journal.PlacementProjection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.NavigableMap;
import java.util.Objects;

/** Applies a fully prevalidated, deterministic batch of BlockEntity intents to one loaded PNX chunk. */
public final class PnxBlockEntityIntentApplier {
    private PnxBlockEntityIntentApplier() { }

    public static List<BlockEntity> apply(IChunk chunk,
                             NavigableMap<PlacementProjection.Position, PlacementProjection.EntityWinner> intents)
            throws IOException {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(intents, "intents");
        if (intents.isEmpty()) return List.of();
        if (!chunk.isLoaded() || !chunk.isGenerated() || !chunk.isInitiated()) {
            throw new IOException("BlockEntity intents require a loaded, generated, initiated target chunk");
        }
        if (chunk.getProvider() == null || chunk.getProvider().getLevel() == null) {
            throw new IOException("BlockEntity target chunk has no live PNX level");
        }
        Level level = chunk.getProvider().getLevel();
        List<PreparedEntity> prepared = preflight(chunk, level, intents);
        Set<BlockEntity> preexisting = Collections.newSetFromMap(new IdentityHashMap<>());
        preexisting.addAll(chunk.getBlockEntities().values());
        List<BlockEntity> created = new ArrayList<>();
        try {
            for (PreparedEntity entity : prepared) {
                if (entity.alreadyPresent) continue;
                BlockEntity blockEntity = BlockEntity.createBlockEntity(entity.canonicalType, chunk, entity.tag.copy());
                if (blockEntity == null) {
                    throw new IOException("PNX factory returned null for BlockEntity type " + entity.canonicalType);
                }
                created.add(blockEntity);
                if (!entity.type.isInstance(blockEntity)
                        || blockEntity.chunk != chunk
                        || blockEntity.getLevel() != level
                        || (int) blockEntity.getX() != entity.x
                        || (int) blockEntity.getY() != entity.y
                        || (int) blockEntity.getZ() != entity.z) {
                    throw new IOException("PNX BlockEntity factory returned an object with mismatched type, world, or coordinates");
                }
                blockEntity.saveNBT();
                if (!entity.intentHash.equals(blockEntity.getNbt().getString(PnxBlockEntityNbtCodec.INTENT_HASH_TAG))) {
                    throw new IOException("PNX BlockEntity constructor discarded its durable intent hash at "
                            + entity.x + "," + entity.y + "," + entity.z);
                }
            }
            return List.copyOf(created);
        } catch (Exception failure) {
            // The PNX factory can swallow constructor failures after the base constructor has
            // already registered the object. Snapshot the chunk registry so even an object whose
            // constructor never returned is found and closed during rollback.
            List<BlockEntity> afterFailure = new ArrayList<>(chunk.getBlockEntities().values());
            for (int i = afterFailure.size() - 1; i >= 0; i--) {
                BlockEntity candidate = afterFailure.get(i);
                if (preexisting.contains(candidate)) continue;
                try {
                    candidate.close();
                } catch (RuntimeException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            if (failure instanceof IOException ioFailure) throw ioFailure;
            throw new IOException("Could not apply BlockEntity intent batch; created entities were rolled back", failure);
        }
    }

    private static List<PreparedEntity> preflight(
            IChunk chunk, Level level,
            NavigableMap<PlacementProjection.Position, PlacementProjection.EntityWinner> intents) throws IOException {
        List<PreparedEntity> prepared = new ArrayList<>(intents.size());
        ChunkPos target = new ChunkPos(chunk.getX(), chunk.getZ());
        for (var entry : intents.entrySet()) {
            PlacementProjection.Position position = entry.getKey();
            BlockEntityIntent intent = entry.getValue().intent();
            if (position.layer() != 0 || position.x() != intent.x() || position.y() != intent.y()
                    || position.z() != intent.z() || !target.equals(intent.chunk())) {
                throw new IOException("BlockEntity projection key does not match its target intent: " + position);
            }
            if (position.y() < chunk.getDimensionData().getMinHeight()
                    || position.y() >= chunk.getDimensionData().getMaxHeight()) {
                throw new IOException("BlockEntity intent is outside world height: " + position);
            }
            Class<? extends BlockEntity> type = Registries.BLOCKENTITY.get(intent.type());
            if (type == null) throw new IOException("Unknown PNX BlockEntity type: " + intent.type());
            String canonicalType = Registries.BLOCKENTITY.getSaveId(type);
            if (canonicalType == null || canonicalType.isBlank() || !canonicalType.equals(intent.type())) {
                throw new IOException("BlockEntity intent must use the canonical PNX save ID: " + intent.type());
            }
            CompoundTag tag = PnxBlockEntityNbtCodec.fromIntent(intent);
            String intentHash = tag.getString(PnxBlockEntityNbtCodec.INTENT_HASH_TAG);
            BlockEntity existing = chunk.getTile(position.x() & 15, position.y(), position.z() & 15);
            boolean alreadyPresent = false;
            if (existing != null) {
                if (!type.isInstance(existing) || existing.chunk != chunk || existing.getLevel() != level) {
                    throw new IOException("Conflicting BlockEntity already occupies " + position);
                }
                existing.saveNBT();
                if (!intentHash.equals(existing.getNbt().getString(PnxBlockEntityNbtCodec.INTENT_HASH_TAG))) {
                    throw new IOException("Existing BlockEntity differs from the durable intent at " + position);
                }
                alreadyPresent = true;
            }
            prepared.add(new PreparedEntity(position.x(), position.y(), position.z(), type, canonicalType,
                    tag, intentHash, alreadyPresent));
        }
        return List.copyOf(prepared);
    }

    private record PreparedEntity(int x, int y, int z, Class<? extends BlockEntity> type,
                                  String canonicalType, CompoundTag tag, String intentHash,
                                  boolean alreadyPresent) { }
}
