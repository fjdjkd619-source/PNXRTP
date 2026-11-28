package org.powernukkitx.terra.crosschunk;

import org.powernukkitx.terra.crosschunk.journal.*;
import java.nio.file.*;
import java.util.*;

public final class Stage22IntentJournalTest {
    public static void main(String[] args) throws Exception {
        Path wal=Files.createTempDirectory("pnx-stage22-").resolve("intents.wal");
        ChunkPos anchor=new ChunkPos(-1,2);
        PlacementKey key=new PlacementKey("world",7,anchor,"feature","chest",-1,64,32,0,9);
        PlacementRecord record=new PlacementRecord(key,List.of(),
                List.of(new BlockEntityIntent(-1,64,32,"Chest",new byte[]{1,2,3})),
                List.of(new PhysicsIntent(-1,64,32,true),new PhysicsIntent(-1,64,32,false)));
        String digest;
        try(PlacementJournal journal=new PlacementJournal(wal)){
            journal.appendPlacement(record);
            List<PlacementRecord> target=journal.placementsFor(new ChunkPos(-1,2));
            check(target.size()==1,"auxiliary-only placement indexed by target chunk");
            check(target.get(0).blockEntities().get(0).equals(record.blockEntities().get(0)),"entity payload preserved");
            check(PlacementProjection.resolveBlockEntities(target,new ChunkPos(-1,2)).size()==1,"entity winner projected");
            check(PlacementProjection.resolvePhysics(target,new ChunkPos(-1,2)).get(0).applyPhysics(),"physics intents merged by OR");
            digest=PlacementDigest.compute(target,new ChunkPos(-1,2));
        }
        try(PlacementJournal recovered=new PlacementJournal(wal)){
            PlacementRecord restored=recovered.placementsFor(new ChunkPos(-1,2)).get(0);
            check(restored.physics().size()==2,"physics intents survive restart");
            check(PlacementDigest.compute(List.of(restored),new ChunkPos(-1,2)).equals(digest),"auxiliary intents included in digest");
        }
        Path legacy=wal.getParent().resolve("legacy-v2.wal");
        writeLegacyV2Frame(legacy);
        try(PlacementJournal recovered=new PlacementJournal(legacy)) {
            check(recovered.events().size()==1,"version 2 placement remains readable after v3 upgrade");
            check(recovered.placementsFor(new ChunkPos(0,0)).get(0).writes().size()==1,"legacy block write preserved");
        }
        System.out.println("Stage22IntentJournalTest: PASS");
    }
    private static void writeLegacyV2Frame(Path path) throws Exception {
        java.io.ByteArrayOutputStream body=new java.io.ByteArrayOutputStream();
        try(java.io.DataOutputStream out=new java.io.DataOutputStream(body)) {
            out.writeByte(2); out.writeByte(1); out.writeLong(1);
            writeString(out,"world"); out.writeLong(11); out.writeInt(0); out.writeInt(0);
            writeString(out,"legacy"); writeString(out,"structure");
            out.writeInt(0); out.writeInt(64); out.writeInt(0); out.writeInt(0); out.writeLong(0);
            out.writeInt(1); out.writeInt(0); out.writeInt(64); out.writeInt(0); out.writeByte(0);
            out.writeInt(1); out.writeByte(5);
        }
        byte[] payload=body.toByteArray(); java.util.zip.CRC32C crc=new java.util.zip.CRC32C(); crc.update(payload,0,payload.length);
        try(java.io.DataOutputStream out=new java.io.DataOutputStream(Files.newOutputStream(path))) {
            out.writeInt(0x50584A31); out.writeInt(payload.length); out.write(payload); out.writeInt((int)crc.getValue());
        }
    }
    private static void writeString(java.io.DataOutputStream out,String value)throws Exception {
        byte[] bytes=value.getBytes(java.nio.charset.StandardCharsets.UTF_8); out.writeInt(bytes.length); out.write(bytes);
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
