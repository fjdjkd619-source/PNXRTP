package org.powernukkitx.terra.delegate;

import org.powernukkitx.block.BlockAir;
import org.powernukkitx.block.BlockProperties;
import org.powernukkitx.block.property.type.BlockPropertyType;
import org.powernukkitx.registry.Registries;
import org.powernukkitx.terra.crosschunk.state.BlockStatePropertyBridge;
import com.dfsek.terra.api.block.BlockType;
import com.dfsek.terra.api.block.state.BlockState;
import com.dfsek.terra.api.block.state.properties.Property;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Terra state view over a canonical PNX state. Only exactly matching property schemas are exposed. */
public record PNXBlockStateDelegate(org.powernukkitx.block.BlockState innerBlockState) implements BlockState {
    private static final BlockStatePropertyBridge BLOCK_ALIASES = BlockStatePropertyBridge.vanillaDefaults();
    @Override
    public boolean matches(BlockState blockState) {
        return blockState instanceof PNXBlockStateDelegate other
                && other.innerBlockState.equals(this.innerBlockState);
    }

    @Override
    public <T extends Comparable<T>> boolean has(Property<T> property) {
        return resolveProperty(property) != null;
    }

    @Override
    public <T extends Comparable<T>> T get(Property<T> property) {
        BlockPropertyType<T> pnxProperty = requireProperty(property);
        return innerBlockState.getPropertyValue(pnxProperty);
    }

    @Override
    public <T extends Comparable<T>> BlockState set(Property<T> property, T value) {
        BlockPropertyType<T> pnxProperty = requireProperty(property);
        if (!pnxProperty.getValidValues().contains(value)) {
            throw new IllegalArgumentException("Unsupported value " + value + " for PNX property " + pnxProperty.getName());
        }
        BlockProperties properties = Registries.BLOCK.getBlockProperties(innerBlockState.getIdentifier());
        return new PNXBlockStateDelegate(innerBlockState.setPropertyValue(properties, pnxProperty, value));
    }

    private <T extends Comparable<T>> BlockPropertyType<T> requireProperty(Property<T> property) {
        BlockPropertyType<T> result = resolveProperty(property);
        if (result == null) {
            throw new IllegalArgumentException("PNX block " + innerBlockState.getIdentifier()
                    + " does not expose a compatible Terra property " + property.getID());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private <T extends Comparable<T>> BlockPropertyType<T> resolveProperty(Property<T> property) {
        Objects.requireNonNull(property, "property");
        BlockProperties properties = Registries.BLOCK.getBlockProperties(innerBlockState.getIdentifier());
        if (properties == null) return null;
        for (BlockPropertyType<?> candidate : properties.getPropertyTypeSet()) {
            if (!candidate.getName().equals(property.getID())) continue;
            Object defaultValue = candidate.getDefaultValue();
            if (defaultValue == null || !property.getType().equals(defaultValue.getClass())) continue;
            if (!new HashSet<>(candidate.getValidValues()).equals(new HashSet<>(property.values()))) continue;
            return (BlockPropertyType<T>) candidate;
        }
        return null;
    }

    @Override
    public BlockType getBlockType() {
        return new PNXBlockType(innerBlockState);
    }

    @Override
    public String getAsString(boolean properties) {
        String name = BLOCK_ALIASES.toTerraIdentifier(innerBlockState.getIdentifier());
        if (!properties || innerBlockState.getBlockPropertyValues().isEmpty()) return name;
        List<String> encoded = innerBlockState.getBlockPropertyValues().stream()
                .sorted(Comparator.comparing(value -> value.getPropertyType().getName()))
                .map(value -> value.getPropertyType().getName() + "=" + value.getSerializedValue())
                .toList();
        return name + "[" + String.join(",", encoded) + "]";
    }

    @Override
    public boolean isAir() {
        return innerBlockState == BlockAir.STATE || innerBlockState.equals(BlockAir.STATE);
    }

    @Override
    public org.powernukkitx.block.BlockState getHandle() {
        return innerBlockState;
    }
}
