package org.powernukkitx.terra.delegate;

import org.powernukkitx.block.BlockFlowingWater;
import org.powernukkitx.block.BlockAir;
import org.powernukkitx.block.BlockID;
import org.powernukkitx.level.format.IChunk;
import org.powernukkitx.terra.PNXAdapter;
import org.powernukkitx.terra.crosschunk.journal.pnx.PnxBlockStateCodec;
import org.powernukkitx.terra.crosschunk.water.WaterLayerPolicy;
import com.dfsek.terra.api.block.state.BlockState;
import com.dfsek.terra.api.world.chunk.generation.ProtoChunk;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

public record PNXProtoChunk(IChunk chunk) implements ProtoChunk {
    public static org.powernukkitx.block.BlockState water = BlockFlowingWater.PROPERTIES.getDefaultState();

    @Override
    public int getMaxHeight() {
        return chunk.getDimensionData().getMaxHeight();
    }

    @Override
    public void setBlock(int x, int y, int z, @NotNull BlockState blockState) {
        var innerBlockState = ((PNXBlockStateDelegate) blockState).innerBlockState();
        org.powernukkitx.block.BlockState oldBase = chunk.getBlockState(x, y, z);
        org.powernukkitx.block.BlockState oldLayerOne = chunk.getBlockState(x, y, z, 1);
        boolean oldBaseFluid = isWaterIdentifier(oldBase.getIdentifier());
        boolean oldLayerOneFluid = isWaterIdentifier(oldLayerOne.getIdentifier());
        boolean previousFluid = oldBaseFluid || oldLayerOneFluid;

        String identifier = innerBlockState.getIdentifier();
        boolean intrinsicWaterPlant = isIntrinsicWaterPlant(identifier);
        boolean explicitWaterlogged = false;
        boolean hasWaterloggedProperty = false;
        for (var property : innerBlockState.getBlockPropertyValues()) {
            if ("waterlogged".equals(property.getPropertyType().getName())) {
                hasWaterloggedProperty = true;
                explicitWaterlogged = Boolean.parseBoolean(String.valueOf(property.getValue()));
                break;
            }
        }
        boolean supportsWaterlogging = innerBlockState.toBlock().getWaterloggingLevel() > 0;
        boolean waterlogged = hasWaterloggedProperty
                ? explicitWaterlogged
                : supportsWaterlogging && previousFluid;

        byte[] waterState;
        try {
            org.powernukkitx.block.BlockState preserved = oldLayerOneFluid ? oldLayerOne
                    : oldBaseFluid ? oldBase : water;
            waterState = PnxBlockStateCodec.encode(preserved);
        } catch (IOException invalidWaterState) {
            throw new IllegalStateException("Could not encode canonical/preserved PNX water state", invalidWaterState);
        }

        WaterLayerPolicy.Result policy = WaterLayerPolicy.resolve(identifier,
                intrinsicWaterPlant, waterlogged, waterState);
        boolean newStateIsLiquid = isWaterIdentifier(identifier);
        chunk.setBlockState(x, y, z, innerBlockState);
        if (policy.writeWaterLayer() && !newStateIsLiquid) {
            try {
                chunk.setBlockState(x, y, z, PnxBlockStateCodec.decode(policy.canonicalWaterState()), 1);
            } catch (IOException invalidWaterState) {
                throw new IllegalStateException("Could not decode canonical PNX water layer state", invalidWaterState);
            }
        } else if (oldLayerOneFluid) {
            chunk.setBlockState(x, y, z, BlockAir.STATE, 1);
        }
    }

    private static boolean isWaterIdentifier(String identifier) {
        return Objects.equals(identifier, BlockID.WATER)
                || Objects.equals(identifier, BlockID.FLOWING_WATER);
    }

    private static boolean isIntrinsicWaterPlant(String identifier) {
        return Objects.equals(identifier, BlockID.KELP)
                || "minecraft:seagrass".equals(identifier)
                || "minecraft:tall_seagrass".equals(identifier);
    }

    @Override
    public @NotNull
    BlockState getBlock(int i, int i1, int i2) {
        return PNXAdapter.adapt(chunk.getBlockState(i, i1, i2));
    }

    @Override
    public Object getHandle() {
        return chunk;
    }
}
