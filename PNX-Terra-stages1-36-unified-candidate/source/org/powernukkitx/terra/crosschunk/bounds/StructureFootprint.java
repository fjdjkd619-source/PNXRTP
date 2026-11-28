package org.powernukkitx.terra.crosschunk.bounds;

import java.util.Objects;

/** Inclusive horizontal write bounds relative to a structure's anchor. */
public record StructureFootprint(int minX, int maxX, int minZ, int maxZ) {
    public StructureFootprint {
        if (minX > maxX || minZ > maxZ) throw new IllegalArgumentException("Inverted structure footprint");
    }

    public StructureFootprint rotate(QuarterTurn turn) {
        Objects.requireNonNull(turn, "turn");
        int[][] corners = {{minX,minZ},{minX,maxZ},{maxX,minZ},{maxX,maxZ}};
        int loX = Integer.MAX_VALUE, hiX = Integer.MIN_VALUE;
        int loZ = Integer.MAX_VALUE, hiZ = Integer.MIN_VALUE;
        for (int[] p : corners) {
            int x, z;
            switch (turn) {
                case NONE -> { x = p[0]; z = p[1]; }
                case CLOCKWISE_90 -> { x = -p[1]; z = p[0]; }
                case HALF -> { x = -p[0]; z = -p[1]; }
                case COUNTERCLOCKWISE_90 -> { x = p[1]; z = -p[0]; }
                default -> throw new AssertionError(turn);
            }
            loX = Math.min(loX, x); hiX = Math.max(hiX, x);
            loZ = Math.min(loZ, z); hiZ = Math.max(hiZ, z);
        }
        return new StructureFootprint(loX, hiX, loZ, hiZ);
    }

    public enum QuarterTurn { NONE, CLOCKWISE_90, HALF, COUNTERCLOCKWISE_90 }
}
