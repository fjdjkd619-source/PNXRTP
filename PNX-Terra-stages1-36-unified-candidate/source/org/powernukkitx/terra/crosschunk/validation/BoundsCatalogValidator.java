package org.powernukkitx.terra.crosschunk.validation;

import org.powernukkitx.terra.crosschunk.bounds.ScriptBoundsProfile;
import java.util.*;

/** Startup validation for a supplied, audited structure-bounds manifest. */
public final class BoundsCatalogValidator {
    private BoundsCatalogValidator() { }
    public static Validation validate(Collection<ScriptBoundsProfile> profiles) {
        Objects.requireNonNull(profiles, "profiles");
        TreeMap<String, ScriptBoundsProfile> byId = new TreeMap<>();
        List<String> errors = new ArrayList<>();
        for (ScriptBoundsProfile profile : profiles) {
            if (profile == null) { errors.add("null profile"); continue; }
            String key = profile.packId() + ":" + profile.structureId();
            if (byId.putIfAbsent(key, profile) != null) errors.add("duplicate profile: " + key);
            if (profile.maxBlockWrites() == 0 && profile.maxBlockReads() == 0 && profile.maxEntitySpawns() == 0)
                errors.add("profile has no permitted operations: " + key);
            if (profile.evidence().isBlank()) errors.add("missing evidence: " + key);
            if (profile.transitiveDependencies().stream().anyMatch(v -> v == null || v.isBlank()))
                errors.add("invalid dependency entry: " + key);
        }
        return new Validation(byId, errors);
    }
    public record Validation(Map<String, ScriptBoundsProfile> profiles, List<String> errors) {
        public Validation { profiles = Collections.unmodifiableMap(new TreeMap<>(profiles)); errors = List.copyOf(errors); }
        public boolean valid() { return errors.isEmpty(); }
        public void requireValid() { if (!valid()) throw new IllegalStateException("Invalid bounds catalog: " + String.join("; ", errors)); }
    }
}
