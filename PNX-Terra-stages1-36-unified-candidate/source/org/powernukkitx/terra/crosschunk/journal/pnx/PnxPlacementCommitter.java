package org.powernukkitx.terra.crosschunk.journal.pnx;

import org.powernukkitx.level.generator.object.BlockManager;
import org.powernukkitx.terra.crosschunk.journal.PlacementKey;
import org.powernukkitx.terra.crosschunk.scheduler.CrossChunkDependencyScheduler;

import java.io.IOException;
import java.util.Objects;

/** Narrow bridge: called by Stage15 root commit before writes are merged into the live PNX batch. */
public final class PnxPlacementCommitter {
    private PnxPlacementCommitter() {}
    public static long commit(CrossChunkDependencyScheduler.PlacementTicket ticket,
                              PlacementKey key,BlockManager staged)throws IOException{
        return commit(ticket,key,staged,java.util.List.of(),java.util.List.of());
    }
    public static long commit(CrossChunkDependencyScheduler.PlacementTicket ticket, PlacementKey key,
                              BlockManager staged, java.util.List<org.powernukkitx.terra.crosschunk.journal.BlockEntityIntent> entities,
                              java.util.List<org.powernukkitx.terra.crosschunk.journal.PhysicsIntent> physics)throws IOException{
        Objects.requireNonNull(ticket,"ticket");
        return ticket.commit(PnxPlacementRecordFactory.fromBlockManager(key,staged,entities,physics));
    }
}
