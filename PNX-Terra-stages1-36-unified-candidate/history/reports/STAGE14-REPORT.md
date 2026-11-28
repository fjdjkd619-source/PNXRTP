# Stage 14 — runtime-enforced structure envelopes

## Source and reproducibility
- Pack: `origen-v2.2.0.zip`
- Pinned source HEAD carried forward from Stage 13: `228426bb192a8d6029952aae1a77008dea5f3cda`
- Archive SHA-256: `1ec27c9f185fe4b0017d08d4023686e7cf6ea56d8ba792f580f26c5d34dd4b65`
- Stage 13 ZIP SHA-256: `9aa61dbfba4877eec98dd5b101bef37c64f4dddc0319bf44b8bdea87eeb5620d`

## Implemented
1. Inclusive 3D AABBs with exact quarter-turn corner transformation and integer-safe translation.
2. Separate read and write envelopes.
3. Runtime checks for block reads, block writes, and entity spawn coordinates.
4. Per-placement read/write/entity budgets.
5. A violation changes guard state to FAILED; subsequent operations and `complete()` are rejected.
6. Unknown structure IDs fail closed; no guessed fallback bounds.
7. Horizontal projection to Stage 12 `StructureFootprint` for anchor dependency calculations.
8. Registry v2 retains all 159 scripts and upgrades only three root scripts to `VERIFIED_RUNTIME_GUARDED`.

## Three bounded scripts

| Script | Write AABB | Read AABB | Budgets (writes / reads / entities) |
|---|---|---|---:|
| `waterfall` | `[-5,-50,-5]..[5,2,5]` | `[-5,-50,-5]..[5,1,5]` | 512 / 128 / 0 |
| `dripstone_column` | `[-10,-10,-10]..[10,100,10]` | same | 100000 / 100000 / 0 |
| `mangrove_tree_procedural` | `[-30,-30,-30]..[30,28,30]` | `[-31,-30,-31]..[31,28,31]` | 20000 / 10000 / 3 |

All coordinates are relative to the root placement anchor, after the root rotation has been applied by the guard. Bounds are intentionally conservative. The `mangrove_tree_procedural` closure includes `mangrove_leaves_clump`, `propagule`, `place_vines`, `is_leaf`, `spore_blossom`, and `bee_nest`; the dripstone closure includes the read-only `is_dripstone_column_replaceable` mask.

## Source reasoning
- `waterfall.tesf`: width 1, length 4, inward length -3, downward search 50; the only orientation choices are four cardinal wall directions. The furthest water write is 5 blocks from anchor horizontally and y=-50; floor air reaches y=2.
- `dripstone_column.tesf`: radius expression is bounded by 10, depth by 10 and height by 100. Its nested mask only reads the block at the supplied position. Both search and final column loops stay inside the declared AABB.
- `mangrove_tree_procedural.tesf`: root path length is 30, so each horizontal component is bounded by 30; adjacent root reads add a one-block halo. Trunk/branch/leaf closure reaches no higher than y=28 under conservative inclusive `randomInt` assumptions. Vine positions are horizontally smaller than the root envelope. `bee_nest` creates three entity intents at offset (-2,0,0).

The profiles are not static claims about Terra's internal coordinate rounding. The guard must receive the same resolved integer block coordinates used by the PNX adapter. Fractional Terra coordinates must be quantized identically before both guard and write/read dispatch.

## Validation
- `javac --release 21 -Xlint:all -Werror`: PASS.
- `Stage14BoundsGuardTest`: PASS.
- Tests cover unknown fail-closed lookup, in-bounds operations, out-of-bounds poisoning, entity budget enforcement, rotation/translation, Stage 12 horizontal projection, and observed extents.

## Not yet integrated / not proven
- No PNX-Terra `PNXServerWorld` or `PNXProtoWorld` hook is included.
- No Terra interpreter/AST modification is included; the guard operates at the adapter boundary.
- No integration with Stage 9 transaction coordinator, Stage 11 finalizer, or Stage 8 journal applier is included.
- No actual PNX/Terra Gradle build or server runtime test was performed.
- The 156 remaining scripts stay `UNKNOWN`; their dependent chunks must not be sealed based on this stage.

