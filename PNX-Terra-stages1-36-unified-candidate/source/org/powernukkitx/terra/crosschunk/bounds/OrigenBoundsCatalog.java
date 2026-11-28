package org.powernukkitx.terra.crosschunk.bounds;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runtime envelopes for the pinned TerraOverworldConfig pack. Profiles are conservative
 * root-placement bounds; nested TerraScript calls share the same root guard.
 */
public final class OrigenBoundsCatalog {
    public static final String PACK_ID = "overworld";
    public static final String PACK_SOURCE = "PolyhedralDev/TerraOverworldConfig@445848bc9f9f28fab1d760f01f9a552d93944681";

    private static final Map<String, ScriptBoundsProfile> PROFILES = Map.ofEntries(
            Map.entry("waterfall", profile("waterfall",
                    -5, -50, -5, 5, 2, 5, 512, 128, 0, List.of(),
                    "Pinned pack waterfall.tesf: fixed width=1, length=4, inwardLength=-3, downwardsMaxSearch=50; four cardinal wall orientations; nested calls absent")),
            Map.entry("dripstone_column", profile("dripstone_column",
                    -10, -10, -10, 10, 100, 10, 100_000, 100_000, 0,
                    List.of("is_dripstone_column_replaceable"),
                    "Pinned pack dripstone_column.tesf: maxRadius=10, maxDepth=10, maxHeight=100; replaceability helper reads only the origin")),
            Map.entry("mangrove_tree_procedural", profile("mangrove_tree_procedural",
                    -30, -30, -30, 30, 28, 30, 20_000, 10_000, 3,
                    List.of("mangrove_leaves_clump", "propagule", "place_vines", "is_leaf", "spore_blossom", "bee_nest"),
                    "Pinned pack mangrove_tree_procedural.tesf: root path <=30; branch/leaf/vine closure within +/-30; one-block read halo; bee_nest spawns three entities")),
            Map.entry("giant_sequoia_tree_procedural", profile("giant_sequoia_tree_procedural",
                    -24, -12, -24, 24, 80, 24, 30_000, 12_000, 0, List.of(),
                    "Pinned pack giant_sequoia_tree_procedural.tesf: trunkHeight 55..64; side branches and leaf clusters remain within the conservative +/-24 horizontal and 80-block vertical envelope; rootLength <=8")),
            Map.entry("great_azalea_tree", profile("great_azalea_tree",
                    -32, -8, -32, 32, 40, 32, 50_000, 20_000, 0, List.of("azalea_leaves_clump"),
                    "Pinned pack great_azalea_tree.tesf: trunk branches <=20, end branches <=11, leaf clump radius 4; read halo 1")),
            Map.entry("weeping_willow_tree", profile("weeping_willow_tree",
                    -32, -48, -32, 32, 48, 32, 80_000, 30_000, 0,
                    List.of("weeping_willow_root_stub", "willow_pale_oak_leaves_clump"),
                    "Pinned pack weeping_willow_tree.tesf: rootMaxLength 26 with decreasing radius <=7.5; descending roots and upper branch/end-branch/leaf closure fit within x/z +/-32 and y -48..48")),
            Map.entry("stone_spike", profile("stone_spike",
                    -24, -64, -24, 24, 64, 24, 60_000, 15_000, 0, List.of(),
                    "Pinned pack stone_spike.tesf: length <=50, radius <=7, dy=5; normalized horizontal displacement plus sphere radius <24")),
            Map.entry("large_iceberg", profile("large_iceberg",
                    -24, -52, -24, 24, 52, 24, 30_000, 10_000, 0, List.of(),
                    "Pinned pack large_iceberg.tesf: radius <14, warp=5, x/z random padding <=3, y warp multiplier <=6")),
            Map.entry("mob_room", profile("mob_room",
                    -20, -12, -20, 20, 120, 20, 50_000, 25_000, 0,
                    List.of("is_rock", "mob_room_east_sc", "mob_room_west_sc", "mob_room_south_sc", "mob_room_north_sc"),
                    "Pinned pack mob_room.tesf: searchRadius=3, vertical search <=100, schematic offsets <=7 and local extents <=6")),
            Map.entry("lava_tunnel", profile("lava_tunnel",
                    -24, -1, -24, 24, 252, 24, 150_000, 200_000, 0,
                    List.of("is_lava_placeable", "hornito"),
                    "Pinned pack lava_tunnel.tesf: vertical search <=250; tunnel radius <=8, helix amplitude <=7, lateral noise amplitude 2.25; one-block neighbour read halo")),
            Map.entry("large_underground_water_lake", profile("large_underground_water_lake",
                    -12, -16, -12, 12, 16, 12, 10_000, 8_000, 0, List.of(),
                    "Pinned pack large_underground_water_lake.tesf: radius <=5.5 plus bounded warp; vertical squish >=1")),
            Map.entry("large_underground_lava_lake", profile("large_underground_lava_lake",
                    -16, -20, -16, 16, 20, 16, 15_000, 10_000, 0, List.of(),
                    "Pinned pack large_underground_lava_lake.tesf: radius <=8 plus bounded warp; vertical squish >=1"))
    );

