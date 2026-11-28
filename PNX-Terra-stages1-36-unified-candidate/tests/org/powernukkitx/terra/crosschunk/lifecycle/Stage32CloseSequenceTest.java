package org.powernukkitx.terra.crosschunk.lifecycle;

import java.io.IOException;
import java.util.*;

public final class Stage32CloseSequenceTest {
    public static void main(String[] args) throws Exception {
        var order=new ArrayList<Integer>();
        AutoCloseable a=()->{order.add(1);throw new IOException("first");};
        AutoCloseable b=()->order.add(2);
        AutoCloseable c=()->{order.add(3);throw new IOException("third");};
        try { RuntimeCloseSequence.closeAll(List.of(a,b,c)); throw new AssertionError("close failures swallowed"); }
        catch(IOException expected) { if(expected.getSuppressed().length!=1) throw new AssertionError("suppressed close failure missing"); }
        if(!order.equals(List.of(1,2,3))) throw new AssertionError("resources not all closed: "+order);
        System.out.println("Stage32CloseSequenceTest: PASS");
    }
}
