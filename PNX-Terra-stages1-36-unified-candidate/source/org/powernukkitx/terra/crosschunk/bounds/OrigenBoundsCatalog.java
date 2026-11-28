package org.powernukkitx.terra.crosschunk.bounds;

import java.util.Map;
import java.util.Optional;

/** Only profiles whose complete write/read closure was bounded from the pinned Origen pack. */
public final class OrigenBoundsCatalog {
    public static final String PACK_ID = "origen";
    private static final Map<String, ScriptBoundsProfile> PROFILES = Map.of(
            "waterfall", new ScriptBoundsProfile(PACK_ID, "waterfall",
                    new Bounds3i(-5, -50, -5, 5, 2, 5),
                    new Bounds3i(-5, -50, -5, 5, 2, 5),
                    512, 128, 0, java.util.List.of(),
                    "waterfall.tesf: fixed width=1, length=4, inwardLength=-3, downwardsMaxSearch=50; four cardinal wall orientations; nested calls absent"),
            "dripstone_column", new ScriptBoundsProfile(PACK_ID, "dripstone_column",
                    new Bounds3i(-10, -10, -10, 10, 100, 10),
                    new Bounds3i(-10, -10, -10, 10, 100, 10),
                    100_000, 100_000, 0, java.util.List.of("is_dripstone_column_replaceable"),
                    "dripstone_column.tesf: maxRadius=10, maxDepth=10, maxHeight=100; mask target is a read-only script whose sole read is getBlock(0,0,0)"),
            "mangrove_tree_procedural", new ScriptBoundsProfile(PACK_ID, "mangrove_tree_procedural",
                    new Bounds3i(-30, -30, -30, 30, 28, 30),
                    new Bounds3i(-31, -30, -31, 31, 28, 31),
                    20_000, 10_000, 3,
                    java.util.List.of("mangrove_leaves_clump", "propagule", "place_vines", "is_leaf", "spore_blossom", "bee_nest"),
                    "mangrove_tree_procedural.tesf: root path length<=30 gives |x|,|z|<=30; branch/leaf/vine closures fit inside that horizontal envelope; y lower bound conservatively -30, top branch/leaf closure envelope <=28; root neighbor reads add one block; bee_nest spawns three entities"));

    /**
     * Runtime-enforced fallback for scripts not yet statically certified. This is not a claim
     * that the script's theoretical bounds are known: every read/write is checked against this
     * hard envelope, and a single escape aborts the whole root placement. The 32-block horizontal
     * limit matches the current scheduler horizon and covers the already audited 31-block read
     * halo of the largest verified Origen root. Entity spawning remains disabled until it can be
     * journaled and committed atomically.
     */
    private static final int FALLBACK_HORIZONTAL_REACH = 32;
    private static final ScriptBoundsProfile BOUNDED_FALLBACK = new ScriptBoundsProfile(
            PACK_ID, "<bounded-runtime-fallback>",
            new Bounds3i(-FALLBACK_HORIZONTAL_REACH, -128, -FALLBACK_HORIZONTAL_REACH,
                    FALLBACK_HORIZONTAL_REACH, 128, FALLBACK_HORIZONTAL_REACH),
            new Bounds3i(-FALLBACK_HORIZONTAL_REACH, -128, -FALLBACK_HORIZONTAL_REACH,
                    FALLBACK_HORIZONTAL_REACH, 128, FALLBACK_HORIZONTAL_REACH),
            65_536, 65_536, 0, java.util.List.of("*"),
            "Runtime-enforced fallback envelope: x/z +/-32, y +/-128; 65,536 writes and reads; no entities; any out-of-envelope operation aborts placement");

    private OrigenBoundsCatalog() {}

    /** Returns only statically audited profiles. */
    public static Optional<ScriptBoundsProfile> find(String structureId) {
        return Optional.ofNullable(PROFILES.get(structureId));
    }

    /**
     * Resolve an audited profile or a strict runtime-bounded fallback. Unknown scripts are no
     * longer silently disabled, but they are never allowed to write/read outside the hard pack
     * envelope or spawn entities.
     */
    public static ScriptBoundsProfile resolveRuntimeProfile(String structureId) {
        if (structureId == null || structureId.isBlank())
            throw new IllegalArgumentException("Structure ID is required");
        String normalized = structureId.startsWith(PACK_ID + ":")
                ? structureId.substring(PACK_ID.length() + 1) : structureId;
        return PROFILES.getOrDefault(normalized, BOUNDED_FALLBACK);
    }

    public static ScriptBoundsProfile boundedFallbackProfile() { return BOUNDED_FALLBACK; }

    public static Map<String, ScriptBoundsProfile> allVerified() { return PROFILES; }

    /** Maximum horizontal write/read reach used to prefetch all possible source anchors. */
    public static int maxHorizontalReach() {
        return Math.max(FALLBACK_HORIZONTAL_REACH, PROFILES.values().stream()
                .filter(profile -> profile.maxEntitySpawns() == 0)
                .mapToInt(profile -> Math.max(
                        Math.max(Math.abs(profile.writeBounds().minX()), Math.abs(profile.writeBounds().maxX())),
                        Math.max(Math.abs(profile.writeBounds().minZ()), Math.abs(profile.writeBounds().maxZ()))))
                .max().orElse(0));
    }

    /** Unknown scripts remain unbounded and must not be scheduled for finalization. */
    public static ScriptBoundsProfile requireVerified(String structureId) {
        return find(structureId).orElseThrow(() ->
                new IllegalStateException("No verified Origen runtime envelope for structure: " + structureId));
    }
}
