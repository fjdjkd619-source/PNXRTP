package org.powernukkitx.terra.crosschunk.bounds;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Generated from the pinned TerraOverworldConfig inventory. Do not edit manually. */
public final class OrigenBoundsCatalog {
    public static final String PACK_ID = "overworld";
    public static final String PACK_SOURCE = "PolyhedralDev/TerraOverworldConfig@445848bc9f9f28fab1d760f01f9a552d93944681";

    private static final Map<String, ScriptBoundsProfile> PROFILES = Map.of();

    private static final ScriptBoundsProfile BOUNDED_FALLBACK = profile(
            "<unverified-deny-all>", 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0,
            0, 0, 0, List.of(),
            "UNVERIFIED_DENY_ALL; static bounds proof required");

    private OrigenBoundsCatalog() {}

    private static ScriptBoundsProfile profile(String id,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            int readMinX, int readMinY, int readMinZ,
            int readMaxX, int readMaxY, int readMaxZ,
            int maxWrites, int maxReads, int maxEntities,
            List<String> dependencies, String evidence) {
        return new ScriptBoundsProfile(PACK_ID, id,
                new Bounds3i(minX, minY, minZ, maxX, maxY, maxZ),
                new Bounds3i(readMinX, readMinY, readMinZ, readMaxX, readMaxY, readMaxZ),
                maxWrites, maxReads, maxEntities, dependencies, evidence);
    }

    private static String normalize(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Structure ID is required");
        int namespace = id.lastIndexOf(':');
        return namespace >= 0 ? id.substring(namespace + 1) : id;
    }

    public static Optional<ScriptBoundsProfile> find(String structureId) {
        return Optional.ofNullable(PROFILES.get(normalize(structureId)));
    }

    public static ScriptBoundsProfile resolveRuntimeProfile(String structureId) {
        return PROFILES.getOrDefault(normalize(structureId), BOUNDED_FALLBACK);
    }

    public static ScriptBoundsProfile boundedFallbackProfile() { return BOUNDED_FALLBACK; }
    public static Map<String, ScriptBoundsProfile> allVerified() { return PROFILES; }

    public static int maxHorizontalReach() {
        return PROFILES.values().stream().mapToInt(p -> Math.max(
                Math.max(Math.abs(p.writeBounds().minX()), Math.abs(p.writeBounds().maxX())),
                Math.max(Math.abs(p.writeBounds().minZ()), Math.abs(p.writeBounds().maxZ())))).max().orElse(0);
    }

    public static ScriptBoundsProfile requireVerified(String structureId) {
        return find(structureId).orElseThrow(() ->
                new IllegalStateException("No Terra bounds profile for structure: " + structureId));
    }
}