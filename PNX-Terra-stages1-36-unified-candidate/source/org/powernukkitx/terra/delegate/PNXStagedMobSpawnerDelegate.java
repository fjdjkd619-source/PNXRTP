package org.powernukkitx.terra.delegate;

import com.dfsek.terra.api.block.entity.MobSpawner;
import com.dfsek.terra.api.block.entity.SerialState;
import com.dfsek.terra.api.block.state.BlockState;
import com.dfsek.terra.api.entity.EntityType;
import org.powernukkitx.nbt.tag.CompoundTag;
import org.powernukkitx.registry.Registries;
import org.powernukkitx.terra.crosschunk.journal.BlockEntityIntent;

import java.util.Map;
import java.util.function.Consumer;

/** Detached PNX spawner state matching TerraScript's documented spawner properties. */
public final class PNXStagedMobSpawnerDelegate extends PNXStagedBlockEntityDelegate implements MobSpawner {
    private static final String ENTITY_ID = "EntityId";
    private static final String ENTITY_IDENTIFIER = "EntityIdentifier";
    private static final String SPAWN_DATA = "SpawnData";
    private static final String TYPE_ID = "TypeId";
    private static final String WEIGHT = "Weight";
    private static final String DELAY = "Delay";
    private static final String MIN_DELAY = "MinSpawnDelay";
    private static final String MAX_DELAY = "MaxSpawnDelay";
    private static final String SPAWN_COUNT_MIN = "MinimumSpawnerCount";
    private static final String SPAWN_COUNT_MAX = "MaximumSpawnerCount";
    private static final String MAX_NEARBY = "MaxNearbyEntities";
    private static final String PLAYER_RANGE = "RequiredPlayerRange";
    private static final String SPAWN_RANGE = "SpawnRange";

    public PNXStagedMobSpawnerDelegate(int x, int y, int z, String type, BlockState blockState,
                                       CompoundTag initialNbt, Consumer<BlockEntityIntent> intentSink) {
        super(x, y, z, type, blockState, initialNbt, intentSink);
        defaults();
    }

    @Override
    public EntityType getSpawnedType() {
        String identifier = nbt.getString(ENTITY_IDENTIFIER);
        if (identifier == null || identifier.isBlank()) {
            identifier = Registries.ENTITY.getEntityIdentifier(nbt.getInt(ENTITY_ID));
        }
        return identifier == null || identifier.isBlank() ? null : new PNXEntityType(identifier);
    }

    @Override
    public void setSpawnedType(EntityType creatureType) {
        Object raw = creatureType == null ? null : creatureType.getHandle();
        if (!(raw instanceof String identifier) || identifier.isBlank()) {
            throw new IllegalArgumentException("PNX spawner requires a namespaced Terra entity identifier");
        }
        int networkId = Registries.ENTITY.getEntityNetworkId(identifier);
        if (networkId <= 0) throw new IllegalArgumentException("Unknown PNX spawner entity identifier: " + identifier);
        nbt.putInt(ENTITY_ID, networkId)
                .putString(ENTITY_IDENTIFIER, identifier)
                .putCompound(SPAWN_DATA, new CompoundTag().putString(TYPE_ID, identifier).putInt(WEIGHT, 1));
    }

    @Override public int getDelay() { return nbt.getShort(DELAY); }
    @Override public void setDelay(int delay) { nbt.putShort(DELAY, (short) checkedShort(delay, "delay", 0)); }
    @Override public int getMinSpawnDelay() { return nbt.getShort(MIN_DELAY); }

    @Override
    public void setMinSpawnDelay(int delay) {
        int checked = checkedShort(delay, "min_delay", 0);
        if (checked > getMaxSpawnDelay()) throw new IllegalArgumentException("min_delay exceeds max_delay");
        nbt.putShort(MIN_DELAY, (short) checked);
    }

    @Override public int getMaxSpawnDelay() { return nbt.getShort(MAX_DELAY); }

    @Override
    public void setMaxSpawnDelay(int delay) {
        int checked = checkedShort(delay, "max_delay", 0);
        if (checked < getMinSpawnDelay()) throw new IllegalArgumentException("max_delay is below min_delay");
        nbt.putShort(MAX_DELAY, (short) checked);
    }

    @Override public int getSpawnCount() { return nbt.getShort(SPAWN_COUNT_MAX); }

    @Override
    public void setSpawnCount(int spawnCount) {
        short checked = (short) checkedShort(spawnCount, "spawn_count", 1);
        nbt.putShort(SPAWN_COUNT_MIN, checked).putShort(SPAWN_COUNT_MAX, checked);
    }

    @Override public int getMaxNearbyEntities() { return nbt.getShort(MAX_NEARBY); }
    @Override public void setMaxNearbyEntities(int count) { nbt.putShort(MAX_NEARBY, (short) checkedShort(count, "max_nearby", 1)); }
    @Override public int getRequiredPlayerRange() { return nbt.getShort(PLAYER_RANGE); }
    @Override public void setRequiredPlayerRange(int range) { nbt.putShort(PLAYER_RANGE, (short) checkedShort(range, "required_player_range", 1)); }
    @Override public int getSpawnRange() { return nbt.getShort(SPAWN_RANGE); }
    @Override public void setSpawnRange(int range) { nbt.putShort(SPAWN_RANGE, (short) checkedShort(range, "spawn_range", 0)); }

    @Override
    public void applyState(String state) {
        if (state == null || state.isBlank()) return;
        for (Map.Entry<String, String> property : SerialState.parse(state).entrySet()) {
            String value = property.getValue();
            switch (property.getKey()) {
                case "type" -> setSpawnedType(new PNXEntityType(value));
                case "delay" -> setDelay(Integer.parseInt(value));
                case "min_delay" -> setMinSpawnDelay(Integer.parseInt(value));
                case "max_delay" -> setMaxSpawnDelay(Integer.parseInt(value));
                case "spawn_count" -> setSpawnCount(Integer.parseInt(value));
                case "spawn_range" -> setSpawnRange(Integer.parseInt(value));
                case "max_nearby" -> setMaxNearbyEntities(Integer.parseInt(value));
                case "required_player_range" -> setRequiredPlayerRange(Integer.parseInt(value));
                default -> throw new IllegalArgumentException("Invalid PNX spawner property: " + property.getKey());
            }
        }
    }

    private void defaults() {
        if (!nbt.contains(SPAWN_RANGE)) nbt.putShort(SPAWN_RANGE, (short) 4);
        if (!nbt.contains(MIN_DELAY)) nbt.putShort(MIN_DELAY, (short) 200);
        if (!nbt.contains(MAX_DELAY)) nbt.putShort(MAX_DELAY, (short) 800);
        if (!nbt.contains(MAX_NEARBY)) nbt.putShort(MAX_NEARBY, (short) 6);
        if (!nbt.contains(PLAYER_RANGE)) nbt.putShort(PLAYER_RANGE, (short) 16);
        if (!nbt.contains(SPAWN_COUNT_MIN)) nbt.putShort(SPAWN_COUNT_MIN, (short) 1);
        if (!nbt.contains(SPAWN_COUNT_MAX)) nbt.putShort(SPAWN_COUNT_MAX, (short) 4);
        if (!nbt.contains(DELAY)) nbt.putShort(DELAY, (short) 0);
        if (!nbt.contains(ENTITY_ID)) nbt.putInt(ENTITY_ID, 0);
    }

    private static int checkedShort(int value, String property, int minimum) {
        if (value < minimum || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException(property + " must be in [" + minimum + "," + Short.MAX_VALUE + "]");
        }
        return value;
    }
}
