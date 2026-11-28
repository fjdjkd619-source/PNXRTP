package org.powernukkitx.terra.crosschunk.bounds;

import java.util.Objects;

/** Inclusive integer AABB in structure-local coordinates. */
public record Bounds3i(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public Bounds3i {
        if (minX > maxX || minY > maxY || minZ > maxZ)
            throw new IllegalArgumentException("Inverted 3D bounds");
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public Bounds3i translate(int x, int y, int z) {
        return new Bounds3i(Math.addExact(minX, x), Math.addExact(minY, y), Math.addExact(minZ, z),
                Math.addExact(maxX, x), Math.addExact(maxY, y), Math.addExact(maxZ, z));
    }

    public Bounds3i rotate(QuarterTurn turn) {
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
        return new Bounds3i(loX, minY, loZ, hiX, maxY, hiZ);
    }

    public enum QuarterTurn { NONE, CLOCKWISE_90, HALF, COUNTERCLOCKWISE_90 }
}
