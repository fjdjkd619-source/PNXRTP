package org.powernukkitx.terra.crosschunk.finalize;

import org.powernukkitx.Server;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.format.IChunk;
import org.powernukkitx.terra.crosschunk.finalize.ChunkFinalizationCoordinator.Prepared;
import org.powernukkitx.terra.crosschunk.journal.ChunkPos;
import org.powernukkitx.terra.crosschunk.runtime.PnxGenerationJournalRuntime;
import org.powernukkitx.terra.crosschunk.runtime.PnxLevelExecutor;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/** PNX-facing async two-phase finalization. No blocking wait and no force-loading. */
public final class PnxCrossChunkFinalizer {
    private PnxCrossChunkFinalizer() {}

    public static CompletableFuture<Void> applyTarget(PnxGenerationJournalRuntime runtime, Level level, IChunk generatingChunk) {
        ChunkPos target = new ChunkPos(generatingChunk.getX(), generatingChunk.getZ());
        ChunkFinalizationCoordinator<PnxChunkHandle> coordinator = new ChunkFinalizationCoordinator<>(
                runtime.worldKey(), runtime.journal(), Server.getInstance().getComputeThreadPool(),
                PnxLevelExecutor.forLevel(level));
        return coordinator.prepare(target, runtime.activateTarget(target),
                        () -> loadedHandle(level, target, generatingChunk))
                .thenAccept(prepared -> runtime.putPendingFinalization(target, prepared));
    }

    public static void persistTarget(PnxGenerationJournalRuntime runtime, Level level, IChunk generatingChunk) throws IOException {
        ChunkPos target = new ChunkPos(generatingChunk.getX(), generatingChunk.getZ());
        Prepared<PnxChunkHandle> prepared = runtime.takePendingFinalization(target);
        if (prepared == null) throw new IOException("Missing prepared cross-chunk finalization for " + target);
        ChunkFinalizationCoordinator<PnxChunkHandle> coordinator = new ChunkFinalizationCoordinator<>(
                runtime.worldKey(), runtime.journal(), Runnable::run);
        coordinator.persist(prepared, () -> loadedHandle(level, target, generatingChunk));
    }

    private static PnxChunkHandle loadedHandle(Level level, ChunkPos target, IChunk expected) {
        IChunk loaded = level.getChunkIfLoaded(target.x(), target.z());
        if (loaded == null || loaded != expected) return null;
        return new PnxChunkHandle(loaded);
    }

}