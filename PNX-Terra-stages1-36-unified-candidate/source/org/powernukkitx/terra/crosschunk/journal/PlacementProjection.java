package org.powernukkitx.terra.crosschunk.journal;

import java.util.*;

/** Deterministic target-chunk projection. Lowest stable placement key wins coordinate/layer conflicts. */
public final class PlacementProjection {
    private PlacementProjection() {}
    public record Position(int x, int y, int z, int layer) implements Comparable<Position> {
        @Override public int compareTo(Position o) {
            int c=Integer.compare(x,o.x); if(c==0)c=Integer.compare(y,o.y); if(c==0)c=Integer.compare(z,o.z);
            return c==0?Integer.compare(layer,o.layer):c;
        }
    }
    public record Winner(String placementId, BlockWrite write) {}
    public record EntityWinner(String placementId, BlockEntityIntent intent) {}

    public static NavigableMap<Position, Winner> resolve(Collection<PlacementRecord> records, ChunkPos target) {
        TreeMap<String, PlacementRecord> stableOrder = new TreeMap<>();
        for (PlacementRecord record : records) stableOrder.put(record.key().stableId(), record);
        TreeMap<Position, Winner> winners = new TreeMap<>();
        for (var entry : stableOrder.entrySet()) {
            // Preserve script order inside one placement; only its final write to a cell survives.
            LinkedHashMap<Position, BlockWrite> local = new LinkedHashMap<>();
            for (BlockWrite write : entry.getValue().writes()) {
                if (write.chunk().equals(target)) local.put(new Position(write.x(),write.y(),write.z(),write.layer()),write);
            }
            for (var cell : local.entrySet()) winners.putIfAbsent(cell.getKey(), new Winner(entry.getKey(),cell.getValue()));
        }
        return Collections.unmodifiableNavigableMap(winners);
    }

    /** Stable entity intent resolution: lowest placement id wins at a coordinate. */
    public static NavigableMap<Position, EntityWinner> resolveBlockEntities(Collection<PlacementRecord> records, ChunkPos target) {
        TreeMap<String, PlacementRecord> stableOrder = new TreeMap<>();
        for (PlacementRecord record : records) stableOrder.put(record.key().stableId(), record);
        TreeMap<Position, EntityWinner> winners = new TreeMap<>();
        for (var entry : stableOrder.entrySet()) {
            for (BlockEntityIntent intent : entry.getValue().blockEntities()) {
                if (intent.chunk().equals(target)) {
                    Position position = new Position(intent.x(), intent.y(), intent.z(), 0);
                    winners.putIfAbsent(position, new EntityWinner(entry.getKey(), intent));
                }
            }
        }
        return Collections.unmodifiableNavigableMap(winners);
    }

    /** Stable entity spawn order and collision check; UUID is the durable idempotency key. */
    public static List<EntitySpawnIntent> resolveEntitySpawns(Collection<PlacementRecord> records, ChunkPos target) {
        TreeMap<String, EntitySpawnIntent> ordered = new TreeMap<>();
        for (PlacementRecord record : records.stream().sorted(Comparator.comparing(r -> r.key().stableId())).toList()) {
            for (EntitySpawnIntent intent : record.entitySpawns()) {
                if (!intent.chunk().equals(target)) continue;
                String id = intent.stableUuid().toString();
                EntitySpawnIntent previous = ordered.putIfAbsent(id, intent);
                if (previous != null && !previous.equals(intent))
                    throw new IllegalStateException("Entity UUID collision with different spawn payload: " + id);
            }
        }
        return List.copyOf(ordered.values());
    }

    /** Preserve all physics intents in stable order; duplicate coordinate requests collapse to OR. */
    public static List<PhysicsIntent> resolvePhysics(Collection<PlacementRecord> records, ChunkPos target) {
        TreeMap<String, PlacementRecord> stableOrder = new TreeMap<>();
        for (PlacementRecord record : records) stableOrder.put(record.key().stableId(), record);
        TreeMap<Position, Boolean> merged = new TreeMap<>();
        for (PlacementRecord record : stableOrder.values()) for (PhysicsIntent intent : record.physics()) {
            if (intent.chunk().equals(target)) {
                Position p = new Position(intent.x(), intent.y(), intent.z(), 0);
                merged.merge(p, intent.applyPhysics(), Boolean::logicalOr);
            }
        }
        return merged.entrySet().stream().map(e -> new PhysicsIntent(e.getKey().x(),e.getKey().y(),e.getKey().z(),e.getValue())).toList();
    }
}
