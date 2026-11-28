package org.powernukkitx.terra.crosschunk.journal;

import java.util.Arrays;
import java.util.Objects;

/** One canonical block-state write. Payload is owned by a platform codec, never a runtime ID. */
public final class BlockWrite {
    private final int x, y, z, layer;
    private final byte[] canonicalState;

    public BlockWrite(int x, int y, int z, int layer, byte[] canonicalState) {
        if (layer < 0 || layer > 1) throw new IllegalArgumentException("PNX block layer must be 0 or 1");
        this.x = x; this.y = y; this.z = z; this.layer = layer;
        this.canonicalState = Objects.requireNonNull(canonicalState, "canonicalState").clone();
        if (this.canonicalState.length == 0 || this.canonicalState.length > 65_536)
            throw new IllegalArgumentException("canonicalState payload length out of range");
    }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public int layer() { return layer; }
    public byte[] canonicalState() { return canonicalState.clone(); }
    public ChunkPos chunk() { return ChunkPos.fromBlock(x, z); }
    @Override public boolean equals(Object o) {
        return o instanceof BlockWrite b && x == b.x && y == b.y && z == b.z && layer == b.layer
                && Arrays.equals(canonicalState, b.canonicalState);
    }
    @Override public int hashCode() { return 31 * Objects.hash(x,y,z,layer) + Arrays.hashCode(canonicalState); }
}