    /**
     * Fail-closed profile for a script absent from the audited manifest.
     *
     * A zero operation budget is intentional: the translated origin is representable, but the
     * first read, write, or entity spawn is rejected by PlacementRuntimeGuard. Unknown scripts
     * must never inherit a permissive generic envelope.
     */
    private static final ScriptBoundsProfile DISABLED_UNVERIFIED = new ScriptBoundsProfile(
            PACK_ID, "<unverified-disabled>",
            new Bounds3i(0, 0, 0, 0, 0, 0),
            new Bounds3i(0, 0, 0, 0, 0, 0),
            0, 0, 0, List.of(),
            "FAIL-CLOSED: no audited manifest entry; all reads, writes and entity spawns are denied");

    private OrigenBoundsCatalog() {}

    private static ScriptBoundsProfile profile(String id, int minX, int minY, int minZ,
                                               int maxX, int maxY, int maxZ,
                                               int maxWrites, int maxReads, int maxEntities,
                                               List<String> dependencies, String evidence) {
        Bounds3i writes = new Bounds3i(minX, minY, minZ, maxX, maxY, maxZ);
        Bounds3i reads = new Bounds3i(minX - 1, minY - 2, minZ - 1,
                maxX + 1, maxY + 2, maxZ + 1);
        return new ScriptBoundsProfile(PACK_ID, id, writes, reads,
                maxWrites, maxReads, maxEntities, dependencies, PACK_SOURCE + "; " + evidence);
    }

    private static String normalize(String structureId) {
        if (structureId == null || structureId.isBlank())
            throw new IllegalArgumentException("Structure ID is required");
        int namespace = structureId.lastIndexOf(':');
        return namespace >= 0 ? structureId.substring(namespace + 1) : structureId;
    }

    /** Returns only statically audited profiles. */
    public static Optional<ScriptBoundsProfile> find(String structureId) {
        return Optional.ofNullable(PROFILES.get(normalize(structureId)));
    }

    /** Resolve an audited profile; unknown scripts receive a deny-all profile. */
    public static ScriptBoundsProfile resolveRuntimeProfile(String structureId) {
        return PROFILES.getOrDefault(normalize(structureId), DISABLED_UNVERIFIED);
    }

    /** Compatibility name retained for callers; the returned profile denies every operation. */
    public static ScriptBoundsProfile boundedFallbackProfile() { return DISABLED_UNVERIFIED; }
    public static Map<String, ScriptBoundsProfile> allVerified() { return PROFILES; }

    /** Maximum horizontal read/write reach used to prefetch every possible source anchor. */
    public static int maxHorizontalReach() {
        return PROFILES.values().stream()
                .mapToInt(profile -> Math.max(
                        Math.max(Math.max(Math.abs(profile.writeBounds().minX()), Math.abs(profile.writeBounds().maxX())),
                                Math.max(Math.abs(profile.writeBounds().minZ()), Math.abs(profile.writeBounds().maxZ()))),
                        Math.max(Math.max(Math.abs(profile.readBounds().minX()), Math.abs(profile.readBounds().maxX())),
                                Math.max(Math.abs(profile.readBounds().minZ()), Math.abs(profile.readBounds().maxZ())))))
                .max().orElse(0));
    }

    /** Requires a statically audited profile; never returns the deny-all fallback. */
    public static ScriptBoundsProfile requireVerified(String structureId) {
        return find(structureId).orElseThrow(() ->
                new IllegalStateException("No verified Terra bounds profile for structure: " + structureId));
    }
}
