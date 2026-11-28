package org.powernukkitx.terra.crosschunk.bounds;

import java.util.List;
import java.util.Objects;

/** Audited conservative runtime envelope for one Origen TerraScript root. */
public record ScriptBoundsProfile(
        String packId,
        String structureId,
        Bounds3i writeBounds,
        Bounds3i readBounds,
        int maxBlockWrites,
        int maxBlockReads,
        int maxEntitySpawns,
        List<String> transitiveDependencies,
        String evidence) {
    public ScriptBoundsProfile {
        if (packId == null || packId.isBlank() || structureId == null || structureId.isBlank())
            throw new IllegalArgumentException("Pack and structure IDs are required");
        Objects.requireNonNull(writeBounds, "writeBounds");
        Objects.requireNonNull(readBounds, "readBounds");
        if (maxBlockWrites < 0 || maxBlockReads < 0 || maxEntitySpawns < 0)
            throw new IllegalArgumentException("Budgets cannot be negative");
        transitiveDependencies = List.copyOf(transitiveDependencies);
        if (evidence == null || evidence.isBlank()) throw new IllegalArgumentException("Evidence is required");
    }

    /** Wildcard is reserved for the runtime-bounded fallback, whose root guard covers all nesting. */
    public boolean allowsNestedStructure(String structureId) {
        return transitiveDependencies.contains("*") || transitiveDependencies.contains(structureId);
    }
}
