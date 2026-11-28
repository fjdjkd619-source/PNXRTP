# Stages 43–50 — completion plan for R15

**Status: Stages 43–46 source/adapter/core patches and ordered apply runners are present; full compile and runtime validation are deliberately deferred until Stage 50. Stage 44 retention is partial. Stages 47–50 remain planned.** These stages close the production blockers found in the Stage 1–42 audit. Stage numbers are work packages, not claims that the work is already complete. The R15 release gate stays BLOCKED until every acceptance condition below is met.

## Stage 43 — WAL checkpoint format and snapshot model

**Code added — not yet built or tested.**

- Added JournalCheckpoint to the source overlay and Stage 43 adapter patch, plus the ordered apply-stages1-43.sh runner.
- Added PlacementJournal.checkpointSnapshot() and writeCheckpoint(Path): both expose a consistent event list plus sequence watermark under the journal lock, and publish a durable checkpoint without rotating or truncating the WAL.
- Added an integration test that confirms the checkpoint captures the same committed prefix and that Stage 43 leaves the WAL intact.
- The codec writes a versioned PXCP snapshot with a SHA-256 integrity digest, strict payload/count limits, deterministic event encoding and strict UTF-8 decoding.
- Publication uses a same-directory temporary file, fsync, atomic replacement and parent-directory fsync; unsupported atomic moves fail closed.
- Added JournalCheckpointTest for round-trip of all event kinds and intent payloads, hash tampering, truncation and atomic replacement. Per the requested build policy, this test has not been run.
- This stage adds the checkpoint data format only; it does not yet rotate or compact the live WAL. That integration belongs to Stage 44.
- Define a versioned, deterministic checkpoint representation for the journal's durable state: placements, anchor lifecycle, applied-chunk digests, entity UUID ownership and sequence watermark.
- Validate every field, collection count, payload bound and digest before accepting a checkpoint.
- Preserve compatibility with WAL event versions 1–4; never reinterpret old frames.
- Add a checkpoint integrity hash and explicit schema/version metadata.
- Acceptance: checkpoint round-trip preserves the complete logical journal state and sequence watermark; malformed, truncated, oversized or hash-mismatched checkpoints fail closed without modifying source files.

## Stage 44 — Crash-safe WAL compaction and bounded retention

**Code added — not yet built or tested; retention remains incomplete.**

- Added a checksummed compaction marker and a recovery protocol that can finish a WAL truncate after a crash once the checkpoint and marker are durable.
- Added a compaction integration test covering checkpoint replay, post-checkpoint WAL events, interrupted compaction and corrupt-marker fail-closed behavior. It has not been run.
- Compaction reduces the WAL to an empty file and collapses duplicate anchor/applied event history in memory. It still retains every placement record and its entity UUID ownership index; therefore the checkpoint and indexes can continue growing. This is not yet a bounded-retention solution.
- Write checkpoints to a new sibling file, force file contents, atomically publish it, and force the parent directory where supported.
- Rotate the WAL only after the checkpoint is durably published; recovery must safely handle crashes at every rotation boundary.
- Replay only WAL frames newer than the checkpoint watermark and reject sequence gaps or conflicting duplicate identities.
- Remove only records proven covered by a durable checkpoint; retain collision-safe evidence for incomplete rotation artifacts.
- Bound in-memory indexes by compacting state that is no longer needed while retaining enough idempotency information to prevent duplicate placements and entity UUID reuse.
- Acceptance: crash-point recovery matrix passes for every file operation; replay remains deterministic; disk/RAM retention is bounded by documented policy and tested against long event histories.

## Stage 45 — Live PNX physics executor

**Code and adapter patch added — not yet built or tested.**

- PnxChunkHandle now dispatches deterministic PhysicsIntentBatch work through the owning Level scheduler path used by PnxCrossChunkFinalizer.
- Before calling PNX updateAround(), it verifies that the target and all cross-chunk neighbour chunks needed by the six-direction update are already loaded; it refuses to force-load missing neighbours.
- Added the ordered adapter patch and apply-stages1-45.sh runner. Runtime correctness and failure/retry behavior remain unverified.
- Connect PhysicsIntentBatch to the actual PNX Level scheduler/executor used by the target chunk.
- Keep deterministic ordering, deduplication and target-chunk ownership checks.
- Never read or mutate unloaded neighboring chunks; schedule dependencies explicitly or fail the placement transaction closed.
- Propagate executor failures into placement rollback/recovery and diagnostics.
- Acceptance: exact pinned PNX compile succeeds; physics intents are applied on the correct Level thread; duplicate, cross-chunk, unloaded-neighbor and executor-failure cases are covered.

## Stage 46 — Bedrock two-layer and water-state integration

**Code and ordered patches added — not yet built or tested.**

