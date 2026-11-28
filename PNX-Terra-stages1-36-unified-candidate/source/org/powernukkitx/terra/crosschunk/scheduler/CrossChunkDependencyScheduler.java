package org.powernukkitx.terra.crosschunk.scheduler;

import org.powernukkitx.terra.crosschunk.journal.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Durable anchor-population barrier. A target waits for every anchor whose possible feature
 * footprint can overlap it. No method loads or generates a chunk.
 */
public final class CrossChunkDependencyScheduler implements AutoCloseable {
    private static final int MAX_REACH_BLOCKS = 4096;
    private final String worldKey;
    private final PlacementJournal journal;
    private final AnchorRequester requester;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean closed;
    private final Map<ChunkPos, AnchorSession> sessions = new HashMap<>();
    private final Map<ChunkPos, TargetBarrier> targets = new HashMap<>();

    public CrossChunkDependencyScheduler(String worldKey, PlacementJournal journal) {
        this(worldKey, journal, anchor -> true);
    }

    public CrossChunkDependencyScheduler(String worldKey, PlacementJournal journal, AnchorRequester requester) {
        if (worldKey == null || worldKey.isBlank()) throw new IllegalArgumentException("worldKey is blank");
        this.worldKey=worldKey; this.journal=Objects.requireNonNull(journal,"journal");
        this.requester=Objects.requireNonNull(requester,"requester");
    }

    /**
     * Register a target's complete conservative source-anchor set. Reach is the maximum horizontal
     * displacement in blocks from any origin point inside an anchor chunk to a write. The default
     * overload requests missing anchors; dependency-prefetch chunks can register without recursion.
     */
    public CompletableFuture<Void> registerTarget(ChunkPos target, int maxReachX, int maxReachZ) {
        return registerTarget(target,maxReachX,maxReachZ,true);
    }

    public CompletableFuture<Void> registerTarget(ChunkPos target, int maxReachX, int maxReachZ, boolean requestDependencies) {
        Objects.requireNonNull(target,"target");
        ensureOpen();
        if(maxReachX<0||maxReachZ<0)throw new IllegalArgumentException("reach cannot be negative");
        if(maxReachX>MAX_REACH_BLOCKS||maxReachZ>MAX_REACH_BLOCKS)throw new IllegalArgumentException("reach exceeds safety limit");
        Set<ChunkPos> expected=expectedAnchors(target,maxReachX,maxReachZ);
        List<ChunkPos> toRequest=new ArrayList<>();
        TargetBarrier barrier;
        lock.lock();try{
            barrier=targets.get(target);
            if(barrier!=null&&!barrier.expected.equals(expected))throw new IllegalStateException("Target registered with different reach envelope");
            if(barrier==null){barrier=new TargetBarrier(expected,maxReachX,maxReachZ);targets.put(target,barrier);}
            if(requestDependencies){
                for(ChunkPos anchor:expected){
                    // A source anchor already being populated in this runtime will publish its own
                    // completion marker. Re-requesting it can recursively queue/reset its stage.
                    if(!journal.isAnchorCompleted(worldKey,anchor)&&!sessions.containsKey(anchor)
                            &&barrier.requested.add(anchor))toRequest.add(anchor);
                }
            }
            refresh(barrier);
        }finally{lock.unlock();}
        for(ChunkPos anchor:toRequest){
            try {
                if (!requester.request(anchor)) {
                    lock.lock(); try { barrier.requested.remove(anchor); } finally { lock.unlock(); }
                }
            } catch(RuntimeException failure){barrier.ready.completeExceptionally(failure);break;}
        }
        if (requestDependencies && !barrier.ready.isDone()) scheduleRetry(barrier);
        return barrier.ready;
    }

    /** Activate a previously dependency-prefetched target when it becomes a player-demanded chunk. */
    public CompletableFuture<Void> activateTarget(ChunkPos target){
        TargetBarrier barrier;
        lock.lock();try{barrier=targets.get(target);}finally{lock.unlock();}
        if(barrier==null)throw new IllegalStateException("Target has not been registered: "+target);
        CompletableFuture<Void> ready = registerTarget(target,barrier.maxReachX,barrier.maxReachZ,true);
        ready.whenComplete((ignored, failure) -> {
            lock.lock();
            try { targets.remove(target, barrier); }
            finally { lock.unlock(); }
        });
        return ready;
    }

