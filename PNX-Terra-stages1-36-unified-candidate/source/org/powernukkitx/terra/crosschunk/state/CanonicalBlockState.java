package org.powernukkitx.terra.crosschunk.state;

import java.util.*;

/** Strict parser for Terra BlockState#getAsString(true), e.g. minecraft:oak_log[axis=y]. */
public record CanonicalBlockState(String identifier, NavigableMap<String,String> properties) {
    public CanonicalBlockState {
        if (identifier == null || !identifier.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Invalid namespaced block identifier: " + identifier);
        TreeMap<String,String> copy = new TreeMap<>();
        Objects.requireNonNull(properties,"properties").forEach((key,value) -> {
            if (key == null || !key.matches("[a-z0-9_]+") || value == null || value.isBlank())
                throw new IllegalArgumentException("Invalid block property");
            if (copy.put(key,value) != null) throw new IllegalArgumentException("Duplicate property " + key);
        });
        properties = Collections.unmodifiableNavigableMap(copy);
    }

    public static CanonicalBlockState parse(String text) {
        Objects.requireNonNull(text,"text");
        String input=text.trim();
        int open=input.indexOf('[');
        if(open<0) return new CanonicalBlockState(input,new TreeMap<>());
        if(!input.endsWith("]") || input.indexOf('[',open+1)>=0) throw new IllegalArgumentException("Malformed block-state properties: " + text);
        String id=input.substring(0,open).trim(); String body=input.substring(open+1,input.length()-1);
        TreeMap<String,String> properties=new TreeMap<>();
        if(!body.isBlank()) for(String part:body.split(",",-1)) {
            int equals=part.indexOf('=');
            if(equals<=0 || equals==part.length()-1 || part.indexOf('=',equals+1)>=0) throw new IllegalArgumentException("Malformed property: " + part);
            String key=part.substring(0,equals).trim(), value=part.substring(equals+1).trim();
            if(properties.put(key,value)!=null) throw new IllegalArgumentException("Duplicate property: " + key);
        }
        return new CanonicalBlockState(id,properties);
    }

    public String asString() {
        if(properties.isEmpty()) return identifier;
        StringJoiner joiner=new StringJoiner(",",identifier+"[","]");
        properties.forEach((key,value)->joiner.add(key+"="+value));
        return joiner.toString();
    }
}
