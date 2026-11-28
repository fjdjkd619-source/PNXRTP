package org.powernukkitx.terra.delegate;

import com.dfsek.terra.api.block.entity.Container;
import com.dfsek.terra.api.block.state.BlockState;
import com.dfsek.terra.api.inventory.Inventory;
import org.powernukkitx.blockentity.BlockEntityInventoryHolder;
import org.powernukkitx.nbt.tag.CompoundTag;
import org.powernukkitx.terra.crosschunk.journal.BlockEntityIntent;

import java.util.function.Consumer;

/** Detached container BlockEntity whose loot inventory is journalled instead of mutating PNX live state. */
public final class PNXStagedContainerDelegate extends PNXStagedBlockEntityDelegate implements Container {
    private final PNXStagedInventory inventory;

    public PNXStagedContainerDelegate(int x, int y, int z, String type, BlockState blockState,
                                      CompoundTag initialNbt, int size, BlockEntityInventoryHolder existing,
                                      Consumer<BlockEntityIntent> intentSink) {
        super(x, y, z, type, blockState, initialNbt, intentSink);
        this.inventory = new PNXStagedInventory(size, existing);
    }

    @Override public Inventory getInventory() { return inventory; }

    @Override
    public boolean update(boolean applyPhysics) {
        nbt.putList("Items", inventory.toNbt());
        return super.update(applyPhysics);
    }
}
