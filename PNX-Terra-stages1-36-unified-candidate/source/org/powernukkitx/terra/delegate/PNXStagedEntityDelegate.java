package org.powernukkitx.terra.delegate;

import com.dfsek.terra.api.entity.Entity;
import com.dfsek.terra.api.util.vector.Vector3;
import com.dfsek.terra.api.world.ServerWorld;
import org.powernukkitx.terra.crosschunk.journal.EntitySpawnIntent;
import org.powernukkitx.terra.crosschunk.journal.PlacementKey;

import java.util.Objects;

/**
 * Placement-local Terra entity facade. It has no live PNX entity handle and cannot enter
 * the Level tick list before its root placement has been durably committed.
 */
final class PNXStagedEntityDelegate implements Entity {
    private final String identifier;
    private final Object stagedHandle = new Object();
    private final PNXServerWorld owner;
    private double x;
    private double y;
    private double z;

    PNXStagedEntityDelegate(String identifier, double x, double y, double z, PNXServerWorld owner) {
        this.identifier = Objects.requireNonNull(identifier, "identifier");
        this.owner = Objects.requireNonNull(owner, "owner");
        setPosition(x, y, z);
    }

    @Override
    public Vector3 position() {
        return Vector3.of(x, y, z);
    }

    @Override
    public void position(Vector3 position) {
        Objects.requireNonNull(position, "position");
        setPosition(position.getX(), position.getY(), position.getZ());
    }

    private void setPosition(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Staged entity coordinates must be finite");
        }
        this.x = x;
        this.y = y;
        this.z = z;
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
        return new EntitySpawnIntent(
                EntitySpawnIntent.deterministicUuid(key.stableId(), ordinal),
                identifier, x, y, z, 0.0f, 0.0f, new byte[0]);
    }
}
