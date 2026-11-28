# Stage 22 — Terra BlockEntity capture gap audit

Date: 2026-11-28  
Target refs:
- Terra `05ddc8b8a66e17412670647cce78ef837b091256`
- PNX-Terra `74318b4a1eae95322550f12d5eb8a76d92a93731`
- PNX `c57499365737654eca32df008751ef6bca5af142`

## Result

**The remaining Stage 22 blocker is not the PNX-side intent applier. It is the absence of a supported Terra-to-adapter BlockEntity capture contract.** The r7 candidate has a PNX NBT codec, a preflight/idempotent PNX intent applier, rollback handling, and Level-scheduler dispatch. Those components can consume a durable `BlockEntityIntent`, but ordinary Terra structure placement does not currently produce such intents.

## Exact upstream API findings

At the pinned Terra ref:

1. `WritableWorld` exposes `setBlockState(...)` and `spawnEntity(...)`; it has no BlockEntity creation/write method.
2. `ReadableWorld` exposes `getBlockEntity(...)`, but that is read-only access.
3. Terra's `BlockEntity` interface exposes `update(boolean)`, `applyState(String)`, position, and block state. It exposes no canonical type ID, NBT payload, serializer, or creation API.
4. The pinned `SpongeStructure.generate(...)` iterates its block palette and invokes only `world.setBlockState(...)`. It does not emit a BlockEntity payload or a durable intent.
5. The Terra structure-script function delegates structure generation through a buffered `WritableWorld`; it does not add a BlockEntity channel.

Pinned source links:
- https://github.com/PolyhedralDev/Terra/blob/05ddc8b8a66e17412670647cce78ef837b091256/common/api/src/main/java/com/dfsek/terra/api/world/WritableWorld.java
- https://github.com/PolyhedralDev/Terra/blob/05ddc8b8a66e17412670647cce78ef837b091256/common/api/src/main/java/com/dfsek/terra/api/world/ReadableWorld.java
- https://github.com/PolyhedralDev/Terra/blob/05ddc8b8a66e17412670647cce78ef837b091256/common/api/src/main/java/com/dfsek/terra/api/block/entity/BlockEntity.java
- https://github.com/PolyhedralDev/Terra/blob/05ddc8b8a66e17412670647cce78ef837b091256/common/addons/structure-sponge-loader/src/main/java/com/dfsek/terra/addons/sponge/SpongeStructure.java
- https://github.com/PolyhedralDev/Terra/blob/05ddc8b8a66e17412670647cce78ef837b091256/common/addons/structure-terrascript-loader/src/main/java/com/dfsek/terra/addons/terrascript/script/functions/StructureFunction.java

At the pinned PNX-Terra ref:

- `PNXServerWorld.setBlockState(...)` passes the Terra block state to `BlockManager.setBlockStateAt(...)`; it has no journal BlockEntity capture path.
- `PNXServerWorld.getBlockEntity(...)` obtains a PNX BlockEntity but the baseline implementation returns `null`.
- r7's read-only wrapper fixes the read path for already-existing entities; it does not create or serialize an entity intent.

Pinned source:
- https://github.com/PowerNukkitX-Bundle/PNX-Terra/blob/74318b4a1eae95322550f12d5eb8a76d92a93731/src/main/java/org/powernukkitx/terra/delegate/PNXServerWorld.java

## Required implementation direction

Do not infer chest contents, signs, spawners, or other tile data from a block identifier or block state. That would silently lose data and is unsafe for replay.

Implement a PNX-Terra-specific capture extension at the structure-input/placement boundary:

1. Define a typed immutable BlockEntity source record: absolute target coordinates, canonical PNX save ID, and validated serialized NBT payload. Validate that the record's coordinates and block type agree with the corresponding block projection.
2. For supported structure formats, parse tile-entity records from the source structure data before placement. The Sponge structure generator at the pinned Terra ref only writes its block palette; therefore either extend/replace its loader with a format-aware implementation or add a separate PNX-Terra structure importer. Do not claim existing SpongeStructure already preserves tile entities.
3. Route records into the same root placement transaction and WAL as block writes. Do not instantiate or mutate live PNX BlockEntities from Terra's asynchronous generation thread.
4. At finalization, apply the existing r7 `PnxBlockEntityIntentApplier` on the owning Level scheduler, then perform durable chunk save, and only then acknowledge the journal. Keep unknown IDs, malformed NBT, coordinate mismatches, unsupported entity types, and collisions fail-closed.
5. Add replay/crash tests covering empty and populated containers, signs, spawners, duplicate intent replay, conflicting existing entities, constructor failure rollback, save failure before acknowledgement, and restart recovery. Test only the explicitly supported type whitelist.

## Acceptance boundary

The r7 PNX-side NBT/applier code is groundwork, not proof of end-to-end BlockEntity support. Stage 22 remains blocked until at least one real Terra structure source carrying tile-entity data is captured, journaled, applied, durably saved, and verified after restart on the pinned integration build. Until then, structures requiring BlockEntities must be rejected or documented as unsupported; no partially initialized entities should be placed.
