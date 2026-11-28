package org.powernukkitx.terra.delegate;

import org.powernukkitx.level.format.IChunk;
import org.powernukkitx.terra.PNXAdapter;
import com.dfsek.terra.api.block.state.BlockState;
import com.dfsek.terra.api.world.ServerWorld;
import com.dfsek.terra.api.world.chunk.Chunk;
import org.jetbrains.annotations.NotNull;

public record PNXChunkDelegate(ServerWorld world, IChunk chunk) implements Chunk {
    @Override
    public void setBlock(int x, int y, int z, BlockState blockState, boolean physics) {
        setBlock(x, y, z, blockState);
    }

    @Override
    public void setBlock(int x, int y, int z, @NotNull BlockState blockState) {
        chunk.setBlockState(x, y, z, ((PNXBlockStateDelegate) blockState).getHandle());
    }

    @Override
    public @NotNull BlockState getBlock(int x, int y, int z) {
        return PNXAdapter.adapt(chunk.getBlockState(x, y, z));
    }

    @Override public int getX() { return chunk.getX(); }
    @Override public int getZ() { return chunk.getZ(); }
    @Override public ServerWorld getWorld() { return world; }
    @Override public Object getHandle() { return chunk; }
}
