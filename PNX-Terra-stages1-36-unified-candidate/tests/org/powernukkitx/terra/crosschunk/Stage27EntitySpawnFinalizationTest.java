package org.powernukkitx.terra.crosschunk;

import org.powernukkitx.terra.crosschunk.finalize.ChunkFinalizationCoordinator;
import org.powernukkitx.terra.crosschunk.journal.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

public final class Stage27EntitySpawnFinalizationTest {
    public static void main(String[] args) throws Exception {
        var target=new ChunkPos(0,0); var key=new PlacementKey("world",3,target,"f","origen:bee_nest",0,64,0,0,0);
        var spawn=new EntitySpawnIntent(EntitySpawnIntent.deterministicUuid(key.stableId(),0),"minecraft:bee",1.5,65,1.5,0,0,new byte[0]);
        try(var journal=new PlacementJournal(Files.createTempDirectory("stage27-finalize").resolve("world.wal"))) {
            journal.appendPlacement(new PlacementRecord(key,List.of(),List.of(),List.of(),List.of(spawn)));
            var fake=new FakeChunk(); var tasks=new ArrayList<Runnable>();
            var coordinator=new ChunkFinalizationCoordinator<FakeChunk>("world",journal,Runnable::run,tasks::add);
            var future=coordinator.prepare(target,CompletableFuture.completedFuture(null),()->fake);
            if(future.isDone() || tasks.size()!=1) throw new AssertionError("spawn work did not wait for Level executor");
            tasks.remove(0).run(); var prepared=future.join();
            if(fake.spawnCount!=1 || !fake.saved || fake.confirmCount!=1) throw new AssertionError("spawn was not saved and confirmed before executor returned");
            if(!fake.phases.equals(List.of("apply", "chunk-save-stage", "durable-save", "activate")))
                throw new AssertionError("entity activation crossed the durable-save boundary in the wrong order: " + fake.phases);
            coordinator.persist(prepared,()->fake);
            if(!journal.isChunkApplied("world",target,prepared.digest())) throw new AssertionError("spawn digest not acknowledged after save");
            var failTarget=new ChunkPos(1,0);
            var failKey=new PlacementKey("world",3,failTarget,"f","origen:bee_nest",16,64,0,0,1);
            var failSpawn=new EntitySpawnIntent(EntitySpawnIntent.deterministicUuid(failKey.stableId(),0),"minecraft:bee",17.5,65,1.5,0,0,new byte[0]);
            journal.appendPlacement(new PlacementRecord(failKey,List.of(),List.of(),List.of(),List.of(failSpawn)));
            var failedChunk=new FakeChunk(1,0); failedChunk.failSave=true;
            var failedCoordinator=new ChunkFinalizationCoordinator<FakeChunk>("world",journal,Runnable::run);
            try { failedCoordinator.prepare(failTarget,CompletableFuture.completedFuture(null),()->failedChunk).join(); throw new AssertionError("failed entity save accepted"); }
            catch(CompletionException expected) { }
            if(failedChunk.spawnCount!=0 || failedChunk.rollbackCount!=1) throw new AssertionError("failed durable save did not roll back entity registrations");
        }
        System.out.println("Stage27EntitySpawnFinalizationTest: PASS");
    }
    private static final class FakeChunk implements ChunkFinalizationCoordinator.ChunkHandle {
        int spawnCount,rollbackCount,confirmCount; boolean saved,failSave; final int chunkX,chunkZ; final List<String> phases=new ArrayList<>();
        FakeChunk(){this(0,0);} FakeChunk(int chunkX,int chunkZ){this.chunkX=chunkX;this.chunkZ=chunkZ;}
        public ChunkPos position(){return new ChunkPos(chunkX,chunkZ);} public boolean isLoaded(){return true;} public boolean isGenerated(){return true;} public boolean isInitiated(){return true;}
        public void applyProjection(NavigableMap<PlacementProjection.Position,PlacementProjection.Winner> p) { }
        public void applyEntitySpawnIntents(List<EntitySpawnIntent> intents) { spawnCount=intents.size(); phases.add("apply"); }
        public void prepareEntitySpawnIntentsForSave(){phases.add("chunk-save-stage");}
        public void rollbackEntitySpawnIntents(){rollbackCount++;spawnCount=0;}
        public void confirmEntitySpawnIntents(){confirmCount++;phases.add("activate");}
        public void saveDurably() throws java.io.IOException {phases.add("durable-save");if(failSave)throw new java.io.IOException("simulated save failure");saved=true;}
    }
}
