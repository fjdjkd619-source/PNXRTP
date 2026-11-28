package org.powernukkitx.terra.crosschunk;

import org.powernukkitx.terra.crosschunk.water.WaterLayerPolicy;
import java.util.Arrays;

public final class Stage23WaterPhysicsTest {
    public static void main(String[] args) {
        byte[] water={9,8,7};
        WaterLayerPolicy.Result waterlogged=WaterLayerPolicy.resolve("minecraft:oak_leaves","waterlogged","true",false,
                (id,key,value)->key.equals("waterlogged")&&value.equals("true"),water);
        check(waterlogged.writeWaterLayer(),"explicit waterlogged property creates extra layer intent");
        check(Arrays.equals(waterlogged.canonicalWaterState(),water),"canonical water state copied");
        WaterLayerPolicy.Result dry=WaterLayerPolicy.resolve("minecraft:stone","waterlogged","false",false,
                (id,key,value)->value.equals("true"),water);
        check(!dry.writeWaterLayer(),"dry block does not create water layer");
        WaterLayerPolicy.Result kelp=WaterLayerPolicy.resolve("minecraft:kelp",null,null,true,
                (id,key,value)->false,water);
        check(kelp.writeWaterLayer(),"intrinsically water-containing plant retains water layer");
        boolean rejected=false;
        try { WaterLayerPolicy.resolve("minecraft:water","waterlogged","true",true,(id,key,value)->true,null); }
        catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"missing water state payload rejected");
        System.out.println("Stage23WaterPhysicsTest: PASS");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