    /** Starts one anchor's Terra population lifecycle. Recovered incomplete anchors may be replayed. */
    public AnchorSession openAnchor(ChunkPos anchor) {
        Objects.requireNonNull(anchor,"anchor");
        ensureOpen();
        lock.lock();try{
            if(journal.isAnchorCompleted(worldKey,anchor))throw new IllegalStateException("Anchor already durably completed: "+anchor);
            if(sessions.containsKey(anchor))throw new IllegalStateException("Anchor session already active: "+anchor);
            try {
                journal.appendAnchorStarted(worldKey, anchor);
            } catch (IOException failure) {
                throw new JournalCommitException(failure);
            }
            AnchorSession session=new AnchorSession(anchor);sessions.put(anchor,session);return session;
        }finally{lock.unlock();}
    }

    public boolean isAnchorComplete(ChunkPos anchor){return journal.isAnchorCompleted(worldKey,anchor);}

    /** Exact conservative geometry, using floor division so negative coordinates are correct. */
    public static Set<ChunkPos> expectedAnchors(ChunkPos target,int reachX,int reachZ){
        if(reachX<0||reachZ<0)throw new IllegalArgumentException("reach cannot be negative");
        long minBlockX=(long)target.x()*16, maxBlockX=minBlockX+15;
        long minBlockZ=(long)target.z()*16, maxBlockZ=minBlockZ+15;
        int minAnchorX=ceilDiv(minBlockX-reachX-15,16), maxAnchorX=floorDiv(maxBlockX+reachX,16);
        int minAnchorZ=ceilDiv(minBlockZ-reachZ-15,16), maxAnchorZ=floorDiv(maxBlockZ+reachZ,16);
        TreeSet<ChunkPos> result=new TreeSet<>();
        for(int x=minAnchorX;x<=maxAnchorX;x++)for(int z=minAnchorZ;z<=maxAnchorZ;z++)result.add(new ChunkPos(x,z));
        return Collections.unmodifiableSet(result);
    }
    private static int floorDiv(long a,long b){return Math.toIntExact(Math.floorDiv(a,b));}
    private static int ceilDiv(long a,long b){return Math.toIntExact(-Math.floorDiv(-a,b));}

    /** Queue-full and interrupted generation requests are retried without blocking any generation worker. */
    private void scheduleRetry(TargetBarrier barrier) {
        lock.lock();
        try {
            if (closed || barrier.ready.isDone() || barrier.retryScheduled) return;
            barrier.retryScheduled = true;
        } finally { lock.unlock(); }
        CompletableFuture.delayedExecutor(250, TimeUnit.MILLISECONDS).execute(() -> retryMissingAnchors(barrier));
    }

    private void retryMissingAnchors(TargetBarrier barrier) {
        List<ChunkPos> missing = new ArrayList<>();
        lock.lock();
        try {
            barrier.retryScheduled = false;
            if (closed || barrier.ready.isDone()) return;
            for (ChunkPos anchor : barrier.expected) {
                // An accepted generation request is already in PNX's queue or running. Reissuing
                // it can reset a live anchor back to GENERATED in the runtime requester, causing
                // duplicate population and invalidating its active AnchorSession. Retry only
                // requests that were rejected (queue full) or never accepted.
                if (journal.isAnchorCompleted(worldKey, anchor) || sessions.containsKey(anchor)) continue;
                if (barrier.requested.contains(anchor)) {
                    // A previously accepted request is not retried while PNX still owns its
                    // generation-queue slot. If PNX released the slot without an anchor WAL
                    // completion marker, the generation attempt failed before/inside PopulateStage
                    // and must be submitted again instead of leaving this barrier pending forever.
                    if (requester.isInFlight(anchor)) continue;
                    barrier.requested.remove(anchor);
                }
                missing.add(anchor);
            }
        } finally { lock.unlock(); }
        for (ChunkPos anchor : missing) {
            try {
                boolean accepted = requester.request(anchor);
                lock.lock();
                try {
                    if (accepted) barrier.requested.add(anchor);
                    else barrier.requested.remove(anchor);
                } finally { lock.unlock(); }
            } catch (RuntimeException failure) {
                barrier.ready.completeExceptionally(failure);
                return;
            }
        }
        if (!barrier.ready.isDone()) scheduleRetry(barrier);
    }

    private void refresh(TargetBarrier barrier){
        if(barrier.ready.isDone())return;
        for(ChunkPos anchor:barrier.expected)if(!journal.isAnchorCompleted(worldKey,anchor))return;
        barrier.ready.complete(null);
    }
    private void refreshAll(){for(TargetBarrier barrier:targets.values())refresh(barrier);}

    @FunctionalInterface
    public interface AnchorRequester {
        boolean request(ChunkPos anchor);

