# Upstream source audit — PNX / Terra / PNX-Terra

## Pinned references

- PowerNukkitX original baseline: `c57499365737654eca32df008751ef6bca5af142`
- PowerNukkitX 3.0.6 successor compatibility experiment: `ff86b15d18e3699459c7913eb1ebec16521b7ad0`
- Terra 6.6.1-BETA: `05ddc8b8a66e17412670647cce78ef837b091256`
- PNX-Terra 1.0.0: `74318b4a1eae95322550f12d5eb8a76d92a93731`

## Confirmed upstream defects / seams

1. PNX `Level.getChunk(x,z)` can force-load a missing chunk even when `create=false`; the Terra adapter must use `getChunkIfLoaded` for reads and refuse missing/uninitiated chunks.
2. PNX LevelDB normal chunk persistence uses `WriteOptions.sync(false)`. A durable WAL acknowledgement must use a separate `sync(true)` write path.
3. Original PNX-Terra `PNXBlockStateDelegate.has/get/set` are stubs returning false/null; state strings omit properties.
4. Original PNX-Terra `PNXChunkDelegate.getX/getZ` return constant zero.
5. Original PNX-Terra `PNXServerWorld.getBlockEntity` and `PNXProtoWorld.getBlockEntity` return null.
6. Original PNX-Terra `PNXProtoWorld.spawnEntity` creates a PNX entity directly; entity lifecycle is not transactionally coupled to the placement WAL.
7. Original PNX-Terra uses a floating `org.powernukkitx:server:nightly-SNAPSHOT`, which can resolve a Java 25 artifact while the project target is Java 21.
8. PNX c574993's old camera API does not compile against the resolved Protocol Beta7 snapshot. The successor ff86b15 updates the protocol integration and built successfully as a standalone baseline.

## Candidate overlay corrections

- `PNXServerWorld.getChunkAt` uses `Level.getChunkIfLoaded` and rejects absent/uninitiated chunks.
- `PnxChunkHandle.saveDurably` writes via `LevelDBProvider.getStorage().writeChunkDurable`, whose PNX storage extension uses `WriteOptions.sync(true)`.
- Per-anchor placement ordinals are assigned from `PlacementTicket.ordinal()` rather than a constant zero.
- `PNXChunkDelegate.getX/getZ` delegate to `IChunk.getX/getZ`.
- `PNXBlockStateDelegate` implements property get/set only for exact matching property ID, Java value class, and full allowed-value set. Serialization uses PNX's canonical `getSerializedValue()`. Unsupported schema/value combinations fail explicitly.
- `PNXServerWorld.getBlockEntity` reads only from an already-loaded chunk and returns a read-only `PNXBlockEntityDelegate`. BE update and non-empty state mutation deliberately fail closed until the NBT transaction contract is implemented.
- Unknown Origen scripts use a bounded runtime guard (x/z +/-32, y +/-128, 65,536 reads/writes, no entity spawns); this is containment, not static bounds proof.
- Scheduler retry state is reconciled with `Level.isChunkGenerating`; accepted-but-failed requests are retried after PNX releases its generation queue slot.

## Remaining integration gaps

- Full alias map for non-identical Terra/PNX property names/types/value representations.
- PNX BlockEntity factory, NBT encode/decode and transactional update integration (the candidate currently provides read-only loaded-chunk access only).
- Transactional entity spawn intents and durable recovery.
- Physics execution semantics and crash-safe completion of neighbor updates.
- Complete Origen static bounds catalog and entity-bearing structure support.
- Full diagnostics routing and runtime tests.

This report separates original upstream observations from candidate-overlay corrections. No production server or world has been modified.

## Stage 35 entity lifecycle seam

- At PNX c574993 and successor ff86b15, `Entity.createEntity(String, IChunk, CompoundTag)` routes through the entity registry; `Entity.init` reads the persisted `uuid` string before adding the entity to both chunk and level registries. `IChunk.getEntities()` exposes the loaded target chunk entity map, and `Entity.getUniqueId()`, `getIdentifier()`, `isClosed()`, `spawnToAll()`, and `getDefaultNBT(Vector3, Vector3, float, float)` are available.
- `Entity.close()` delegates to private `close(true)`, which can be cancelled by `EntityDespawnEvent`; `close(false)` removes the entity from viewers, chunk and level without that cancellable event. Stage 35 adds a narrow public rollback bridge to that existing lifecycle path.
- The finalizer now applies journaled entity intents on the target chunk, holds created entities invisible until durable chunk save, and publishes them only on confirm. It matches existing entities by UUID and validates identifier and transform before treating replay as complete.
- Opaque payload bytes are rejected until a canonical PNX entity NBT payload codec is implemented. `PNXServerWorld.spawnEntity` still fails closed; no Terra deferred-entity capture path is claimed.
