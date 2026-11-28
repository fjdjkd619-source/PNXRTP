package org.powernukkitx.terra.crosschunk.journal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Durable, platform-neutral entity spawn request. Runtime entity IDs are never persisted. */
public final class EntitySpawnIntent {
    private final UUID stableUuid;
    private final String type;
    private final double x, y, z;
    private final float yaw, pitch;
    private final byte[] payload;

    public EntitySpawnIntent(UUID stableUuid, String type, double x, double y, double z,
                             float yaw, float pitch, byte[] payload) {
        this.stableUuid = Objects.requireNonNull(stableUuid, "stableUuid");
        if (type == null || !type.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || type.length() > 256)
            throw new IllegalArgumentException("Entity type must be a canonical namespaced identifier");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Entity transform must be finite and horizontally representable by block coordinates");
        this.type = type; this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
        this.payload = Objects.requireNonNull(payload, "payload").clone();
        if (this.payload.length > 1_048_576) throw new IllegalArgumentException("Entity payload exceeds 1 MiB");
    }

    /** Stable identity derived from the durable placement key and the feature-local spawn ordinal. */
    public static UUID deterministicUuid(String placementStableId, int spawnOrdinal) {
        if (placementStableId == null || !placementStableId.matches("[0-9a-f]{64}") || spawnOrdinal < 0)
            throw new IllegalArgumentException("Invalid placement identity or spawn ordinal");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((placementStableId + ":entity:" + spawnOrdinal).getBytes(StandardCharsets.UTF_8));
            digest[6] = (byte)((digest[6] & 0x0f) | 0x50);
            digest[8] = (byte)((digest[8] & 0x3f) | 0x80);
            java.nio.ByteBuffer bytes = java.nio.ByteBuffer.wrap(digest);
            return new UUID(bytes.getLong(), bytes.getLong());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public UUID stableUuid() { return stableUuid; }
    public String type() { return type; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public byte[] payload() { return payload.clone(); }
    public ChunkPos chunk() { return ChunkPos.fromBlock((int)Math.floor(x), (int)Math.floor(z)); }

    @Override public boolean equals(Object other) {
        return other instanceof EntitySpawnIntent e && stableUuid.equals(e.stableUuid) && type.equals(e.type)
                && Double.compare(x,e.x)==0 && Double.compare(y,e.y)==0 && Double.compare(z,e.z)==0
                && Float.compare(yaw,e.yaw)==0 && Float.compare(pitch,e.pitch)==0 && Arrays.equals(payload,e.payload);
    }
    @Override public int hashCode() {
        return 31 * Objects.hash(stableUuid,type,x,y,z,yaw,pitch) + Arrays.hashCode(payload);
    }
}