## Required next integration
1. Add one root-scoped guard to the actual Terra placement context and propagate it through every nested `BufferedWorld`.
2. Hook all reads, writes (both PNX layers), and entity intents at the common PNX-Terra adapter.
3. Keep all writes/entities in the root transaction until the guard passes; violation aborts the root transaction.
4. Convert verified profile AABBs to Stage 12 `StructureFootprint` for the actual feature ID and register them before generation begins.
5. Ensure the finalizer waits for all source anchors intersecting those footprints and for durable journal acknowledgement.
6. Compile against pinned PNX 3.0.6 and Terra 6.6.1-BETA, then test in a disposable world.

No production server files, worlds, player data, configs, or installed plugins were changed.

## Adapter-boundary source audit (exact pinned revisions)
- Terra `WritableWorld` defines integer-coordinate `setBlockState(x,y,z,...)`; its vector overloads floor coordinates before delegation.
- Terra `ReadableWorld` defines integer-coordinate `getBlockState(x,y,z)`; vector reads also floor coordinates.
- Terra `BufferedWorld` adds its offsets and delegates reads/writes/entities to the underlying world. Therefore a root guard installed at the PNX server-world adapter sees absolute coordinates from nested buffered structures.
- Terra's TerraScript documentation explicitly warns that `getBlock()` force-loads chunks. This is a generation-cascade risk, not merely a bounds issue.
- PNX-Terra 1.0.0 `PNXServerWorld.getBlockState()` calls `BlockManager.getBlockAt()`, and its `setBlockState()` also calls `getBlockAt()` for the existing block water heuristic. PNX `BlockManager.getBlockAt()` delegates to `Level.getBlock()` and can load a chunk.
- PNX 3.0.6 `BlockManager` exposes `isCached`, `getBlockIfCachedOrLoaded`, and `getLevel().isChunkLoaded(...)`. The safe adapter must first determine whether the position is in the transaction cache or its chunk is already loaded; it must not treat the unloaded fallback AIR as known AIR.

The guard therefore requires `beforeRead(x,y,z,availableWithoutLoading)` with an explicit availability flag. There is deliberately no three-argument convenience overload that silently assumes the read is safe. For a block write whose water-preservation heuristic reads the old block, the adapter must call `beforeRead` before performing that implicit read. If neither cache nor loaded chunk can answer, the root placement is rejected rather than forcing chunk generation.

Primary source URLs:
- Terra WritableWorld: https://github.com/PolyhedralDev/Terra/blob/05ddc8b8a66e17412670647cce78ef837b091256/common/api/src/main/java/com/dfsek/terra/api/world/WritableWorld.java
- Terra ReadableWorld: https://github.com/PolyhedralDev/Terra/blob/05ddc8b8b66e17412670647cce78ef837b091256/common/api/src/main/java/com/dfsek/terra/api/world/ReadableWorld.java
- Terra BufferedWorld: https://github.com/PolyhedralDev/Terra/blob/05ddc8b8a66e17412670647cce78ef837b091256/common/api/src/main/java/com/dfsek/terra/api/world/BufferedWorld.java
- PNX-Terra PNXServerWorld: https://github.com/PowerNukkitX-Bundle/PNX-Terra/blob/74318b4a1eae95322550f12d5eb8a76d92a93731/src/main/java/org/powernukkitx/terra/delegate/PNXServerWorld.java
- PNX BlockManager: https://github.com/PowerNukkitX/PowerNukkitX/blob/c57499365737654eca32df008751ef6bca5af142/src/main/java/org/powernukkitx/level/generator/object/BlockManager.java
- TerraScript `getBlock()` documentation: https://terra.polydev.org/config/documentation/terrascript/functions.html
