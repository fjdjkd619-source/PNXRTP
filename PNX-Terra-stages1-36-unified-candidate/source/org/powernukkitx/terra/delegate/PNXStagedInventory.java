package org.powernukkitx.terra.delegate;

import com.dfsek.terra.api.inventory.Inventory;
import com.dfsek.terra.api.inventory.ItemStack;
import org.powernukkitx.blockentity.BlockEntityInventoryHolder;
import org.powernukkitx.item.Item;
import org.powernukkitx.nbt.tag.CompoundTag;
import org.powernukkitx.nbt.tag.ListTag;
import org.powernukkitx.nbt.tag.Tag;
import org.powernukkitx.utils.ItemHelper;

/** Detached inventory used by Terra loot functions before the target chunk is finalized. */
public final class PNXStagedInventory implements Inventory {
    private final Item[] contents;

    public PNXStagedInventory(int size, BlockEntityInventoryHolder existing) {
        if (size <= 0) throw new IllegalArgumentException("Inventory size must be positive");
        this.contents = new Item[size];
        if (existing != null) {
            int copySize = Math.min(size, existing.getInventory().getSize());
            for (int i = 0; i < copySize; i++) {
                Item item = existing.getInventory().getItem(i);
                if (item != null && !item.isNull() && item.getCount() > 0) contents[i] = item.clone();
            }
        }
    }

    @Override
    public void setItem(int slot, ItemStack newStack) {
        checkSlot(slot);
        if (newStack == null) {
            contents[slot] = null;
            return;
        }
        Object handle = newStack.getHandle();
        if (!(handle instanceof Item item)) {
            throw new IllegalArgumentException("Terra item stack is not backed by a PNX Item");
        }
        contents[slot] = item.clone();
    }

    @Override public int getSize() { return contents.length; }

    @Override
    public ItemStack getItem(int slot) {
        checkSlot(slot);
        Item item = contents[slot];
        return item == null || item.isNull() || item.getCount() <= 0 ? null : new PNXItemStack(item.clone());
    }

    @Override public Object getHandle() { return this; }

    public ListTag<CompoundTag> toNbt() {
        ListTag<CompoundTag> items = new ListTag<>(Tag.TAG_Compound);
        for (int slot = 0; slot < contents.length; slot++) {
            Item item = contents[slot];
            if (item != null && !item.isNull() && item.getCount() > 0) items.add(ItemHelper.write(item, slot));
        }
        return items;
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= contents.length) throw new IndexOutOfBoundsException("Inventory slot: " + slot);
    }
}
