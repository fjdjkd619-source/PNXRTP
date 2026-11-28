package org.powernukkitx.terra.crosschunk;

import org.powernukkitx.terra.crosschunk.state.*;
import java.util.*;

public final class Stage21StateBridgeTest {
    public static void main(String[] args) {
        CanonicalBlockState parsed=CanonicalBlockState.parse("minecraft:oak_log[z=0,axis=y]");
        check(parsed.asString().equals("minecraft:oak_log[axis=y,z=0]"),"canonical property ordering");
        BlockStatePropertyBridge bridge=new BlockStatePropertyBridge();
        bridge.mapIdentifier("terra:oak_log","minecraft:oak_log");
        bridge.mapPropertyValue("terra:oak_log","axis","vertical","axis","y");
        CanonicalBlockState translated=bridge.translate(CanonicalBlockState.parse("terra:oak_log[axis=vertical]"),
                Set.of("axis"),Map.of("axis",Set.of("x","y","z")));
        check(translated.asString().equals("minecraft:oak_log[axis=y]"),"explicit property translation");
        boolean rejected=false;
        try { bridge.translate(CanonicalBlockState.parse("minecraft:oak_log[axis=bad]"),Set.of("axis"),Map.of("axis",Set.of("x","y","z"))); }
        catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"unknown values fail closed");
        rejected=false;
        try { CanonicalBlockState.parse("minecraft:oak_log[axis=x,axis=z]"); } catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"duplicate property rejected");
        System.out.println("Stage21StateBridgeTest: PASS");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
