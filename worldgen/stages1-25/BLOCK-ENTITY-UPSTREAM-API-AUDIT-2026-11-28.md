# Block Entity integration audit — PowerNukkitX 3.0.6 pinned source

Date: 2026-11-28  
Audited PNX revision: `PowerNukkitX/PowerNukkitX@c57499365737654eca32df008751ef6bca5af142`  
Audited Terra API revision: `PolyhedralDev/Terra@05ddc8b8a66e17412670647cce78ef837b091256`

## Confirmed PNX lifecycle

- `BlockEntity.createBlockEntity(String, IChunk, CompoundTag, Object...)` resolves the class through `Registries.BLOCKENTITY`.
- The BlockEntity constructor reads `id/x/y/z`, calls `initBlockEntity()/loadNBT()`, then immediately registers itself in both the chunk and level.
- `BlockEntity.close()` removes the object from both registries. `getNbt()` and `saveNBT()` expose/update its NBT state.
- `IChunk` exposes `addBlockEntity`, `removeBlockEntity`, `getBlockEntities`, and `getTile(localX,y,localZ)`.
- `Chunk.addBlockEntity` marks initialized chunks changed, but closes an existing tile at the same coordinate. A transactional adapter must preflight collisions and never rely on implicit replacement.
- `Registries.BLOCKENTITY` is the authoritative type registry. Constructor input must contain valid identity and position tags.

## Confirmed NBT codec

The exact PNX chunk serializer uses Cloudburst NBT little-endian streams:
- `NbtUtils.createWriterLE(...)` + `NBTOutputStream.writeTag(compound.toNetwork())`
- `NbtUtils.createReaderLE(...)` + `CompoundTag.fromNetwork((NbtMap) readTag())`

Added `PnxBlockEntityNbtCodec` using this exact format. It caps payloads at 1 MiB, fixes `id/x/y/z` when creating an intent, and rejects type/coordinate drift when decoding. A narrow Java 21 contract-stub test passes; real upstream dependency round-trip/build verification remains pending.

## Execution context

PNX `Level` owns a `ServerScheduler`; `Level.doTick()` calls its `mainThreadHeartbeat`. Added `PnxLevelExecutor.forLevel(level)`, which schedules work through `level.getScheduler().scheduleTask(Runnable)`. The helper is not yet wired into the finalizer, and actual level-thread behavior still needs integration testing.

## Required Stage 22 sequence

1. Decode every journal payload with the pinned little-endian codec.
2. Prevalidate all intents: target chunk coordinates, world height, registered type, NBT ID/coordinates, payload limits, and absence of an existing tile at every target.
3. Apply on the owning PNX level scheduler context. The constructor mutates live chunk and level registries immediately.
4. Create with `BlockEntity.createBlockEntity(type, chunk, tag)`; verify returned type, coordinates, chunk, and level.
5. On any failure, close every object created by the batch. Never silently replace an existing BlockEntity.
6. Durably save the target chunk; only then append the applied-digest acknowledgement.

## Still unresolved

- Wiring the codec into intent capture and `PnxChunkHandle.applyBlockEntityIntents`.
- Wiring the level scheduler executor into asynchronous finalization without blocking generation workers.
- Batch rollback under concurrent chunk mutation.
- End-to-end save/load/restart tests for chests, signs, skulls, and other supported types.

Terra's `BlockEntity.applyState(String)` is a default no-op in the pinned API and has no documented NBT serialization contract. It must not be interpreted as raw NBT.

## Safety decision

Keep `PNXBlockEntityDelegate.update/applyState` and `ChunkFinalizationCoordinator.ChunkHandle.applyBlockEntityIntents` fail-closed until capture, finalizer wiring, collision handling, rollback, and durable-recovery tests are implemented. The current refusal is safer than partial creation or silent replacement of inventories/sign data.

This is a pinned-source audit plus codec/executor groundwork, not a completed BlockEntity implementation or runtime certification.
