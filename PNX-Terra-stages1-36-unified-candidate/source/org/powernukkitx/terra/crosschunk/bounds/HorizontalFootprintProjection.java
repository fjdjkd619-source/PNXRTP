package org.powernukkitx.terra.crosschunk.bounds;

/** Bridges a verified 3D profile to Stage 12's horizontal anchor scheduler footprint. */
public final class HorizontalFootprintProjection {
    private HorizontalFootprintProjection() {}

    public static StructureFootprint project(ScriptBoundsProfile profile, Bounds3i.QuarterTurn turn) {
        return projectBounds(profile.writeBounds(), turn);
    }

    /** Read halo is separate from the write footprint and must be preflighted as well. */
    public static StructureFootprint projectReads(ScriptBoundsProfile profile, Bounds3i.QuarterTurn turn) {
        return projectBounds(profile.readBounds(), turn);
    }

    private static StructureFootprint projectBounds(Bounds3i bounds, Bounds3i.QuarterTurn turn) {
        Bounds3i rotated = bounds.rotate(turn);
        return new StructureFootprint(rotated.minX(), rotated.maxX(), rotated.minZ(), rotated.maxZ());
    }
}
