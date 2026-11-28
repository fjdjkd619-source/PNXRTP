package org.powernukkitx.terra.crosschunk.state;

import java.util.*;

/**
 * Fail-closed Terra-to-PNX property compatibility table. No guessed aliases or value coercions:
 * each non-identity mapping must be registered explicitly after checking both registries.
 */
public final class BlockStatePropertyBridge {
    private final Map<String,String> identifiers = new HashMap<>();
    private final Map<PropertyKey,Map<String,String>> propertyAliases = new HashMap<>();

    public synchronized void mapIdentifier(String terraId,String pnxId) {
        requireId(terraId); requireId(pnxId); identifiers.put(terraId,pnxId);
    }
    public synchronized void mapPropertyValue(String terraBlock,String terraProperty,String terraValue,
                                               String pnxProperty,String pnxValue) {
        requireId(terraBlock);
        if(!terraProperty.matches("[a-z0-9_]+")||!pnxProperty.matches("[a-z0-9_]+")||terraValue.isBlank()||pnxValue.isBlank())
            throw new IllegalArgumentException("Invalid property alias");
        propertyAliases.computeIfAbsent(new PropertyKey(terraBlock,terraProperty,terraValue),ignored->new HashMap<>())
                .put(pnxProperty,pnxValue);
    }

    public synchronized CanonicalBlockState translate(CanonicalBlockState terra,
                                                        Set<String> pnxPropertyNames,
                                                        Map<String,Set<String>> pnxAllowedValues) {
        String pnxId=identifiers.getOrDefault(terra.identifier(),terra.identifier());
        TreeMap<String,String> translated=new TreeMap<>();
        for(var entry:terra.properties().entrySet()) {
            String targetName=entry.getKey(), targetValue=entry.getValue();
            Map<String,String> aliases=propertyAliases.get(new PropertyKey(terra.identifier(),entry.getKey(),entry.getValue()));
            if(aliases!=null&&!aliases.isEmpty()) {
                if(aliases.size()!=1) throw new IllegalArgumentException("Ambiguous property mapping for " + entry.getKey());
                var alias=aliases.entrySet().iterator().next(); targetName=alias.getKey(); targetValue=alias.getValue();
            }
            if(!pnxPropertyNames.contains(targetName)) throw new IllegalArgumentException("PNX block " + pnxId + " has no property " + targetName);
            Set<String> allowed=pnxAllowedValues.get(targetName);
            if(allowed==null||!allowed.contains(targetValue)) throw new IllegalArgumentException("PNX property value is not registered: " + targetName + "=" + targetValue);
            if(translated.put(targetName,targetValue)!=null) throw new IllegalArgumentException("Property mapping collision: " + targetName);
        }
        return new CanonicalBlockState(pnxId,translated);
    }
    private static void requireId(String id){if(id==null||!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid namespaced identifier: "+id);}
    private record PropertyKey(String block,String property,String value){}
}
