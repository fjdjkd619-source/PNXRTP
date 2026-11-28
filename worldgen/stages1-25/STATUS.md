# Stage 1–25 gate status

**Status: BLOCKED — the complete modified stages 1–25 chain has not yet passed a full upstream build or runtime certification. It is not deployable.**

## Latest candidate overlay (2026-11-28, r8)

The r8 candidate adds staged TerraScript BlockEntity capture for supported signs, mob spawners and loot containers; hash-protected journal intents; Level-scheduler finalization with early durable save/rollback; and the pinned PNX spawner-delay persistence fix. The underlying candidate contains 44 Java source files, 11 test classes, 21 active patches, an exact-ref patch-application script, validation scripts/results, and 13 original archives.

Latest corrections carried in the candidate:

1. **Stage 17 retry liveness:** accepted anchor requests are not resubmitted while PNX still owns the generation-queue slot. If PNX releases that slot without a durable anchor-complete WAL marker, the scheduler clears the stale request and retries using the pinned `Level.isChunkGenerating(x,z)` API.
2. **Stage 17 self-dependency:** `PopulateStage` opens its `AnchorSession` before registering the target dependency envelope. The scheduler skips active sessions and waits for durable completion.
3. **No force-load during Terra reads:** `PNXServerWorld.getChunkAt` uses `Level.getChunkIfLoaded` and rejects absent/uninitiated chunks.
4. **Unknown Origen scripts:** unknown structure IDs use a hard runtime-bounded fallback (x/z +/-32, y +/-128, 65,536 reads/writes, zero entity spawns). Nested scripts share the root guard; any out-of-envelope operation aborts the placement. Namespaced IDs resolve to audited profiles where available. This is runtime containment, not static proof of every script's theoretical bounds.
5. **Durability and ordering:** chunk persistence uses LevelDB `WriteOptions.sync(true)` before WAL acknowledgement; placement records use the per-anchor ticket ordinal.
6. **PNX delegates:** `PNXChunkDelegate` returns actual chunk X/Z. `PNXBlockStateDelegate` supports exact matching property schemas and fails closed on mismatches. `PNXServerWorld.getBlockEntity` remains read-only outside an active placement. Inside a placement, staged sign/spawner/container blocks receive detached Terra delegates; `update(false)` records immutable journal intents. Unknown types and unsupported inventory capacities remain fail-closed.

## Local validation

- Java 21 core compile: PASS, `--release 21 -Xlint:all -Werror`.
- 11 core test suites x 10 complete rounds in the earlier recorded validation: **110/110 PASS**.
- Fresh r8 rerun: all 11 core test suites PASS.
- Runtime journal Java 21 contract-stub compile: PASS against `Level.isChunkGenerating(x,z)`; freshly rerun for r8.
- PNX block-state/chunk/block-entity delegate Java 21 contract compile and behavior test: PASS for exact property matching/get/set/serialization, chunk coordinates, read-only BE access and fail-closed BE mutations; freshly rerun for r8.
- Active patch syntax: **21/21 PASS** using `git apply --stat`.
- Supplemental hardening patch: clean `git apply --check` and application PASS against the reconstructed Stage 21–25 baseline overlay.
- Original archive readability: **13/13 PASS**.
- The exact-ref patch-application script passes shell syntax validation; it has not yet been run against full upstream checkouts.

### New pinned BlockEntity source audit

Audited `PowerNukkitX/PowerNukkitX@c57499365737654eca32df008751ef6bca5af142` and confirmed:
- `BlockEntity.createBlockEntity` resolves through `Registries.BLOCKENTITY`, runs `loadNBT`, and immediately registers in both chunk and level.
- `BlockEntity.close` removes the object from both registries.
- `Chunk.addBlockEntity` closes an existing tile at the same coordinate, so collisions must be rejected before creation.
- The pinned little-endian NBT codec, transactional intent applier, and level-scheduler executor are implemented. The finalizer now keeps block projection on the compute pool and dispatches BE/physics intents through the owning level scheduler.

The adapter intentionally remains fail-closed for BlockEntity mutation until Terra-side intent capture and full durable recovery are implemented. Full audit: [BLOCK-ENTITY-UPSTREAM-API-AUDIT-2026-11-28.md](BLOCK-ENTITY-UPSTREAM-API-AUDIT-2026-11-28.md).

## Latest recovery archive

