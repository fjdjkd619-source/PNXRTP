package org.powernukkitx.terra.crosschunk.journal;

import java.util.Arrays;
import java.util.Objects;

/** Durable, platform-neutral block-entity creation/update intent. NBT payload is canonical adapter data. */
public final class BlockEntityIntent {
    private final int x, y, z;
    private final String type;
    private final byte[] payload;

    public BlockEntityIntent(int x, int y, int z, String type, byte[] payload) {
        if (type == null || type.isBlank() || type.length() > 256) throw new IllegalArgumentException("Invalid block entity type");
        this.x=x; this.y=y; this.z=z; this.type=type;
        this.payload=Objects.requireNonNull(payload,"payload").clone();
        if (this.payload.length == 0 || this.payload.length > 1_048_576) throw new IllegalArgumentException("Block entity payload length out of range");
    }
    public int x(){return x;} public int y(){return y;} public int z(){return z;} public String type(){return type;}
    public byte[] payload(){return payload.clone();}
    public ChunkPos chunk(){return ChunkPos.fromBlock(x,z);}
    @Override public boolean equals(Object o){return o instanceof BlockEntityIntent b&&x==b.x&&y==b.y&&z==b.z&&type.equals(b.type)&&Arrays.equals(payload,b.payload);}
    @Override public int hashCode(){return 31*Objects.hash(x,y,z,type)+Arrays.hashCode(payload);}
}
