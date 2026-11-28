package org.powernukkitx.terra.crosschunk.journal;

/** Immutable chunk coordinate with correct negative-coordinate ordering. */
public record ChunkPos(int x, int z) implements Comparable<ChunkPos> {
    public static ChunkPos fromBlock(int blockX, int blockZ) {
        return new ChunkPos(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
    }

    @Override public int compareTo(ChunkPos other) {
        int xOrder = Integer.compare(x, other.x);
        return xOrder != 0 ? xOrder : Integer.compare(z, other.z);
    }
}