- File: `PNX-Terra-stages1-25-unified-candidate-2026-11-28-r8-complete.zip`
- Size: 1,452,073 bytes
- SHA-256: `d845c9520d5e06fbfa97f59a04b5949974b0fd317569e5b0d4b6a150ba19a111`
- ZIP integrity: PASS; re-materialized from Library and SHA-256 matched the source archive exactly.
- The r8 ZIP, checksum sidecar, and new Terra BlockEntity capture audit are saved under `/PNX-Terra-Backups/`.
- Earlier r3–r7 archives remain preserved; no prior copy was deleted or overwritten.

### Stage 22 Terra-side capture audit (r8)

Pinned Terra API inspection found that `WritableWorld` can write block states and spawn entities but has no BlockEntity creation/write method. `ReadableWorld.getBlockEntity` is read-only; Terra's BlockEntity interface exposes no canonical PNX type ID or NBT payload. The pinned `SpongeStructure.generate` writes only palette block states, and TerraScript delegates through buffered `WritableWorld` without a BlockEntity channel. PNX-Terra's `PNXServerWorld.setBlockState` likewise has no journal capture path.

Therefore the existing r7 PNX NBT codec/applier is only the consumer side; it cannot receive correct BlockEntity intents from ordinary Terra structures. Do not infer tile data from block IDs. Required next work: add a PNX-Terra structure-input capture extension for explicitly supported formats, route immutable typed NBT records through the same placement journal, then apply on the Level scheduler before durable save/ack. Full source audit: [STAGE22-TERRA-BLOCKENTITY-CAPTURE-AUDIT-2026-11-28.md](STAGE22-TERRA-BLOCKENTITY-CAPTURE-AUDIT-2026-11-28.md).

### Stage 22 groundwork added in r5

- `PnxBlockEntityNbtCodec` follows the pinned PNX LevelDB chunk serializer's little-endian NBT format; identity/coordinate drift is rejected.
- `PnxLevelExecutor` dispatches through `Level.getScheduler().scheduleTask`.
- New patch `22-stage22-be-nbt-codec-and-level-executor.patch` parses and applies cleanly to an empty synthetic adapter tree.
- Contract-stub test: PASS. The r6/r7 increments subsequently wired the applier into the finalizer and added scheduler dispatch; this remains contract-level validation, not a full upstream NBT/runtime test.
- The two source files, patch, audit, and validation result have been committed to this GitHub branch.


### Stage 22 BlockEntity integration increment (r6)

- `PnxBlockEntityNbtCodec` uses the pinned PNX LevelDB serializer's little-endian NBT format and validates payload identity/coordinates.
- `PnxBlockEntityIntentApplier` preflights all intents, resolves registered types, rejects collisions, accepts byte-identical persisted entities for replay, and rolls back every newly registered entity if creation fails. This specifically handles the PNX factory swallowing constructor exceptions after the base constructor has registered the object.
- `ChunkFinalizationCoordinator` now accepts separate projection/intent executors. `PnxCrossChunkFinalizer` keeps block writes on the compute pool and sends BE/physics intents through `PnxLevelExecutor` on the owning Level scheduler.
- Three Stage 22 patches pass `git apply --check` and application against the reconstructed post-r5 adapter baseline; all six resulting source files match the r6 overlay.
- Contract tests pass for idempotent replay, type conflicts, rollback after swallowed constructor failure, and scheduler dispatch.
- Still open: Terra structures/populators do not yet emit BlockEntity intents into the journal; no full upstream compile or runtime save/restart certification has been run.

## Verified upstream baseline results

### Terra 6.6.1-BETA — PASS
Pinned commit `05ddc8b8a66e17412670647cce78ef837b091256`:
https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46098232420

### PNX 3.0.6 successor + original PNX-Terra 1.0.0 — PASS
Compatibility run:
https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46098134148

PNX commit `ff86b15d18e3699459c7913eb1ebec16521b7ad0` built/published as `3.0.6-SNAPSHOT`; the unmodified pinned PNX-Terra 1.0.0 source compiled and packaged against it using Java 21. Artifact:
https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46098134148/artifacts/15243649817

This is a compatibility baseline only; it does not contain the stages 1–25 overlay.

