package org.powernukkitx.terra.crosschunk.physics;

import org.powernukkitx.terra.crosschunk.journal.*;
import java.util.*;
import java.util.concurrent.*;

public final class Stage28PhysicsBatchTest {
    public static void main(String[] args) throws Exception {
        var seen=new ArrayList<String>(); java.util.concurrent.Executor executor=Runnable::run;
        int count=PhysicsIntentBatch.dispatch(List.of(new PhysicsIntent(2,1,1,true),new PhysicsIntent(1,1,1,true),
                new PhysicsIntent(2,1,1,true),new PhysicsIntent(3,1,1,false)),executor,new ChunkPos(0,0),
                (x,y,z)->seen.add(x+":"+y+":"+z)).get();
        if(count!=2 || !seen.equals(List.of("1:1:1","2:1:1"))) throw new AssertionError("physics order/dedup incorrect: "+seen);
        try { PhysicsIntentBatch.dispatch(List.of(new PhysicsIntent(16,0,0,true)),executor,new ChunkPos(0,0),(x,y,z)->{}); throw new AssertionError("cross-chunk physics accepted"); }
        catch(IllegalArgumentException expected) { }
        System.out.println("Stage28PhysicsBatchTest: PASS");
    }
}
