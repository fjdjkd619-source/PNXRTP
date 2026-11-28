package org.powernukkitx.terra.delegate;

import com.dfsek.seismic.type.vector.Vector3;
import com.dfsek.terra.api.block.entity.BlockEntity;
import com.dfsek.terra.api.block.state.BlockState;
import org.powernukkitx.nbt.tag.CompoundTag;
import org.powernukkitx.terra.crosschunk.journal.BlockEntityIntent;
import org.powernukkitx.terra.crosschunk.journal.pnx.PnxBlockEntityNbtCodec;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/** A detached Terra view whose changes are serialized into the active placement journal. */
public abstract class PNXStagedBlockEntityDelegate implements BlockEntity {
    protected final int x;
    protected final int y;
    protected final int z;
    protected final String type;
    protected final CompoundTag nbt;
    private final BlockState blockState;
    private final Consumer<BlockEntityIntent> intentSink;

    protected PNXStagedBlockEntityDelegate(int x, int y, int z, String type, BlockState blockState,
                                           CompoundTag initialNbt, Consumer<BlockEntityIntent> intentSink) {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("BlockEntity type is blank");
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
        this.blockState = Objects.requireNonNull(blockState, "blockState");
        this.intentSink = Objects.requireNonNull(intentSink, "intentSink");
        this.nbt = initialNbt == null ? new CompoundTag() : initialNbt.copy();
        this.nbt.putString("id", type).putInt("x", x).putInt("y", y).putInt("z", z);
    }

    @Override
    public boolean update(boolean applyPhysics) {
        if (applyPhysics) {
            throw new UnsupportedOperationException("BlockEntity update physics must use a separate durable physics intent");
        }
        try {
            intentSink.accept(PnxBlockEntityNbtCodec.toIntent(x, y, z, type, nbt));
            return true;
        } catch (IOException failure) {
            throw new IllegalStateException("Could not encode staged PNX BlockEntity intent", failure);
        }
    }

    @Override
    public void applyState(String state) {
        if (state != null && !state.isBlank()) {
            throw new UnsupportedOperationException("This staged PNX BlockEntity type has no Terra state schema");
        }
    }

    @Override public Vector3 getPosition() { return Vector3.of(x, y, z); }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getZ() { return z; }
    @Override public BlockState getBlockState() { return blockState; }
    @Override public Object getHandle() { return nbt; }
}