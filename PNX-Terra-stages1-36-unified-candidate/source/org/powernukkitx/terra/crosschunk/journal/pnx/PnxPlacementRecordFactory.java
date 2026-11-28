package org.powernukkitx.terra.crosschunk.journal.pnx;

import org.powernukkitx.block.Block;
import org.powernukkitx.level.generator.object.BlockManager;
import org.powernukkitx.terra.crosschunk.journal.*;

import java.io.IOException;
import java.util.*;

/** Converts a staged PNX BlockManager into a durable, platform-canonical root placement record. */
public final class PnxPlacementRecordFactory {
    private PnxPlacementRecordFactory() {}
    public static PlacementRecord fromBlockManager(PlacementKey key,BlockManager manager)throws IOException{
        return fromBlockManager(key,manager,List.of(),List.of());
    }
    public static PlacementRecord fromBlockManager(PlacementKey key,BlockManager manager,
            List<BlockEntityIntent> blockEntities,List<PhysicsIntent> physics)throws IOException{
        return fromBlockManager(key,manager,blockEntities,physics,List.of());
    }
    public static PlacementRecord fromBlockManager(PlacementKey key,BlockManager manager,
            List<BlockEntityIntent> blockEntities,List<PhysicsIntent> physics,List<EntitySpawnIntent> entitySpawns)throws IOException{
        Objects.requireNonNull(key,"key");Objects.requireNonNull(manager,"manager");
        List<BlockWrite>writes=new ArrayList<>();
        for(Block block:manager.getBlocks()){
            writes.add(new BlockWrite(block.getFloorX(),block.getFloorY(),block.getFloorZ(),block.layer,
                    PnxBlockStateCodec.encode(block.getBlockState())));
        }
        if(writes.isEmpty()&&blockEntities.isEmpty()&&physics.isEmpty()&&entitySpawns.isEmpty())
            throw new IOException("Refusing to journal an empty root placement");
        return new PlacementRecord(key,writes,blockEntities,physics,entitySpawns);
    }
}
