package org.powernukkitx.terra.crosschunk.journal;

import java.util.*;

/** Immutable root placement transaction. Duplicate coordinates within this placement are last-write-wins. */
public record PlacementRecord(PlacementKey key, List<BlockWrite> writes,
                              List<BlockEntityIntent> blockEntities, List<PhysicsIntent> physics,
                              List<EntitySpawnIntent> entitySpawns) {
    public PlacementRecord(PlacementKey key, List<BlockWrite> writes) { this(key, writes, List.of(), List.of(), List.of()); }
    public PlacementRecord(PlacementKey key, List<BlockWrite> writes, List<BlockEntityIntent> blockEntities, List<PhysicsIntent> physics) {
        this(key, writes, blockEntities, physics, List.of());
    }
    public PlacementRecord {
        Objects.requireNonNull(key, "key"); Objects.requireNonNull(writes, "writes");
        Objects.requireNonNull(blockEntities, "blockEntities"); Objects.requireNonNull(physics, "physics"); Objects.requireNonNull(entitySpawns, "entitySpawns");
        if (writes.isEmpty() && blockEntities.isEmpty() && physics.isEmpty() && entitySpawns.isEmpty()) throw new IllegalArgumentException("A committed placement must contain at least one intent");
        if (writes.size() > 2_000_000 || blockEntities.size() > 65_536 || physics.size() > 2_000_000 || entitySpawns.size() > 65_536)
            throw new IllegalArgumentException("Placement exceeds intent limit");
        writes=List.copyOf(writes); blockEntities=List.copyOf(blockEntities); physics=List.copyOf(physics); entitySpawns=List.copyOf(entitySpawns);
        Set<java.util.UUID> spawnIds = new HashSet<>();
        for (EntitySpawnIntent spawn : entitySpawns) {
            if (!spawnIds.add(spawn.stableUuid())) throw new IllegalArgumentException("Duplicate entity UUID inside one placement: " + spawn.stableUuid());
        }
    }
    public Set<ChunkPos> targetChunks() {
        TreeSet<ChunkPos> result = new TreeSet<>();
        writes.forEach(w -> result.add(w.chunk())); blockEntities.forEach(e -> result.add(e.chunk())); physics.forEach(p -> result.add(p.chunk())); entitySpawns.forEach(e -> result.add(e.chunk()));
        return Collections.unmodifiableSet(result);
    }
}
