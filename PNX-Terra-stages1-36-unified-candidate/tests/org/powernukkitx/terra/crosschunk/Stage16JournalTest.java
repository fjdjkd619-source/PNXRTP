package org.powernukkitx.terra.crosschunk;

import org.powernukkitx.terra.crosschunk.journal.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class Stage16JournalTest {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("pnx-stage16-");Path wal=dir.resolve("placements.wal");
        PlacementRecord a=record("feature-a",0,0,"stone");
        PlacementRecord b=record("feature-b",0,0,"dirt");
        try(PlacementJournal journal=new PlacementJournal(wal)){
            boolean locked=false;
            try {
                PlacementJournal duplicate=new PlacementJournal(wal);
                duplicate.close();
            } catch(java.io.IOException expectedLock){locked=true;}
            check(locked,"exclusive process lock");
            long s1=journal.appendPlacement(a);long s2=journal.appendPlacement(b);
            check(s1==1&&s2==2,"monotonic sequence");
            check(journal.appendPlacement(a)==s1,"idempotent root commit");
            boolean collision=false;
            try{journal.appendPlacement(new PlacementRecord(a.key(),List.of(new BlockWrite(0,64,0,0,"sand".getBytes(StandardCharsets.UTF_8)))));}
            catch(java.io.IOException expectedCollision){collision=true;}
            check(collision,"stable-key payload collision rejected");
            var projection=PlacementProjection.resolve(journal.placementsFor(new ChunkPos(0,0)),new ChunkPos(0,0));
            String expected=List.of(a,b).stream().min(Comparator.comparing(p->p.key().stableId())).orElseThrow().key().stableId();
            check(projection.firstEntry().getValue().placementId().equals(expected),"stable conflict winner");
            journal.appendAnchorCompleted("world",new ChunkPos(0,0));
        }
        long goodSize=Files.size(wal);
        Files.write(wal,new byte[]{1,2,3,4,5},StandardOpenOption.APPEND);
        try(PlacementJournal recovered=new PlacementJournal(wal)){
            check(recovered.events().size()==3,"replay valid records");
            check(recovered.isAnchorCompleted("world",new ChunkPos(0,0)),"replay completion marker");
            check(Files.size(wal)==goodSize,"truncate torn tail");
        }
        Path corrupt=dir.resolve("corrupt.wal");
        try(PlacementJournal j=new PlacementJournal(corrupt)){j.appendPlacement(a);}
        byte[] bytes=Files.readAllBytes(corrupt);bytes[bytes.length-1]^=0x44;Files.write(corrupt,bytes);
        byte[] damaged=Files.readAllBytes(corrupt);
        boolean crcRejected=false;
        try(PlacementJournal ignored=new PlacementJournal(corrupt)){ }
        catch(java.io.IOException expected){crcRejected=true;}
        check(crcRejected,"complete final frame with bad CRC fails closed");
        check(Arrays.equals(damaged,Files.readAllBytes(corrupt)),"bad-CRC WAL remains byte-for-byte unchanged");
        check(ChunkPos.fromBlock(-1,-17).equals(new ChunkPos(-1,-2)),"negative block-to-chunk mapping");

        Path concurrentWal=dir.resolve("concurrent.wal");
        try(PlacementJournal concurrent=new PlacementJournal(concurrentWal)){
            ExecutorService pool=Executors.newFixedThreadPool(6);
            List<Future<Long>> futures=new ArrayList<>();
            for(int i=0;i<48;i++){final int id=i;futures.add(pool.submit(()->concurrent.appendPlacement(record("parallel-"+id,2,2,"state-"+id))));}
            for(Future<Long> future:futures)future.get();pool.shutdown();
            check(concurrent.events().size()==48,"concurrent durable appends serialized");
            var forward=PlacementProjection.resolve(concurrent.placementsFor(new ChunkPos(0,0)),new ChunkPos(0,0));
            List<PlacementRecord> reversed=new ArrayList<>(concurrent.placementsFor(new ChunkPos(0,0)));Collections.reverse(reversed);
            var backward=PlacementProjection.resolve(reversed,new ChunkPos(0,0));
            check(forward.firstEntry().getValue().placementId().equals(backward.firstEntry().getValue().placementId()),"conflict winner independent of completion order");
        }
        System.out.println("Stage16JournalTest: PASS");
    }
    static PlacementRecord record(String feature,int x,int z,String state){
        ChunkPos anchor=ChunkPos.fromBlock(x,z);
        PlacementKey key=new PlacementKey("world",1234L,anchor,feature,"test_structure",x,64,z,0,0);
        return new PlacementRecord(key,List.of(new BlockWrite(x,64,z,0,state.getBytes(StandardCharsets.UTF_8))));
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