- Added a PNX BlockManager layer-state accessor that reads the active placement cache first and never force-loads a missing chunk.
- Corrected Terra population writes to distinguish intrinsic water plants, blocks that support waterlogging, explicit waterlogged state, and ordinary non-waterloggable blocks.
- Preserves the existing source/flowing liquid state when replacing water with a waterloggable block; clears only stale water in layer one and leaves unrelated second-layer content untouched.
- Applied the same policy to PNX ProtoChunk terrain writes, covering both terrain and structure/population generation paths.
- Added the explicit PNX two-layer overload to `WaterLayerPolicy`, plus separate PNX-core and adapter patches and `apply-stages1-46.sh`.
- Updated the manual compile-only workflow to apply stages 1–46. All Terra, PNX and adapter Gradle commands continue to pass `-x test`; no workflow was launched because tests/build certification remain deferred until Stage 50.
- Acceptance remains open: compile the exact pinned sources, then verify round trips for flowing/still water, kelp, seagrass, waterlogged blocks, replacement/clearing semantics and unloaded-neighbor boundaries.

## Stage 47 — Transactional Terra entity capture and finalization

**Capture and WAL integration code added — not yet built or tested.**

- Replaced the unconditional spawn rejection with a placement-local Terra entity facade. It exposes mutable position/world through the Terra API but creates no live PNX entity during feature execution.
- Root commit derives deterministic UUIDs from the durable PlacementKey and feature-local spawn order, then appends typed EntitySpawnIntent records to the existing WAL v4 format.
- Nested placements merge staged entity captures into the root savepoint; journal-less legacy construction rejects entity spawning rather than creating a non-durable entity.
- Extended the PNX placement record factory and committer to carry entity intents; the existing PNX finalizer/applier performs UUID validation, chunk-local staging, synchronous durable save, activation and rollback.
- Added the Stage 47 patch and ordered runner. Entity factory compatibility, restart replay, cancellation and save-failure behavior remain unverified.
- Replace the fail-closed PNXServerWorld.spawnEntity placeholder with a Terra spawn-capture path that creates deterministic EntitySpawnIntent records.
- Preserve stable UUID ownership, entity type, position, rotation and supported NBT payload.
- Integrate the deferred PNX entity construction, pre-save staging, durable chunk save, post-save Level activation and network publication sequence.
- Roll back only entities owned by the failed placement batch; replay must recognize already persisted UUIDs.
- Acceptance: entity-bearing structures can be generated without early Level/chunk registration; save failure, factory failure, cancelled spawn, duplicate UUID, restart and replay cases preserve transactional guarantees.

## Stage 48 — Complete structure bounds, state aliases and diagnostics

- Replace the tiny Origen bounds catalog and generic fallback with an audited manifest for every enabled structure/feature script.
- Validate footprint, vertical range, read/write budgets, cross-chunk reach and entity budgets against actual script behavior.
- Build a verified Terra↔PNX block/property alias catalog from the pinned registries; reject unknown values with actionable diagnostics.
- Wrap every relevant Terra/PNX generation entry point in the stage guard and include stage, feature/structure ID, world, chunk and coordinates in diagnostics.
- Acceptance: no enabled structure relies on an unverified generic envelope; catalog validation and mapping checks pass; failures retain original causes and do not leave partial durable placements.

## Stage 49 — Medieval-fantasy procedural content pack

- Deliver the actual Terra ConfigPack content; infrastructure stages alone are not world content.
- Add coherent natural terrain and biome transitions, rivers/lakes/waterfalls/coasts, unusual rock formations, floating islands, crystal zones, giant caves and underground water systems.
- Add castles, fortresses, towers, villages, bridges, roads, ancient cities, temples, ruins, sanctuaries and hidden complexes.
- Use deterministic placement, weighted rarity, biome/terrain suitability, spacing, footprint bounds and cross-chunk-safe structure placement.
- Add transition rules so structures and natural features combine organically instead of forming fixed map regions.
- Acceptance: pack loads through the pinned Terra 6.6.1 runtime; every enabled asset/script has validated bounds and mappings; representative seeds demonstrate all required feature families and coherent transitions.

## Stage 50 — Unified generator integration and release packaging

- Wire all completed infrastructure and content into one supported PNX–Terra entry point with explicit configuration, lifecycle and failure handling.
- Produce a reproducible release manifest containing exact upstream revisions, config-pack version, JAR names, SHA-256 hashes and required Java version.
- Package the installable generator JAR and required content/resources together; fail packaging if any stage or required asset is missing.
- Verify that the generated artifact is self-consistent and that the server loads the generator without silently falling back to incomplete/default content.
- Acceptance: clean compile-only build produces the complete installable artifact and manifest. Only after this artifact exists do we re-enable regression suites and run the full test, runtime generation, persistence, restart/replay, cross-chunk, lighting, water, physics, entity and long-soak matrix.

## Build and test policy

- Until Stage 50 is complete: no automatic test workflows and no test tasks during build.
- The first final packaging pass is compile/package only: Gradle receives -x test for Terra, PNX and the adapter.
- After a successful complete JAR and manifest exist: enable the test workflows, run the full suite, fix failures, rebuild and publish only the artifact that corresponds to the tested commit.
- A successful patch-application check or core-only compile is not a release certification.
