# Block Entity integration audit — PowerNukkitX 3.0.6 pinned source

Date: 2026-11-28  
Audited PNX revision: `PowerNukkitX/PowerNukkitX@c57499365737654eca32df008751ef6bca5af142`  
Audited Terra API revision: `PolyhedralDev/Terra@05ddc8b8a66e17412670647cce78ef837b091256`

## Confirmed PNX lifecycle

- `BlockEntity.createBlockEntity(String, IChunk, CompoundTag, Object...)` resolves the class through `Registries.BLOCKENTITY`.
- The BlockEntity constructor reads `id/x/y/z`, calls `initBlockEntity()/loadNBT()`, then immediately registers itself in both the chunk and level.
- `BlockEntity.close()` removes the object from both registries.
- `IChunk` exposes `getBlockEntities()`, `getTile(localX,y,localZ)`, and add/remove methods.
- `Chunk.addBlockEntity` closes an existing tile at the same coordinate. The adapter therefore rejects collisions before construction.
- The PNX factory may swallow constructor exceptions and return null after the base constructor has already registered the object. The applier snapshots the chunk's BlockEntity registry and closes every newly registered object on failure, including objects whose constructor never returned to the caller.

## NBT codec and execution

The pinned LevelDB chunk serializer uses Cloudburst NBT little-endian streams:
- `NbtUtils.createWriterLE(...)` + `NBTOutputStream.writeTag(compound.toNetwork())`
- `NbtUtils.createReaderLE(...)` + `CompoundTag.fromNetwork((NbtMap) readTag())`

`PnxBlockEntityNbtCodec` uses that format, caps payloads at 1 MiB, fixes `id/x/y/z` when creating an intent, and rejects type/coordinate drift.

PNX `Level` owns a `ServerScheduler`; `Level.doTick()` calls its `mainThreadHeartbeat`. `PnxLevelExecutor.forLevel(level)` schedules through `level.getScheduler().scheduleTask(Runnable)`. The finalizer now keeps block projection on the compute pool and dispatches BE/physics intents through this Level scheduler.

## Current Stage 22 implementation

- Preflight validates chunk coordinates, layer, world height, registered type, canonical save ID, NBT identity/coordinates, and existing tile state.
- A byte-identical persisted BlockEntity is accepted as replay-safe; a conflicting type or payload is rejected.
- New objects are created only after the complete batch passes preflight.
- Returned objects are checked for type, chunk, level, and coordinates; their serialized NBT must match the durable intent.
- If any creation fails, all newly registered objects are closed before failure propagates.
- The coordinator applies block projections on its compute executor, then applies BlockEntity and physics intents on the PNX Level scheduler before producing the prepared finalization object.

Three Stage 22 patches pass `git apply --check` and application against the reconstructed post-r5 adapter baseline; all six resulting source files match the r6 overlay. Narrow Java 21 contract-stub tests pass for codec identity/coordinate round-trip, idempotent replay, collision refusal, swallowed constructor-failure rollback, and scheduler dispatch.

## Still unresolved

- Terra structure/populator operations do not yet capture BlockEntity data into `BlockEntityIntent` records. The journal path supports intents, but current production population does not yet supply them.
- Full compilation against the pinned upstream dependencies and runtime execution of the scheduler/finalizer chain.
- Rollback against concurrent mutation from code that bypasses the owning level scheduler.
- End-to-end save/load/restart tests for chests, signs, skulls, and other supported types.

Terra's `BlockEntity.applyState(String)` is a default no-op in the pinned API and has no documented NBT serialization contract. It must not be interpreted as raw NBT.

## Safety decision

Keep `PNXBlockEntityDelegate.update/applyState` fail-closed. The finalizer has a guarded PNX intent applier, but no Terra structure capture path supplies these intents yet; do not enable live BlockEntity generation until that capture path and full durable-recovery tests are implemented.

This is source-level integration plus contract-stub testing, not full upstream build or runtime certification.
