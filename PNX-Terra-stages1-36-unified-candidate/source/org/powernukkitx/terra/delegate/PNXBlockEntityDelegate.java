package org.powernukkitx.terra.delegate;

import com.dfsek.seismic.type.vector.Vector3;
import com.dfsek.terra.api.block.state.BlockState;
import com.dfsek.terra.api.block.entity.BlockEntity;
import org.powernukkitx.terra.PNXAdapter;

import java.util.Objects;

/** Read/update view over an already-loaded PNX block entity; it never creates or force-loads one. */
public final class PNXBlockEntityDelegate implements BlockEntity {
    private final org.powernukkitx.blockentity.BlockEntity handle;

    public PNXBlockEntityDelegate(org.powernukkitx.blockentity.BlockEntity handle) {
        this.handle = Objects.requireNonNull(handle, "handle");
    }

    @Override
    public boolean update(boolean applyPhysics) {
        throw new UnsupportedOperationException("PNX block-entity updates are not transactionally journaled");
    }

    @Override
    public void applyState(String state) {
        if (state != null && !state.isBlank()) {
            throw new UnsupportedOperationException("Terra block-entity state mutation has no verified PNX NBT schema");
        }
    }

    @Override public Vector3 getPosition() { return Vector3.of(getX(), getY(), getZ()); }
    @Override public int getX() { return (int) handle.getX(); }
    @Override public int getY() { return (int) handle.getY(); }
    @Override public int getZ() { return (int) handle.getZ(); }

    @Override
    public BlockState getBlockState() {
        return PNXAdapter.adapt(handle.getBlock().getBlockState());
    }

    @Override public Object getHandle() { return handle; }
}
