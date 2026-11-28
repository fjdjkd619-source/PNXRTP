package org.powernukkitx.terra.crosschunk.state;

import java.util.*;

/**
 * Fail-closed Terra-to-PNX identifier/property compatibility table.
 * Every non-identity mapping is explicit; conflicting registrations are rejected.
 */
public final class BlockStatePropertyBridge {
    private final Map<String,String> identifiers = new HashMap<>();
    private final Map<PropertyKey,Map<String,String>> propertyAliases = new HashMap<>();

    /** Known Terra-facing names already used by the pinned PNX adapter. */
    public static BlockStatePropertyBridge vanillaDefaults() {
        BlockStatePropertyBridge bridge = new BlockStatePropertyBridge();
        bridge.mapIdentifier("minecraft:snow", "minecraft:snow_layer");
        bridge.mapIdentifier("minecraft:snow_block", "minecraft:snow");
        return bridge;
    }

    public synchronized void mapIdentifier(String terraId, String pnxId) {
        requireId(terraId);
        requireId(pnxId);
        String previous = identifiers.putIfAbsent(terraId, pnxId);
        if (previous != null && !previous.equals(pnxId)) {
            throw new IllegalArgumentException("Conflicting block identifier alias for " + terraId
                    + ": " + previous + " versus " + pnxId);
        }
    }

    /** Return the Terra-facing identifier for a PNX block ID, rejecting ambiguous reverse aliases. */
    public synchronized String toTerraIdentifier(String pnxId) {
        requireId(pnxId);
        String result = null;
        for (Map.Entry<String, String> entry : identifiers.entrySet()) {
            if (!entry.getValue().equals(pnxId)) continue;
            if (result != null && !result.equals(entry.getKey())) {
                throw new IllegalStateException("Ambiguous reverse block identifier alias for " + pnxId);
            }
            result = entry.getKey();
        }
        return result == null ? pnxId : result;
    }

    public synchronized void mapPropertyValue(String terraBlock, String terraProperty, String terraValue,
                                               String pnxProperty, String pnxValue) {
        requireId(terraBlock);
        if (!terraProperty.matches("[a-z0-9_]+") || !pnxProperty.matches("[a-z0-9_]+")
                || terraValue == null || terraValue.isBlank()
                || pnxValue == null || pnxValue.isBlank()) {
            throw new IllegalArgumentException("Invalid property alias");
        }
        Map<String, String> aliases = propertyAliases.computeIfAbsent(
                new PropertyKey(terraBlock, terraProperty, terraValue), ignored -> new HashMap<>());
        String previous = aliases.putIfAbsent(pnxProperty, pnxValue);
        if (previous != null && !previous.equals(pnxValue)) {
            throw new IllegalArgumentException("Conflicting property value alias for "
                    + terraBlock + "[" + terraProperty + "=" + terraValue + "]");
        }
    }

    public synchronized CanonicalBlockState translate(CanonicalBlockState terra,
                                                        Set<String> pnxPropertyNames,
                                                        Map<String, Set<String>> pnxAllowedValues) {
        return translateInternal(terra, null, pnxPropertyNames, pnxAllowedValues);
    }

    /** Strict overload: the target block identifier must exist in the pinned PNX registry. */
    public synchronized CanonicalBlockState translate(CanonicalBlockState terra,
                                                        Set<String> pnxBlockIdentifiers,
                                                        Set<String> pnxPropertyNames,
                                                        Map<String, Set<String>> pnxAllowedValues) {
        Objects.requireNonNull(pnxBlockIdentifiers, "pnxBlockIdentifiers");
        return translateInternal(terra, pnxBlockIdentifiers, pnxPropertyNames, pnxAllowedValues);
    }

    private CanonicalBlockState translateInternal(CanonicalBlockState terra,
                                                   Set<String> pnxBlockIdentifiers,
                                                   Set<String> pnxPropertyNames,
                                                   Map<String, Set<String>> pnxAllowedValues) {
        Objects.requireNonNull(terra, "terra");
        Objects.requireNonNull(pnxPropertyNames, "pnxPropertyNames");
        Objects.requireNonNull(pnxAllowedValues, "pnxAllowedValues");
        String pnxId = identifiers.getOrDefault(terra.identifier(), terra.identifier());
        if (pnxBlockIdentifiers != null && !pnxBlockIdentifiers.contains(pnxId)) {
            throw new IllegalArgumentException("PNX block identifier is not registered: " + pnxId);
        }
        TreeMap<String, String> translated = new TreeMap<>();
        for (var entry : terra.properties().entrySet()) {
            String targetName = entry.getKey();
            String targetValue = entry.getValue();
            Map<String, String> aliases = propertyAliases.get(
                    new PropertyKey(terra.identifier(), entry.getKey(), entry.getValue()));
            if (aliases != null && !aliases.isEmpty()) {
                if (aliases.size() != 1) {
                    throw new IllegalArgumentException("Ambiguous property mapping for " + entry.getKey());
                }
                var alias = aliases.entrySet().iterator().next();
                targetName = alias.getKey();
                targetValue = alias.getValue();
            }
            if (!pnxPropertyNames.contains(targetName)) {
                throw new IllegalArgumentException("PNX block " + pnxId + " has no property " + targetName);
            }
            Set<String> allowed = pnxAllowedValues.get(targetName);
            if (allowed == null || !allowed.contains(targetValue)) {
                throw new IllegalArgumentException("PNX property value is not registered: "
                        + targetName + "=" + targetValue);
            }
            if (translated.put(targetName, targetValue) != null) {
                throw new IllegalArgumentException("Property mapping collision: " + targetName);
            }
        }
        return new CanonicalBlockState(pnxId, translated);
    }

    private static void requireId(String id) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid namespaced identifier: " + id);
        }
    }

    private record PropertyKey(String block, String property, String value) { }
}
