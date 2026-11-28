# Block Entity integration audit — PowerNukkitX 3.0.6 pinned source

Date: 2026-11-28  
Audited upstream revision: `PowerNukkitX/PowerNukkitX@c57499365737654eca32df008751ef6bca5af142`

## Confirmed source contracts

- `BlockEntity.createBlockEntity(String, IChunk, CompoundTag, Object...)` resolves the class through `Registries.BLOCKENTITY`.
- The BlockEntity constructor reads `id/x/y/z`, calls `initBlockEntity()/loadNBT()`, then immediately registers itself in both the chunk and level.
- `BlockEntity.close()` removes the object from both registries. `getNbt()` and `saveNBT()` expose/update its NBT state.
- `IChunk` exposes `addBlockEntity`, `removeBlockEntity`, `getBlockEntities`, and `getTile(localX,y,localZ)`.
- `Chunk.addBlockEntity` marks initialized chunks changed, but also closes an existing tile at the same coordinate. A transactional adapter must preflight collisions and must not rely on implicit replacement.
- `Registries.BLOCKENTITY` is the authoritative type registry. The constructor requires valid identity and position tags.
- The pinned Level has a loaded-only BlockEntity lookup, while the adapter should continue using its existing loaded-only chunk boundary.

## Required Stage 22 sequence

1. Decode every journal payload into a fresh `CompoundTag` using an NBT codec verified against this exact PNX revision.
2. Prevalidate all intents: chunk coordinates, world height, registered type, NBT ID/coordinates, payload limits, and absence of an existing tile at every target.
3. Apply on the PNX-owned/server execution context. The constructor mutates live chunk and level registries immediately.
4. Create with `BlockEntity.createBlockEntity(type, chunk, tag)`; verify returned type, coordinates, chunk, and level.
5. On any failure, close every object created by the batch. Never silently replace an existing BlockEntity.
6. Durably save the target chunk; only then append the applied-digest acknowledgement.

## Still unresolved

- Exact binary NBT codec API and canonical journal payload encoding.
- Meaning of Terra's `BlockEntity.applyState(String)`; it must not be assumed to be raw NBT.
- Correct bridge from finalization executor to the PNX server-owned execution context.
- Runtime rollback behavior under concurrent chunk mutation.
- End-to-end save/load/restart tests for chests, signs, skulls, and other supported types.

## Safety decision

Keep `PNXBlockEntityDelegate.update/applyState` and `ChunkFinalizationCoordinator.ChunkHandle.applyBlockEntityIntents` fail-closed until codec, execution context, collision handling, rollback, and durable-recovery tests are implemented. The current refusal is safer than partial creation or silent replacement of inventories/sign data.

This is a pinned-source audit, not a runtime test or a completed BlockEntity implementation.
