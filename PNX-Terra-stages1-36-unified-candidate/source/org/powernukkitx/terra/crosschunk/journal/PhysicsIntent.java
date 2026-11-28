package org.powernukkitx.terra.crosschunk.journal;

/** Deferred physics request emitted by Terra's WritableWorld#setBlockState(..., physics). */
public record PhysicsIntent(int x, int y, int z, boolean applyPhysics) {
    public ChunkPos chunk(){return ChunkPos.fromBlock(x,z);}
}