        /**
         * True while PNX still owns an accepted generation request for this anchor. The default
         * preserves conservative behavior for requesters that cannot observe their queue state.
         */
        default boolean isInFlight(ChunkPos anchor) { return true; }
    }

    private final class TargetBarrier{
        final Set<ChunkPos> expected;final int maxReachX,maxReachZ;
        final Set<ChunkPos> requested=new HashSet<>();
        final CompletableFuture<Void> ready=new CompletableFuture<>();
        boolean retryScheduled;
        TargetBarrier(Set<ChunkPos> expected,int maxReachX,int maxReachZ){this.expected=expected;this.maxReachX=maxReachX;this.maxReachZ=maxReachZ;}
    }

    public final class AnchorSession implements AutoCloseable{
        private final ChunkPos anchor;private int inFlight;private long nextPlacementOrdinal;private boolean populationFinished;private boolean completed;private boolean failed;
        private AnchorSession(ChunkPos anchor){this.anchor=anchor;}
        public ChunkPos anchor(){return anchor;}
        public PlacementTicket beginPlacement(){
            lock.lock();try{
                ensureOpen();
                if(populationFinished)throw new IllegalStateException("Population already marked finished");
                if(nextPlacementOrdinal==Long.MAX_VALUE)throw new IllegalStateException("Per-anchor placement ordinal exhausted");
                long ordinal=nextPlacementOrdinal;
                nextPlacementOrdinal=Math.incrementExact(nextPlacementOrdinal);
                inFlight++;
                return new PlacementTicket(this, ordinal);
            }finally{lock.unlock();}
        }
        /** Called only after Terra PopulateStage has finished selecting/running all features for this anchor. */
        public void finishPopulation() throws IOException{
            lock.lock();try{ensureOpen();populationFinished=true;tryComplete();}finally{lock.unlock();}
        }
        private void ticketFinished(){lock.lock();try{if(inFlight<=0)throw new IllegalStateException("Placement ticket underflow");inFlight--;tryComplete();}catch(IOException e){failed=true;throw new JournalCommitException(e);}finally{lock.unlock();}}
        private void tryComplete() throws IOException{
            if(populationFinished&&inFlight==0&&!completed&&!failed){journal.appendAnchorCompleted(worldKey,anchor);completed=true;sessions.remove(anchor);refreshAll();}
        }
        private void ensureOpen(){if(completed||failed||sessions.get(anchor)!=this)throw new IllegalStateException("Anchor session is not active");}
        @Override public void close(){lock.lock();try{if(!completed){failed=true;sessions.remove(anchor);}}finally{lock.unlock();}}
    }

    public final class PlacementTicket implements AutoCloseable{
        private final AnchorSession session;private final long ordinal;private boolean finished;
        private PlacementTicket(AnchorSession session, long ordinal){this.session=session;this.ordinal=ordinal;}
        /** Deterministic per-anchor root-placement ordinal, replayed from zero with the anchor. */
        public long ordinal(){return ordinal;}
        /** WAL append is fsynced before the ticket is released and before the anchor can complete. */
        public long commit(PlacementRecord record) throws IOException{
            if(finished)throw new IllegalStateException("Ticket already finished");
            lock.lock();
            try { session.ensureOpen(); } finally { lock.unlock(); }
            if(!record.key().worldKey().equals(worldKey)||!record.key().anchor().equals(session.anchor))
                throw new IllegalArgumentException("Placement key does not belong to this world/anchor session");
            if(record.key().placementOrdinal()!=ordinal)
                throw new IllegalArgumentException("Placement key ordinal does not match its ticket");
            long sequence=journal.appendPlacement(record);finished=true;session.ticketFinished();return sequence;
        }
        public void abort(){if(finished)return;finished=true;session.ticketFinished();}
        @Override public void close(){abort();}
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Cross-chunk dependency scheduler is closed");
    }

    @Override public void close() {
        lock.lock();
        try {
            if (closed) return;
            closed = true;
            for (TargetBarrier barrier : targets.values()) {
                if (!barrier.ready.isDone()) barrier.ready.completeExceptionally(
                        new IllegalStateException("Cross-chunk dependency scheduler closed"));
            }
            for (AnchorSession session : sessions.values()) session.failed = true;
            sessions.clear();
        } finally { lock.unlock(); }
    }

    public static final class JournalCommitException extends RuntimeException{
        private static final long serialVersionUID = 1L;
        public JournalCommitException(IOException cause){super("Failed to durably complete anchor population",cause);}
    }
}
