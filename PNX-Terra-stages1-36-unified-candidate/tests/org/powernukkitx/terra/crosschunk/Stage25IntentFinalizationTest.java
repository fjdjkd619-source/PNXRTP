package org.powernukkitx.terra.crosschunk;

import org.powernukkitx.terra.crosschunk.finalize.ChunkFinalizationCoordinator;
import org.powernukkitx.terra.crosschunk.journal.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class Stage25IntentFinalizationTest {
    public static void main(String[] args) throws Exception {
        Path wal=Files.createTempDirectory("pnx-stage25-").resolve("finalize.wal");
        ChunkPos target=new ChunkPos(0,0);
        PlacementKey key=new PlacementKey("world",1,target,"feature","structure",0,64,0,0,1);
        PlacementRecord record=new PlacementRecord(key,List.of(new BlockWrite(0,64,0,0,new byte[]{1})),
                List.of(new BlockEntityIntent(1,64,1,"Chest",new byte[]{2})),
                List.of(new PhysicsIntent(0,64,0,true)));
        try(PlacementJournal journal=new PlacementJournal(wal)){
            journal.appendPlacement(record);
            ChunkFinalizationCoordinator<FakeChunk> coordinator=new ChunkFinalizationCoordinator<>("world",journal,Runnable::run);
            FakeChunk unsupported=new FakeChunk(false);
            boolean failed=false;
            try { coordinator.prepare(target,CompletableFuture.completedFuture(null),()->unsupported).join(); }
            catch(CompletionException expected){failed=true;}
            check(failed,"adapter without entity support fails closed");
            check(!journal.isChunkApplied("world",target,PlacementDigest.compute(journal.placementsFor(target),target)),"unsupported intents never acknowledged");
            FakeChunk supported=new FakeChunk(true);
            List<Runnable> levelTasks=new ArrayList<>();
            ChunkFinalizationCoordinator<FakeChunk> splitCoordinator =
                    new ChunkFinalizationCoordinator<>("world",journal,Runnable::run,levelTasks::add);
            CompletableFuture<ChunkFinalizationCoordinator.Prepared<FakeChunk>> prepareFuture =
                    splitCoordinator.prepare(target,CompletableFuture.completedFuture(null),()->supported);
            check(supported.blocks==1&&supported.entities==0&&supported.physics==0,
                    "block projection runs before PNX-owned intent executor");
            check(!prepareFuture.isDone()&&levelTasks.size()==1,
                    "finalization waits asynchronously for PNX-owned intent execution");
            levelTasks.remove(0).run();
            var prepared=prepareFuture.join();
            check(supported.blocks==1&&supported.entities==1&&supported.physics==1,"all intent classes reach adapter before save");
            check(supported.saved&&supported.confirmed==1,
                    "BlockEntity batch is durably saved and rollback window closed on the Level scheduler before its heartbeat returns");
            splitCoordinator.persist(prepared,()->supported);
            check(supported.saved,"chunk persisted after complete intent application");
            check(journal.isChunkApplied("world",target,prepared.digest()),"acknowledgement follows durable save");
            check(supported.saved, "save completed before acknowledgement is visible");
            ChunkPos failureTarget = new ChunkPos(1,0);
            PlacementKey failureKey = new PlacementKey("world",1,failureTarget,"feature-failure","structure",16,64,0,0,1);
            journal.appendPlacement(new PlacementRecord(failureKey,List.of(new BlockWrite(16,64,0,0,new byte[]{3}))));
            ChunkFinalizationCoordinator<FakeChunk> failureCoordinator = new ChunkFinalizationCoordinator<>("world",journal,Runnable::run);
            FakeChunk saveFailure = new FakeChunk(true,1,0);
            saveFailure.failSave = true;
            var failedPrepared = failureCoordinator.prepare(failureTarget,CompletableFuture.completedFuture(null),()->saveFailure).join();
            boolean saveRejected=false;
            try { failureCoordinator.persist(failedPrepared,()->saveFailure); } catch (java.io.IOException expected) { saveRejected=true; }
            check(saveRejected, "durable save failure propagates");
            check(!journal.isChunkApplied("world",failureTarget,failedPrepared.digest()), "failed durable save cannot publish digest acknowledgement");
            ChunkPos beFailureTarget = new ChunkPos(2,0);
            PlacementKey beFailureKey = new PlacementKey("world",1,beFailureTarget,"feature-be-failure","structure",32,64,0,0,1);
            journal.appendPlacement(new PlacementRecord(beFailureKey,List.of(new BlockWrite(32,64,0,0,new byte[]{4})),
                    List.of(new BlockEntityIntent(33,64,1,"Chest",new byte[]{5})),List.of()));
            FakeChunk beSaveFailure = new FakeChunk(true,2,0);
            beSaveFailure.failSave = true;
            boolean bePrepareRejected=false;
            try { new ChunkFinalizationCoordinator<FakeChunk>("world",journal,Runnable::run)
                    .prepare(beFailureTarget,CompletableFuture.completedFuture(null),()->beSaveFailure).join(); }
            catch (CompletionException expected) { bePrepareRejected=true; }
            check(bePrepareRejected&&beSaveFailure.rollbackCount==1&&beSaveFailure.entities==0,
                    "failed early durable save rolls back newly registered BlockEntities before scheduler task completes");
        }
        System.out.println("Stage25IntentFinalizationTest: PASS");
    }
    private static final class FakeChunk implements ChunkFinalizationCoordinator.ChunkHandle {
        private final boolean supports; private final int chunkX,chunkZ; int blocks,entities,physics; int rollbackCount,confirmed; boolean saved; boolean failSave;
        FakeChunk(boolean supports){this(supports,0,0);}
        FakeChunk(boolean supports,int chunkX,int chunkZ){this.supports=supports;this.chunkX=chunkX;this.chunkZ=chunkZ;}
        public ChunkPos position(){return new ChunkPos(chunkX,chunkZ);} public boolean isLoaded(){return true;}
        public boolean isGenerated(){return true;} public boolean isInitiated(){return true;}
        public void applyProjection(NavigableMap<PlacementProjection.Position,PlacementProjection.Winner> p){blocks=p.size();}
        public void applyBlockEntityIntents(NavigableMap<PlacementProjection.Position,PlacementProjection.EntityWinner> p){if(!supports&&!p.isEmpty())throw new UnsupportedOperationException();entities=p.size();}
        public void applyPhysicsIntents(List<PhysicsIntent> p){if(!supports&&!p.isEmpty())throw new UnsupportedOperationException();physics=p.size();}
        public void rollbackBlockEntityIntents(){rollbackCount++;entities=0;}
        public void confirmBlockEntityIntents(){confirmed++;}
        public void saveDurably() throws java.io.IOException {if(failSave)throw new java.io.IOException("simulated durable write failure");saved=true;}
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
