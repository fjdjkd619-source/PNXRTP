# Stage 48 — Terra structure bounds coverage audit (R15)

**Audit status: incomplete; release gate remains blocked.** This report is a coverage inventory, not proof that the remaining scripts are safe.

## Pinned input

- Config pack: `PolyhedralDev/TerraOverworldConfig`
- Config pack commit: `445848bc9f9f28fab1d760f01f9a552d93944681`
- Source archive label in the generated registry: `origen-v2.2.0.zip`
- Registry source head: `228426bb192a8d6029952aae1a77008dea5f3cda`
- Candidate branch: `R15`

## Measured coverage

| Measure | Count |
|---|---:|
| TerraScript files in registry | 159 |
| Scripts with statically certified bounds in registry | 0 |
| Scripts marked UNKNOWN | 159 |
| Scripts referenced as feature roots | 73 |
| Feature roots matched by the current runtime catalog | 3 |
| Feature roots without a runtime bounds profile | 70 |
| Scripts with dynamic nested targets | 10 |
| Runtime catalog entries after excluding wildcard-dependent profiles | 12 |

The 12 runtime entries are not equivalent to 12 enabled feature roots. Only three names overlap the 73 root references. A registry entry marked UNKNOWN is not upgraded to VERIFIED merely because a similarly named profile exists in Java.

## Root references still lacking a verified runtime profile

```text
boulder
boulder_textured
dripstone_caves_boulder
granite_boulder
sandstone_boulder
small_boulder
small_boulder_textured
small_granite_boulder
small_sandstone_boulder
amethyst_geode
big_boulder
ceiling_icicle
del_floating_blocks
iceberg
large_ceiling_pointed_dripstone
lava_column
red_rock
small_ceiling_pointed_dripstone
small_floor_pointed_dripstone
dam_to_floor
farm_edge
farm_water
andesite_slabs
diorite_slabs
granite_slabs
iceberg_snow_layers
red_sand_slabs
sand_slabs
snow_layers
stone_slabs
big_dripleaf
jungle_bush
oak_bush
spruce_bush
cactus
beetroot_patch
carrot_patch
melon_patch
potato_patch
pumpkin_patch
sweet_berry_bush_patch
wheat_patch
lilac
peony
pink_petals
rose_bush
sunflower
glow_lichen
large_mixed_mushroom_procedural
small_brown_mushroom_disk
small_dripleaf
sugar_cane
place_great_azalea_tree_underground
cherry_blossom_tree_procedural
dead_large_swamp_tree_procedural
dead_tree_procedural
eucalyptus_tree_procedural
giant_redwood
large_swamp_tree_procedural
mesquite_tree_procedural
oak_tree_procedural
palm_tree_procedural
spruce_tree_procedural
submerged_dead_tree_procedural
crossing_vines
crossing_vines_frozen
crossing_vines_uncolored
glow_berries
large_ceiling_vines
tree_vines
```

## Dynamic nested target scripts requiring special treatment

```text
dripstone_column
large_mixed_mushroom_procedural
cherry_blossom_tree_procedural
eucalyptus_tree_procedural
large_dark_oak_tree_procedural
large_oak_tree_procedural
large_oak_tree_procedural_2
large_swamp_tree_procedural
mangrove_tree_procedural
mesquite_tree_procedural
```

Dynamic nested targets cannot be covered by a hand-written list of static child IDs unless the TerraScript execution is instrumented to enforce the same root guard and bounds envelope for every nested call.

## R15 fail-closed changes

- Unknown roots are rejected in `PNXServerWorld.beginPlacement` before a placement transaction or journal ticket is opened.
- The rejection emits a warning with stage, feature ID, structure ID, world key, chunk anchor and origin coordinates.
- `OrigenBoundsCatalog.resolveRuntimeProfile` also resolves unknown IDs to a zero-budget profile, so any caller bypassing root admission still cannot perform reads, writes or entity spawns.
- Three previous profiles using wildcard nested dependencies were removed from the runtime catalog pending an explicit dependency audit.
- `BoundsCatalogValidator.validateEnabled` checks that every supplied enabled root has an audited profile and rejects wildcard dependencies in audited profiles.

## Remaining work before Stage 48 can pass

1. Recover the exact enabled feature-root set from the pinned ConfigPack and distinguish feature roots from helper scripts.
2. Inspect each enabled TerraScript and its transitive static and dynamic calls; derive conservative write AABB, read halo, vertical range, maximum write/read counts and entity budget.
3. Verify rotations and coordinate transforms against the actual Terra runtime semantics.
4. Promote only scripts with source-backed evidence into the runtime catalog; keep every other root rejected.
5. Build the Terra↔PNX block identifier/property alias manifest from both pinned registries and validate every used block state and property value.
6. Wire catalog validation to pack startup so a pack cannot start with an enabled root lacking a profile.
7. Add world-aware diagnostics to every generation stage and confirm failures abort the placement transaction without durable partial writes.

## Acceptance decision

**NOT PASSED.** The current implementation makes unknown roots fail closed, but it does not yet provide bounds for all enabled roots, a complete alias manifest, or full diagnostic coverage. Do not treat the current TerraOverworldConfig pack as production-ready under the cross-chunk guard until the remaining work is complete.
