package org.powernukkitx.terra.crosschunk.water;

import java.util.Objects;

/** Explicit Bedrock two-layer water policy; callers must supply authoritative state classification. */
public final class WaterLayerPolicy {
    private WaterLayerPolicy() {}
    public record Result(boolean writeWaterLayer, byte[] canonicalWaterState) {
        public Result { canonicalWaterState=canonicalWaterState==null?null:canonicalWaterState.clone();
            if(writeWaterLayer&&(canonicalWaterState==null||canonicalWaterState.length==0))throw new IllegalArgumentException("Water layer requires a canonical water state"); }
        @Override public byte[] canonicalWaterState(){return canonicalWaterState==null?null:canonicalWaterState.clone();}
    }
    @FunctionalInterface public interface WaterloggedClassifier { boolean isWaterlogged(String blockIdentifier, String propertyName, String propertyValue); }
    /**
     * PNX-specific overload: PNX models Bedrock waterlogging as a second block layer and
     * exposes waterlogging support through Block#getWaterloggingLevel(), not a universal
     * block-state property. The adapter supplies the already-validated decision.
     */
    public static Result resolve(String blockIdentifier, boolean intrinsicallyWaterContaining,
                                 boolean waterlogged, byte[] canonicalWaterState) {
        Objects.requireNonNull(blockIdentifier, "blockIdentifier");
        boolean writeWater = intrinsicallyWaterContaining || waterlogged;
        return new Result(writeWater, writeWater ? canonicalWaterState : null);
    }

    public static Result resolve(String blockIdentifier, String propertyName, String propertyValue,
                                 boolean intrinsicallyWaterContaining, WaterloggedClassifier classifier,
                                 byte[] canonicalWaterState) {
        Objects.requireNonNull(blockIdentifier,"blockIdentifier"); Objects.requireNonNull(classifier,"classifier");
        boolean water=intrinsicallyWaterContaining || (propertyName!=null && propertyValue!=null
                && classifier.isWaterlogged(blockIdentifier,propertyName,propertyValue));
        return new Result(water,water?canonicalWaterState:null);
    }
}
