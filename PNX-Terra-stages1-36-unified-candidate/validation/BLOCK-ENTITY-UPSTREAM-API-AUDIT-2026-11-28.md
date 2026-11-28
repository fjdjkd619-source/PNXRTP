# Block Entity integration audit — PowerNukkitX 3.0.6 pinned source

Date: 2026-11-28  
Audited upstream revision: `PowerNukkitX/PowerNukkitX@c57499365737654eca32df008751ef6bca5af142`  
Scope: establish the real PNX BlockEntity lifecycle before implementing Stage 22 intent application. This is a source audit, not a runtime test.

## Confirmed source contracts

### Creation and registration

`org.powernukkitx.blockentity.BlockEntity` exposes:

- `BlockEntity.createBlockEntity(String type, IChunk chunk, CompoundTag nbt, Object... args)`.
- The factory resolves the implementation through `Registries.BLOCKENTITY.get(type)`.
- The base constructor binds the supplied chunk and its level, reads `id/x/y/z`, calls `initBlockEntity()` (which calls `loadNBT()`), then registers itself using both `chunk.addBlockEntity(this)` and `level.addBlockEntity(this)`.
- `getNbt()` exposes the entity's CompoundTag; `saveNBT()` refreshes the standard identity and position fields.
- `close()` removes the entity from its chunk and level registries.

The constructor is therefore not a detached object factory. Calling it mutates live PNX registries immediately.

### Chunk and level operations

The pinned `IChunk` contract exposes:

- `addBlockEntity(BlockEntity)`
- `removeBlockEntity(BlockEntity)`
- `getBlockEntities()`
- `getTile(int localX, int y, int localZ)`

The pinned `Chunk.addBlockEntity` implementation updates the tile maps and marks an initialized chunk changed. It also closes an existing BlockEntity occupying the same position when the new one is inserted. A transactional adapter must preflight collisions and must not rely on `addBlockEntity` to perform a safe replacement.

The pinned `Level` exposes `getBlockEntityIfLoaded(Vector3)`, and `getBlockEntity(BlockVector3)` uses the general chunk lookup path. The current adapter's loaded-only chunk lookup remains the safer boundary for generation workers.

### Registry and NBT state

`Registries.BLOCKENTITY` is the authoritative registry. The type must be resolved and validated before construction. The `CompoundTag` passed to the constructor must include the expected `id`, `x`, `y`, and `z` values; constructor coordinates are read directly from those fields.

## Required Stage 22 implementation sequence

1. Decode every journal payload into a fresh `CompoundTag` using a codec verified against this pinned PNX revision. The codec is deliberately not guessed in this audit.
2. Validate every intent before creating anything: target chunk coordinates, world-height bounds, registered type, NBT ID, NBT coordinates, payload limits, and absence of an existing tile at each target position.
3. Apply mutations on the PNX-owned/server execution context, not an arbitrary journal or generation worker. The constructor immediately mutates chunk and level registries.
4. Create each entity with `BlockEntity.createBlockEntity(type, chunk, tag)`; verify the returned type, coordinates, chunk, and level.
5. If any creation fails, close every entity created by this batch before propagating failure. Existing entities must never be silently replaced.
6. Only after the entire batch succeeds may the target chunk be durably saved. The existing finalization coordinator must append the applied-digest acknowledgement only after that durable save succeeds.

## Explicitly unresolved

- Exact binary NBT codec API and canonical encoding for the journal payload.
- Whether Terra's BlockEntity `applyState(String)` represents PNX NBT, a block-state string, or another adapter-specific representation. It must not be interpreted as raw NBT without confirming the Terra contract.
- The scheduling bridge is represented by `PnxLevelExecutor.forLevel(level)`, which delegates to `Level.getScheduler().scheduleTask(Runnable)`. PNX calls that scheduler's `mainThreadHeartbeat` from `Level.doTick`. `PnxCrossChunkFinalizer` now uses it as the intent executor while keeping block projection on the compute pool; real level-thread behavior still needs integration testing.
- Atomic rollback behavior under concurrent PNX chunk mutation; preflight plus server-thread confinement is required, and runtime verification is still outstanding.
- End-to-end load/save/restart behavior for representative chests, signs, skulls, and other supported BlockEntity types.

## Safety decision

Keep `PNXBlockEntityDelegate.update/applyState` and `ChunkFinalizationCoordinator.ChunkHandle.applyBlockEntityIntents` fail-closed until the codec, execution context, collision handling, rollback, and durable-recovery tests are implemented. The current refusal is safer than partially creating or silently replacing inventories/sign data during world generation.

## Source files inspected

- `src/main/java/org/powernukkitx/blockentity/BlockEntity.java`
- `src/main/java/org/powernukkitx/block/BlockEntityHolder.java`
- `src/main/java/org/powernukkitx/registry/Registries.java`
- `src/main/java/org/powernukkitx/level/format/IChunk.java`
- `src/main/java/org/powernukkitx/level/format/Chunk.java`
- `src/main/java/org/powernukkitx/level/Level.java`

Terra API semantics were also checked at `PolyhedralDev/Terra@05ddc8b8a66e17412670647cce78ef837b091256`: `BlockEntity.applyState(String)` is a default no-op with no documented NBT serialization contract, so it must not be interpreted as raw NBT.

All PNX observations above are tied to the exact pinned commit listed at the top, not to an unpinned current branch.
