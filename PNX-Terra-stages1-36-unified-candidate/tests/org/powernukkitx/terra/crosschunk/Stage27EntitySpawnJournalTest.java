package org.powernukkitx.terra.crosschunk;

import org.powernukkitx.terra.crosschunk.journal.*;
import java.nio.file.Files;
import java.util.List;

public final class Stage27EntitySpawnJournalTest {
    public static void main(String[] args) throws Exception {
        var dir=Files.createTempDirectory("pnx-stage27-"); var wal=dir.resolve("world.wal");
        var key=new PlacementKey("world",7,new ChunkPos(-1,2),"feature","origen:bee_nest",-1,64,32,0,0);
        var spawn=new EntitySpawnIntent(EntitySpawnIntent.deterministicUuid(key.stableId(),0),"minecraft:bee",-0.5,65,32.25,90,0,new byte[]{1,2,3});
        var record=new PlacementRecord(key,List.of(),List.of(),List.of(),List.of(spawn));
        try(var journal=new PlacementJournal(wal)) {
            journal.appendPlacement(record);
            if(!journal.placementsFor(spawn.chunk()).get(0).entitySpawns().get(0).equals(spawn)) throw new AssertionError("spawn intent not indexed");
            if(!PlacementProjection.resolveEntitySpawns(journal.placementsFor(spawn.chunk()),spawn.chunk()).equals(List.of(spawn))) throw new AssertionError("spawn projection differs");
            journal.appendPlacement(record); // same-key retry must be idempotent
        }
        try(var recovered=new PlacementJournal(wal)) {
            var actual=recovered.placementsFor(spawn.chunk()).get(0).entitySpawns().get(0);
            if(!actual.equals(spawn)) throw new AssertionError("WAL v4 replay changed spawn intent");
            if(!PlacementDigest.compute(recovered.placementsFor(spawn.chunk()),spawn.chunk()).matches("[0-9a-f]{64}")) throw new AssertionError("invalid digest");
        }
        // Version-3 WAL frames remain readable after the v4 extension.
        var legacyWal=dir.resolve("legacy-v3.wal");
        writeLegacyV3Frame(legacyWal);
        try(var legacy=new PlacementJournal(legacyWal)) {
            var legacyRecord=legacy.placementsFor(new ChunkPos(0,0)).get(0);
            if(legacyRecord.writes().size()!=1 || !legacyRecord.entitySpawns().isEmpty()) throw new AssertionError("v3 WAL compatibility broken");
        }
        var collision=new EntitySpawnIntent(spawn.stableUuid(),"minecraft:bee",16.5,65,32.25,90,0,new byte[]{9});
        try(var journal=new PlacementJournal(dir.resolve("collision.wal"))) {
            journal.appendPlacement(record);
            try { journal.appendPlacement(new PlacementRecord(
                    new PlacementKey("world",7,new ChunkPos(0,2),"feature2","origen:bee_nest",16,64,32,0,0),List.of(),List.of(),List.of(),List.of(collision)));
                throw new AssertionError("global UUID collision accepted");
            } catch(java.io.IOException expected) { }
        }
        System.out.println("Stage27EntitySpawnJournalTest: PASS");
    }
    private static void writeLegacyV3Frame(java.nio.file.Path path) throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();
        try(var out=new java.io.DataOutputStream(bytes)) {
            out.writeByte(3); out.writeByte(1); out.writeLong(1);
            writeString(out,"legacy-world"); out.writeLong(1); out.writeInt(0); out.writeInt(0);
            writeString(out,"legacy-feature"); writeString(out,"legacy:structure");
            out.writeInt(1); out.writeInt(64); out.writeInt(1); out.writeInt(0); out.writeLong(0);
            out.writeInt(1); out.writeInt(1); out.writeInt(64); out.writeInt(1); out.writeByte(0); out.writeInt(1); out.writeByte(7);
            out.writeInt(0); out.writeInt(0);
        }
        byte[] payload=bytes.toByteArray(); var crc=new java.util.zip.CRC32C(); crc.update(payload,0,payload.length);
        var frame=java.nio.ByteBuffer.allocate(8+payload.length+4);
        frame.putInt(0x50584A31).putInt(payload.length).put(payload).putInt((int)crc.getValue());
        java.nio.file.Files.write(path,frame.array());
    }
    private static void writeString(java.io.DataOutputStream out,String value) throws java.io.IOException {
        byte[] data=value.getBytes(java.nio.charset.StandardCharsets.UTF_8); out.writeInt(data.length); out.write(data);
    }
}
