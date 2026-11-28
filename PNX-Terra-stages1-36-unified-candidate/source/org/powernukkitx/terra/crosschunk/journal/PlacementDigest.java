package org.powernukkitx.terra.crosschunk.journal;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Stable digest of every durable placement affecting one target, independent of append/completion order. */
public final class PlacementDigest {
    private PlacementDigest() {}

    public static String compute(Collection<PlacementRecord> records, ChunkPos target) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(3);
                out.writeInt(target.x());
                out.writeInt(target.z());
                List<PlacementRecord> ordered = records.stream()
                        .filter(record -> record.targetChunks().contains(target))
                        .sorted(Comparator.comparing(record -> record.key().stableId())).toList();
                out.writeInt(ordered.size());
                for (PlacementRecord record : ordered) {
                    out.writeUTF(record.key().stableId());
                    List<BlockWrite> writes = record.writes().stream()
                            .filter(write -> write.chunk().equals(target)).toList();
                    out.writeInt(writes.size());
                    for (BlockWrite write : writes) {
                        out.writeInt(write.x()); out.writeInt(write.y()); out.writeInt(write.z());
                        out.writeByte(write.layer());
                        byte[] payload = write.canonicalState();
                        out.writeInt(payload.length); out.write(payload);
                    }
                    List<BlockEntityIntent> entities = record.blockEntities().stream().filter(e -> e.chunk().equals(target))
                            .sorted(Comparator.comparingInt(BlockEntityIntent::x).thenComparingInt(BlockEntityIntent::y).thenComparingInt(BlockEntityIntent::z)).toList();
                    out.writeInt(entities.size());
                    for (BlockEntityIntent entity : entities) {
                        out.writeInt(entity.x()); out.writeInt(entity.y()); out.writeInt(entity.z()); out.writeUTF(entity.type());
                        byte[] payload=entity.payload(); out.writeInt(payload.length); out.write(payload);
                    }
                    List<PhysicsIntent> physics = record.physics().stream().filter(p -> p.chunk().equals(target))
                            .sorted(Comparator.comparingInt(PhysicsIntent::x).thenComparingInt(PhysicsIntent::y).thenComparingInt(PhysicsIntent::z)).toList();
                    out.writeInt(physics.size());
                    for (PhysicsIntent intent : physics) { out.writeInt(intent.x()); out.writeInt(intent.y()); out.writeInt(intent.z()); out.writeBoolean(intent.applyPhysics()); }
                    List<EntitySpawnIntent> spawns = record.entitySpawns().stream().filter(e -> e.chunk().equals(target))
                            .sorted(Comparator.comparing(e -> e.stableUuid().toString())).toList();
                    out.writeInt(spawns.size());
                    for (EntitySpawnIntent spawn : spawns) {
                        out.writeLong(spawn.stableUuid().getMostSignificantBits()); out.writeLong(spawn.stableUuid().getLeastSignificantBits());
                        out.writeUTF(spawn.type()); out.writeDouble(spawn.x()); out.writeDouble(spawn.y()); out.writeDouble(spawn.z());
                        out.writeFloat(spawn.yaw()); out.writeFloat(spawn.pitch()); byte[] payload=spawn.payload(); out.writeInt(payload.length); out.write(payload);
                    }
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException impossible) {
            throw new IllegalStateException("In-memory digest encoding failed", impossible);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
