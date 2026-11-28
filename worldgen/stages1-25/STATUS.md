# Stage 1–25 gate status

**Status: BLOCKED — the complete modified stages 1–25 chain has not yet passed a full upstream build or runtime certification. It is not deployable.**

## Latest candidate overlay (2026-11-28, r8)

The r8 candidate is the r7 overlay plus a pinned Terra BlockEntity capture-gap audit. The underlying candidate contains 39 Java source files, 11 test classes, 19 active patches, an exact-ref patch-application script, validation scripts/results, and 13 original archives.

Latest corrections carried in the candidate:

1. **Stage 17 retry liveness:** accepted anchor requests are not resubmitted while PNX still owns the generation-queue slot. If PNX releases that slot without a durable anchor-complete WAL marker, the scheduler clears the stale request and retries using the pinned `Level.isChunkGenerating(x,z)` API.
2. **Stage 17 self-dependency:** `PopulateStage` opens its `AnchorSession` before registering the target dependency envelope. The scheduler skips active sessions and waits for durable completion.
3. **No force-load during Terra reads:** `PNXServerWorld.getChunkAt` uses `Level.getChunkIfLoaded` and rejects absent/uninitiated chunks.
4. **Unknown Origen scripts:** unknown structure IDs use a hard runtime-bounded fallback (x/z +/-32, y +/-128, 65,536 reads/writes, zero entity spawns). Nested scripts share the root guard; any out-of-envelope operation aborts the placement. Namespaced IDs resolve to audited profiles where available. This is runtime containment, not static proof of every script's theoretical bounds.
5. **Durability and ordering:** chunk persistence uses LevelDB `WriteOptions.sync(true)` before WAL acknowledgement; placement records use the per-anchor ticket ordinal.
6. **PNX delegates:** `PNXChunkDelegate` returns actual chunk X/Z. `PNXBlockStateDelegate` supports exact matching property schemas and fails closed on mismatches. `PNXServerWorld.getBlockEntity` reads only already-loaded chunks through a read-only wrapper; BE updates and NBT state mutations remain fail-closed.

## Local validation

- Java 21 core compile: PASS, `--release 21 -Xlint:all -Werror`.
- 11 core test suites x 10 complete rounds in the earlier recorded validation: **110/110 PASS**.
- Fresh r7 rerun: all 11 core test suites PASS.
- Runtime journal Java 21 contract-stub compile: PASS against `Level.isChunkGenerating(x,z)`; freshly rerun for r7.
- PNX block-state/chunk/block-entity delegate Java 21 contract compile and behavior test: PASS for exact property matching/get/set/serialization, chunk coordinates, read-only BE access and fail-closed BE mutations; freshly rerun for r5.
- Active patch syntax: **19/19 PASS** using `git apply --stat`.
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

- File: `PNX-Terra-stages1-25-unified-candidate-2026-11-28-r7.zip`
- Size: 1,360,756 bytes
- SHA-256: `9874916e2ffeaaac4a3b1c6e5c3cc3fabd815fa41cd692e6431590e03b20a0e9`
- ZIP integrity: PASS
- The r7 ZIP, checksum sidecar, and updated BlockEntity audit report have been saved to the user's persistent Library under `/PNX-Terra-Backups/` and the ZIP was re-downloaded and re-verified by SHA-256 and ZIP CRC.
- Earlier r3 archive and verified duplicate remain preserved; no prior copy was deleted or overwritten.


### Stage 22 groundwork added in r5

- `PnxBlockEntityNbtCodec` follows the pinned PNX LevelDB chunk serializer's little-endian NBT format; identity/coordinate drift is rejected.
- `PnxLevelExecutor` dispatches through `Level.getScheduler().scheduleTask`.
- New patch `22-stage22-be-nbt-codec-and-level-executor.patch` parses and applies cleanly to an empty synthetic adapter tree.
- Contract-stub test: PASS. This is not a full upstream NBT/runtime test and is not yet wired into the finalizer.
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
