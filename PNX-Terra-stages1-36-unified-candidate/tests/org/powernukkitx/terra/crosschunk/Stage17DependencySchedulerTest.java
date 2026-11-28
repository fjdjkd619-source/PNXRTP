package org.powernukkitx.terra.crosschunk;

import org.powernukkitx.terra.crosschunk.journal.*;
import org.powernukkitx.terra.crosschunk.scheduler.CrossChunkDependencyScheduler;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Stage17DependencySchedulerTest {
    public static void main(String[] args) throws Exception {
        check(CrossChunkDependencyScheduler.expectedAnchors(new ChunkPos(0,0),0,0).equals(Set.of(new ChunkPos(0,0))),"zero reach anchor set");
        Set<ChunkPos> neg=CrossChunkDependencyScheduler.expectedAnchors(new ChunkPos(-1,-1),16,16);
        check(neg.contains(new ChunkPos(-2,-2))&&neg.contains(new ChunkPos(-1,-1))&&neg.contains(new ChunkPos(0,0)),"negative and positive overlap envelope");

        Path wal=Files.createTempDirectory("pnx-stage17-").resolve("world.wal");
        try(PlacementJournal journal=new PlacementJournal(wal)){
            CrossChunkDependencyScheduler scheduler=new CrossChunkDependencyScheduler("world",journal);
            Set<ChunkPos> expected=CrossChunkDependencyScheduler.expectedAnchors(new ChunkPos(0,0),0,0);
            CompletableFuture<Void> ready=scheduler.registerTarget(new ChunkPos(0,0),0,0);
            check(!ready.isDone(),"target initially blocked");
            CrossChunkDependencyScheduler.AnchorSession session=scheduler.openAnchor(new ChunkPos(0,0));
            var ticket=session.beginPlacement();
            var secondTicket=session.beginPlacement();
            check(ticket.ordinal()==0L && secondTicket.ordinal()==1L, "root placements receive unique per-anchor ordinals");
            secondTicket.abort();
            var mismatchedTicket=session.beginPlacement();
            long sequenceBeforeMismatch=journal.lastSequence();
            boolean ordinalMismatchRejected=false;
            try {
                PlacementKey wrongOrdinal=new PlacementKey("world",1,new ChunkPos(0,0),"feature-mismatch","structure",2,64,2,0,3);
                mismatchedTicket.commit(new PlacementRecord(wrongOrdinal,List.of()));
            } catch(IllegalArgumentException expectedFailure) {
                ordinalMismatchRejected=true;
            }
            check(ordinalMismatchRejected,"ticket rejects a journal record carrying another placement ordinal");
            check(journal.lastSequence()==sequenceBeforeMismatch,"ordinal mismatch is rejected before any WAL append");
            mismatchedTicket.abort();
            session.finishPopulation();
            check(!ready.isDone(),"population cannot complete with in-flight root placement");
            PlacementKey key=new PlacementKey("world",1,new ChunkPos(0,0),"feature","structure",1,64,1,0,0);
            ticket.commit(new PlacementRecord(key,List.of(new BlockWrite(1,64,1,0,"stone".getBytes(StandardCharsets.UTF_8)))));
            check(ready.isDone(),"target releases only after durable placement and anchor marker");
            check(scheduler.isAnchorComplete(new ChunkPos(0,0)),"anchor marker persisted");
        }
        try(PlacementJournal recovered=new PlacementJournal(wal)){
            CrossChunkDependencyScheduler scheduler=new CrossChunkDependencyScheduler("world",recovered);
            CompletableFuture<Void> ready=scheduler.registerTarget(new ChunkPos(0,0),0,0);
            check(ready.isDone(),"completed dependencies restored after restart");
        }
        Path wideWal=Files.createTempDirectory("pnx-stage17-wide-").resolve("world.wal");
        try(PlacementJournal journal=new PlacementJournal(wideWal)){
            CrossChunkDependencyScheduler scheduler=new CrossChunkDependencyScheduler("world",journal);
            var ready=scheduler.registerTarget(new ChunkPos(0,0),16,16,false);
            Set<ChunkPos> anchors=CrossChunkDependencyScheduler.expectedAnchors(new ChunkPos(0,0),16,16);
            check(anchors.size()==9,"wide dependency envelope includes all overlapping anchor chunks");
            int completed=0;
            for(ChunkPos anchor:anchors){var session=scheduler.openAnchor(anchor);session.finishPopulation();completed++;if(completed<anchors.size())check(!ready.isDone(),"target remains blocked until every source anchor completes");}
            check(ready.isDone(),"wide target barrier releases after final source anchor");
        }

        Path requestWal=Files.createTempDirectory("pnx-stage17-request-").resolve("world.wal");
        try(PlacementJournal journal=new PlacementJournal(requestWal)){
            AtomicInteger attempts=new AtomicInteger();
            CrossChunkDependencyScheduler scheduler=new CrossChunkDependencyScheduler("world",journal,
                    anchor -> attempts.incrementAndGet()>1);
            scheduler.registerTarget(new ChunkPos(0,0),0,0,false);
            check(attempts.get()==0,"dependency-prefetch registration does not recurse");
            scheduler.activateTarget(new ChunkPos(0,0));
            check(attempts.get()==1,"activation retries one missing source anchor");
            scheduler.activateTarget(new ChunkPos(0,0));
            check(attempts.get()==2,"queue-rejected anchor can be requested again");
        }

        Path activeWal=Files.createTempDirectory("pnx-stage17-active-").resolve("world.wal");
        try(PlacementJournal journal=new PlacementJournal(activeWal)){
            AtomicInteger requests=new AtomicInteger();
            CrossChunkDependencyScheduler scheduler=new CrossChunkDependencyScheduler("world",journal,
                    anchor -> { requests.incrementAndGet(); return true; });
            var activeSession=scheduler.openAnchor(new ChunkPos(0,0));
            CompletableFuture<Void> ready=scheduler.registerTarget(new ChunkPos(0,0),0,0,true);
            check(requests.get()==0,"currently populating target anchor is not recursively requested");
            check(!ready.isDone(),"target barrier waits for its active anchor session");
            activeSession.finishPopulation();
            check(ready.isDone(),"active anchor completion releases its own target barrier");
            scheduler.close();
        }

        Path acceptedWal=Files.createTempDirectory("pnx-stage17-accepted-").resolve("world.wal");
        try(PlacementJournal journal=new PlacementJournal(acceptedWal)){
            AtomicInteger acceptedCalls=new AtomicInteger();
            CrossChunkDependencyScheduler scheduler=new CrossChunkDependencyScheduler("world",journal,
                    anchor -> { acceptedCalls.incrementAndGet(); return true; });
            CompletableFuture<Void> ready=scheduler.registerTarget(new ChunkPos(0,0),0,0,true);
            check(acceptedCalls.get()==1,"accepted anchor request is submitted once");
            Thread.sleep(650L); // allow multiple 250 ms retry windows to elapse
            check(acceptedCalls.get()==1,"accepted in-flight anchor is not resubmitted by retry loop");
            var session=scheduler.openAnchor(new ChunkPos(0,0));
            session.finishPopulation();
            check(ready.isDone(),"accepted anchor completion releases target");
            scheduler.close();
        }


        Path failedAcceptedWal=Files.createTempDirectory("pnx-stage17-failed-accepted-").resolve("world.wal");
        try(PlacementJournal journal=new PlacementJournal(failedAcceptedWal)){
            AtomicInteger acceptedAttempts=new AtomicInteger();
            AtomicBoolean pnxQueueOwnsRequest=new AtomicBoolean(true);
            CountDownLatch retried=new CountDownLatch(1);
            CrossChunkDependencyScheduler scheduler=new CrossChunkDependencyScheduler("world",journal,
                    new CrossChunkDependencyScheduler.AnchorRequester() {
                        @Override public boolean request(ChunkPos anchor) {
                            int attempt=acceptedAttempts.incrementAndGet();
                            if(attempt==2) retried.countDown();
                            pnxQueueOwnsRequest.set(true);
                            return true;
                        }
                        @Override public boolean isInFlight(ChunkPos anchor) { return pnxQueueOwnsRequest.get(); }
                    });
            CompletableFuture<Void> ready=scheduler.registerTarget(new ChunkPos(0,0),0,0,true);
            check(acceptedAttempts.get()==1,"first accepted request is submitted once");
            Thread.sleep(350L);
            check(acceptedAttempts.get()==1,"accepted request is not duplicated while PNX queue owns it");
            pnxQueueOwnsRequest.set(false); // PNX released the slot after a failed generation attempt.
            check(retried.await(2,TimeUnit.SECONDS),"accepted-but-failed request is retried after PNX releases its queue slot");
            var recoveredSession=scheduler.openAnchor(new ChunkPos(0,0));
            recoveredSession.finishPopulation();
            check(ready.isDone(),"retried anchor completion releases target barrier");
            scheduler.close();
        }

        Path abortWal=Files.createTempDirectory("pnx-stage17-abort-").resolve("world.wal");
        try(PlacementJournal journal=new PlacementJournal(abortWal)){
            CrossChunkDependencyScheduler scheduler=new CrossChunkDependencyScheduler("world",journal);
            var ready=scheduler.registerTarget(new ChunkPos(0,0),0,0);
            var session=scheduler.openAnchor(new ChunkPos(0,0));
            var ticket=session.beginPlacement();ticket.abort();session.finishPopulation();
            check(ready.isDone(),"empty but successfully populated anchor can complete");
            check(journal.placementsFor(new ChunkPos(0,0)).isEmpty(),"aborted root writes absent from WAL");
        }
        System.out.println("Stage17DependencySchedulerTest: PASS");
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
