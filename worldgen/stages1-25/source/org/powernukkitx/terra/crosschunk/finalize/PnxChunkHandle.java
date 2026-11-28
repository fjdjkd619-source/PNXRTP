package org.powernukkitx.terra.crosschunk.finalize;

import org.powernukkitx.block.BlockState;
import org.powernukkitx.level.format.IChunk;
import org.powernukkitx.level.format.leveldb.LevelDBProvider;
import org.powernukkitx.terra.crosschunk.journal.ChunkPos;
import org.powernukkitx.terra.crosschunk.journal.PlacementProjection;
import org.powernukkitx.terra.crosschunk.journal.pnx.PnxBlockStateCodec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;

/** PNX 3.0.6 chunk adapter. It only ever mutates the already-loaded target chunk instance. */
public final class PnxChunkHandle implements ChunkFinalizationCoordinator.ChunkHandle {
    private final IChunk chunk;
    private List<org.powernukkitx.blockentity.BlockEntity> pendingBlockEntityRollback = List.of();

    public PnxChunkHandle(IChunk chunk) {
        this.chunk = java.util.Objects.requireNonNull(chunk, "chunk");
    }

    public IChunk chunk() { return chunk; }
    @Override public ChunkPos position() { return new ChunkPos(chunk.getX(), chunk.getZ()); }
    @Override public boolean isLoaded() { return chunk.isLoaded(); }
    @Override public boolean isGenerated() { return chunk.isGenerated(); }
    @Override public boolean isInitiated() { return chunk.isInitiated(); }

    @Override
    public void applyProjection(NavigableMap<PlacementProjection.Position, PlacementProjection.Winner> projection) throws IOException {
        List<DecodedWrite> decoded = new ArrayList<>(projection.size());
        for (var entry : projection.entrySet()) {
            PlacementProjection.Position pos = entry.getKey();
            if (!position().equals(ChunkPos.fromBlock(pos.x(), pos.z())))
                throw new IOException("Projection contains a write outside target chunk: " + pos);
            if (pos.y() < chunk.getDimensionData().getMinHeight() || pos.y() >= chunk.getDimensionData().getMaxHeight())
                throw new IOException("Projection write is outside world height: " + pos);
            BlockState state = PnxBlockStateCodec.decode(entry.getValue().write().canonicalState());
            decoded.add(new DecodedWrite(pos.x() & 15, pos.y(), pos.z() & 15, pos.layer(), state));
        }
        // Decode and validate the entire projection before taking the chunk write lock.
        chunk.batchProcess(unsafe -> {
            for (DecodedWrite write : decoded) {
                unsafe.setBlockState(write.localX, write.y, write.localZ, write.state, write.layer);
            }
        });
        // PNX 3.0.6 batchProcess logs and swallows callback exceptions. Verify every write so a
        // partial batch can never receive a durable journal acknowledgement.
        for (DecodedWrite write : decoded) {
            if (!chunk.getBlockState(write.localX, write.y, write.localZ, write.layer).equals(write.state)) {
                throw new IOException("PNX chunk batch did not apply expected block state at "
                        + (chunk.getX() * 16 + write.localX) + "," + write.y + "," + (chunk.getZ() * 16 + write.localZ));
            }
        }
        chunk.recalculateHeightMap();
        chunk.setLightPopulated(false);
        chunk.setChanged();
        chunk.reObfuscateChunk();
    }

    @Override
    public void applyBlockEntityIntents(NavigableMap<PlacementProjection.Position, PlacementProjection.EntityWinner> intents)
            throws IOException {
        pendingBlockEntityRollback = PnxBlockEntityIntentApplier.apply(chunk, intents);
    }

    @Override
    public void rollbackBlockEntityIntents() {
        List<org.powernukkitx.blockentity.BlockEntity> rollback = pendingBlockEntityRollback;
        pendingBlockEntityRollback = List.of();
        for (int i = rollback.size() - 1; i >= 0; i--) {
            try {
                rollback.get(i).close();
            } catch (RuntimeException ignored) {
                // Preserve the original finalization/save failure; all remaining objects still get closed.
            }
        }
    }

    @Override
    public void confirmBlockEntityIntents() {
        pendingBlockEntityRollback = List.of();
    }

    @Override
    public void saveDurably() throws IOException {
        if (chunk.getProvider() == null) throw new IOException("Target chunk has no storage provider");
        if (!(chunk.getProvider() instanceof LevelDBProvider provider)) {
            throw new IOException("Durable Terra finalization requires the pinned LevelDBProvider; refusing a non-durable fallback");
        }
        // Match PNX's save-race discipline: clear before serialization so a concurrent
        // mutation can re-mark the chunk dirty instead of being erased after the write.
        chunk.setChanged(false);
        try {
            // Bypass LevelDBProvider.saveChunk(): it deliberately uses sync(false) for autosaves.
            // The WAL acknowledgement must never precede a synchronous LevelDB write.
            provider.getStorage().writeChunkDurable(chunk);
        } catch (IOException failure) {
            chunk.setChanged(true);
            throw failure;
        }
    }

    private record DecodedWrite(int localX, int y, int localZ, int layer, BlockState state) {}
}