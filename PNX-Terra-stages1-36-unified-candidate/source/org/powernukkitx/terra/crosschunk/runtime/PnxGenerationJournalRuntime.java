package org.powernukkitx.terra.crosschunk.runtime;

import org.powernukkitx.level.Level;
import org.powernukkitx.level.format.IChunk;
import org.powernukkitx.terra.crosschunk.journal.ChunkPos;
import org.powernukkitx.terra.crosschunk.journal.PlacementJournal;
import org.powernukkitx.terra.crosschunk.finalize.ChunkFinalizationCoordinator;
import org.powernukkitx.terra.crosschunk.scheduler.CrossChunkDependencyScheduler;
import org.powernukkitx.terra.crosschunk.lifecycle.RuntimeCloseSequence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Per-world journal runtime shared by concurrent PNX chunk-population workers. */
public final class PnxGenerationJournalRuntime implements AutoCloseable {
    private final String worldKey;
    private final Level level;
    private final PlacementJournal journal;
    private final CrossChunkDependencyScheduler scheduler;
    private final Set<ChunkPos> dependencyPrefetch = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<ChunkPos, ChunkFinalizationCoordinator.Prepared<?>> pendingFinalizations = new ConcurrentHashMap<>();

    private PnxGenerationJournalRuntime(String worldKey, Level level, PlacementJournal journal) {
        this.worldKey=worldKey;this.level=level;this.journal=journal;
        this.scheduler=new CrossChunkDependencyScheduler(worldKey,journal,new CrossChunkDependencyScheduler.AnchorRequester() {
            @Override public boolean request(ChunkPos anchor) { return requestAnchor(anchor); }
            @Override public boolean isInFlight(ChunkPos anchor) {
                return level.isChunkGenerating(anchor.x(), anchor.z());
            }
        });
    }

    public static PnxGenerationJournalRuntime open(Path directory,Level level,int dimensionId)throws IOException{
        Objects.requireNonNull(directory,"directory");Objects.requireNonNull(level,"level");
        String worldKey=level.getName()+"|"+level.getSeed()+"|dimension="+dimensionId;
        Path wal=directory.resolve(sha256(worldKey)+".pnxterra.wal");
        return new PnxGenerationJournalRuntime(worldKey,level,new PlacementJournal(wal));
    }
    public String worldKey(){return worldKey;}
    public CrossChunkDependencyScheduler scheduler(){return scheduler;}
    public CompletableFuture<Void> registerTarget(ChunkPos target,int maxReachBlocks,boolean requestDependencies){
        return scheduler.registerTarget(target,maxReachBlocks,maxReachBlocks,requestDependencies);
    }
    public CompletableFuture<Void> activateTarget(ChunkPos target){return scheduler.activateTarget(target);}
    public CrossChunkDependencyScheduler.AnchorSession openAnchor(ChunkPos anchor){return scheduler.openAnchor(anchor);}
    public PlacementJournal journal(){return journal;}
    public <T extends ChunkFinalizationCoordinator.ChunkHandle> void putPendingFinalization(
            ChunkPos target, ChunkFinalizationCoordinator.Prepared<T> prepared) {
        pendingFinalizations.put(target, prepared);
    }
    @SuppressWarnings("unchecked")
    public <T extends ChunkFinalizationCoordinator.ChunkHandle> ChunkFinalizationCoordinator.Prepared<T> takePendingFinalization(ChunkPos target) {
        return (ChunkFinalizationCoordinator.Prepared<T>) pendingFinalizations.remove(target);
    }

    /** True once for chunks queued only as source anchors; prevents infinite recursive prefetch. */
    public boolean consumeDependencyPrefetch(ChunkPos anchor){return dependencyPrefetch.remove(anchor);}

    /** Non-blocking, queue-aware source-anchor request. False means PNX's generation queue was full. */
    private boolean requestAnchor(ChunkPos anchor){
        if(journal.isAnchorCompleted(worldKey,anchor))return true;
        IChunk loaded=level.getChunkIfLoaded(anchor.x(),anchor.z());
        if(loaded!=null&&loaded.getChunkState().canSend()){
            if (journal.isAnchorStarted(worldKey, anchor) && !journal.isAnchorCompleted(worldKey, anchor)) {
                // Crash recovery: this exact anchor had entered Terra population but never committed
                // its completion marker. Re-run only the population tail from GENERATED.
                loaded.setChunkState(org.powernukkitx.level.format.ChunkState.GENERATED);
                loaded.setChanged();
            } else {
                throw new IllegalStateException("Existing finished chunk has no Terra anchor WAL marker: "+anchor+
                        ". Refusing to treat an unjournaled chunk as a complete dependency.");
            }
        }
        dependencyPrefetch.add(anchor);
        // Request only the Terra PopulateStage. Prefetched anchors must write their root placements
        // durably but must not recursively activate their own target barriers until player demand.
        boolean accepted = level.generateChunkToStage(anchor.x(), anchor.z(), "terra_populate",
                journal.isAnchorStarted(worldKey, anchor));
        if (accepted || journal.isAnchorCompleted(worldKey, anchor)) return true;
        dependencyPrefetch.remove(anchor);
        return false;
    }

    private static String sha256(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 unavailable",impossible);}
    }
    @Override public void close()throws IOException{RuntimeCloseSequence.closeAll(java.util.List.of(scheduler,journal));}
}
