package org.powernukkitx.terra.crosschunk;

import org.powernukkitx.terra.crosschunk.state.*;
import java.util.Map;
import java.util.Set;

public final class Stage29PropertyAliasTest {
    public static void main(String[] args) {
        var bridge=new BlockStatePropertyBridge();
        bridge.mapIdentifier("minecraft:old_log","minecraft:oak_log");
        bridge.mapPropertyValue("minecraft:old_log","axis","vertical","pillar_axis","y");
        var translated=bridge.translate(new CanonicalBlockState("minecraft:old_log",new java.util.TreeMap<>(Map.of("axis","vertical"))),
                Set.of("pillar_axis"),Map.of("pillar_axis",Set.of("x","y","z")));
        if(!translated.identifier().equals("minecraft:oak_log") || !translated.properties().equals(Map.of("pillar_axis","y"))) throw new AssertionError("alias translation incorrect");
        try { bridge.translate(new CanonicalBlockState("minecraft:old_log",new java.util.TreeMap<>(Map.of("axis","sideways"))),Set.of("axis"),Map.of("axis",Set.of("x","y","z"))); throw new AssertionError("unknown value coerced"); }
        catch(IllegalArgumentException expected) { }
        System.out.println("Stage29PropertyAliasTest: PASS");
    }
}