### Original PNX c574993 baseline remains incompatible
- PNX `c574993` fails compilation against the resolved Protocol Beta7 snapshot due to nine camera API symbols.
- PNX-Terra's floating `server:nightly-SNAPSHOT` resolves Java 25 class files while the build targets Java 21.
- The successful successor experiment does not silently replace the user's pinned production server.

## Remaining blockers before completion

- Apply the complete active patch chain to clean full upstream checkouts and resolve every context/API conflict.
- Build the modified Terra, PNX and PNX-Terra source together; the successful upstream baseline build did not include this overlay.
- Compile the full adapter against actual pinned APIs; only the core and narrow contract slices have been compiled locally.
- Complete Terra-to-adapter BlockEntity intent capture from actual structure source data and connect it to the existing r7 NBT codec/applier; test end-to-end durable save/restart. The PNX applier exists, but no ordinary Terra structure path currently emits its intents.
- Implement transactional entity spawn intents and durable recovery.
- Connect the physics executor and crash-safe neighbor updates; finish water-layer/property mapping.
- Complete non-identical Terra/PNX property aliases, global diagnostics and the static Origen bounds catalog.
- Run end-to-end generation, crash/restart/replay, persistence, lighting, cross-chunk and load tests.
- Produce and verify the integrated stages 1–25 JAR.

Production server and world remain untouched. Do not start stages 26–34 or deploy while the gate is BLOCKED.

## GitHub-first and backup policy

This branch is the authoritative project history. Commit completed source changes, tests, patches, and status changes to `worldgen/stages1-25` before moving to the next task. Preserve branch history; do not force-push or rewrite commits.

Persistent Library copies are additional recovery copies, not a substitute for the GitHub source history. Keep older copies until newer copies have been independently verified. The r5 archive's full binary payload is stored in the Library backup folder; it is not yet a binary asset in this repository.


### Stage 22 TerraScript BlockEntity capture (r8)

- Four adapter patches and one PNX core patch are now in the branch; the five patches are also preserved in the complete r8 Library archive.
- Staged Sign delegates map `text0..text3`; MobSpawner delegates map Terra's documented state properties and require a registered PNX entity identifier; Container delegates stage loot inventory through PNX item serialization.
- The active placement transaction journals detached BlockEntity intents; no live PNX BlockEntity is created during population.
- The PNX core patch persists MobSpawner `Delay`, which the pinned PNX source previously kept only in memory.
- The finalizer applies BlockEntity intents on the owning Level scheduler, durably saves before the next spawner heartbeat, and rolls back newly registered objects if creation or early save fails.
- Contract tests pass for NBT/hash tampering, sign state, spawner properties, container item serialization, replay, collision refusal, constructor rollback, early-save rollback, and scheduler dispatch.
- Full PNX/Terra upstream compilation, Bedrock runtime generation, and crash/restart certification remain pending.

The r8 archive itself is stored in ChatGPT Library at `/PNX-Terra-Backups/PNX-Terra-stages1-25-unified-candidate-2026-11-28-r8-complete.zip`. The binary ZIP is not committed to this GitHub branch; the branch contains the relevant source files, patch files, and audit/validation reports.


### Stage 22 TerraScript BlockEntity capture — current r8 implementation

- Four adapter patches plus `22-stage22-pnx-spawner-delay.patch` are committed in this branch.
- `PNXStagedSignDelegate` maps Terra `Sign` state properties `text0..text3` into PNX front-sign NBT.
- `PNXStagedMobSpawnerDelegate` maps Terra's documented spawner properties and requires the entity identifier to resolve in the pinned PNX registry.
- `PNXStagedContainerDelegate` and `PNXStagedInventory` stage loot inventory through PNX `ItemHelper.write`.
- `PNXServerWorld.PlacementTransaction` journals these detached BlockEntity intents and merges nested transaction savepoints.
- `PnxBlockEntityNbtCodec` adds a SHA-256 marker so PNX default-field normalization does not break replay detection. `PnxBlockEntityIntentApplier` validates the marker, canonical registry ID, coordinates and collisions; it rolls back entities registered before a constructor failure is swallowed by PNX.
- The coordinator applies intents on the owning Level scheduler, durably saves before the next spawner heartbeat, and rolls back newly registered entities if that early save fails.
- Narrow Java 21 contract tests pass for hash tampering, sign state, spawner properties, container serialization, replay, collision refusal, constructor rollback and early-save rollback. Full upstream compile, Bedrock runtime generation and crash/restart certification remain pending.

