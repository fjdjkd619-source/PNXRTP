package org.powernukkitx.terra.crosschunk.validation;

import org.powernukkitx.terra.crosschunk.bounds.ScriptBoundsProfile;
import java.util.*;

/** Startup validation for an audited structure-bounds manifest and its enabled roots. */
public final class BoundsCatalogValidator {
    private BoundsCatalogValidator() { }

    public static Validation validate(Collection<ScriptBoundsProfile> profiles) {
        Objects.requireNonNull(profiles, "profiles");
        TreeMap<String, ScriptBoundsProfile> byId = new TreeMap<>();
        List<String> errors = new ArrayList<>();
        for (ScriptBoundsProfile profile : profiles) {
            if (profile == null) { errors.add("null profile"); continue; }
            String key = profile.packId() + ":" + normalize(profile.structureId());
            if (byId.putIfAbsent(key, profile) != null) errors.add("duplicate profile: " + key);
            if (profile.maxBlockWrites() == 0 && profile.maxBlockReads() == 0
                    && profile.maxEntitySpawns() == 0)
                errors.add("profile has no permitted operations: " + key);
            if (profile.evidence().isBlank()) errors.add("missing evidence: " + key);
            if (profile.transitiveDependencies().stream().anyMatch(v -> v == null || v.isBlank()))
                errors.add("invalid dependency entry: " + key);
            // A wildcard is permitted only for an explicitly bounded emergency profile.
            // Every nested operation still passes through the shared root PlacementRuntimeGuard.
            if (profile.transitiveDependencies().contains("*")
                    && !profile.evidence().contains("BOUNDED_FAIL_CLOSED radius=32"))
                errors.add("wildcard dependency is only allowed for bounded fallback profiles: " + key);
        }
        return new Validation(byId, errors);
    }

    /**
     * Validates the exact set of enabled structure roots. Any enabled root without a profile is a
     * release-blocking error; it cannot silently inherit the disabled unknown-script profile.
     */
    public static Validation validateEnabled(Collection<String> enabledRootIds,
                                             Collection<ScriptBoundsProfile> profiles) {
        Objects.requireNonNull(enabledRootIds, "enabledRootIds");
        Validation base = validate(profiles);
        TreeMap<String, ScriptBoundsProfile> byId = new TreeMap<>(base.profiles());
        List<String> errors = new ArrayList<>(base.errors());
        TreeSet<String> enabled = new TreeSet<>();
        for (String raw : enabledRootIds) {
            if (raw == null || raw.isBlank()) {
                errors.add("enabled structure ID is blank");
                continue;
            }
            String id = normalize(raw);
            if (!enabled.add(id)) errors.add("duplicate enabled structure: " + id);
            if (!byId.containsKey(OrigenBoundsCatalogKey.PACK_ID + ":" + id))
                errors.add("enabled structure has no audited bounds profile: " + id);
        }
        return new Validation(byId, errors);
    }

    private static String normalize(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Structure ID is required");
        int namespace = id.lastIndexOf(':');
        return namespace >= 0 ? id.substring(namespace + 1) : id;
    }

    /** Avoids coupling this validator to a particular content-pack implementation. */
    private static final class OrigenBoundsCatalogKey {
        private static final String PACK_ID = "overworld";
    }

    public record Validation(Map<String, ScriptBoundsProfile> profiles, List<String> errors) {
        public Validation {
            profiles = Collections.unmodifiableMap(new TreeMap<>(profiles));
            errors = List.copyOf(errors);
        }
        public boolean valid() { return errors.isEmpty(); }
        public void requireValid() {
            if (!valid()) throw new IllegalStateException("Invalid bounds catalog: " + String.join("; ", errors));
        }
    }
}
