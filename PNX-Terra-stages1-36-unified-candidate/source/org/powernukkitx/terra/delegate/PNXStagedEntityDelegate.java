package org.powernukkitx.terra.delegate;

import com.dfsek.terra.api.entity.Entity;
import com.dfsek.terra.api.util.vector.Vector3;
import com.dfsek.terra.api.world.ServerWorld;
import org.powernukkitx.terra.crosschunk.journal.EntitySpawnIntent;
import org.powernukkitx.terra.crosschunk.journal.PlacementKey;
import org.powernukkitx.terra.crosschunk.bounds.PlacementRuntimeGuard;

import java.util.Objects;

/**
 * Placement-local Terra entity facade. It has no live PNX entity handle and cannot enter
 * the Level tick list before its root placement has been durably committed.
 */
final class PNXStagedEntityDelegate implements Entity {
    private final String identifier;
    private final Object stagedHandle = new Object();
    private final PNXServerWorld owner;
    private final PlacementRuntimeGuard guard;
    private final org.powernukkitx.terra.crosschunk.bounds.PlacementRuntimeGuard guard;
    private double x;
    private double y;
    private double z;

    PNXStagedEntityDelegate(String identifier, double x, double y, double z, PNXServerWorld owner,
                            org.powernukkitx.terra.crosschunk.bounds.PlacementRuntimeGuard guard) {
        this.identifier = Objects.requireNonNull(identifier, "identifier");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.guard = Objects.requireNonNull(guard, "guard");
        setPosition(x, y, z);
    }

    @Override
    public Vector3 position() {
        return Vector3.of(x, y, z);
    }

    @Override
    public void position(Vector3 position) {
        Objects.requireNonNull(position, "position");
        guard.beforeEntityMove((int) Math.floor(position.getX()), (int) Math.floor(position.getY()),
                (int) Math.floor(position.getZ()));
        guard.beforeEntityMove((int) Math.floor(position.getX()), (int) Math.floor(position.getY()), (int) Math.floor(position.getZ()));
        setPosition(position.getX(), position.getY(), position.getZ());
    }

    private void setPosition(double x, double y, double z) {
        validatePosition(x, y, z);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    private static void validatePosition(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || x < Integer.MIN_VALUE || x > Integer.MAX_VALUE
                || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Staged entity coordinates must be finite and horizontally representable");
        }
    }

    @Override
    public void world(ServerWorld world) {
        if (world != owner) {
            throw new IllegalArgumentException("A staged Terra entity cannot be moved to another world before commit");
        }
    }

    @Override
    public ServerWorld world() {
        return owner;
    }

    @Override
    public Object getHandle() {
        return stagedHandle;
    }

    EntitySpawnIntent toIntent(PlacementKey key, int ordinal) {
        Objects.requireNonNull(key, "key");
        guard.beforeEntityMove((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        return new EntitySpawnIntent(
                EntitySpawnIntent.deterministicUuid(key.stableId(), ordinal),
                identifier, x, y, z, 0.0f, 0.0f, new byte[0]);
    }
}
