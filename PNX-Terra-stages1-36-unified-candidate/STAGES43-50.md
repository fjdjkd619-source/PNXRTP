# Stages 43–50 — completion plan for R15

**Status: Stages 43–47 source/adapter/core patches and ordered apply runners are present; full compile and runtime validation are deliberately deferred until Stage 50. Stage 44 retention is partial. Stages 45–47 remain uncompiled and untested. Stage 48 has a partial fail-closed implementation and a measured coverage audit, but its acceptance gate is NOT PASSED. Stages 49–50 remain planned.** These stages close the production blockers found in the Stage 1–42 audit. Stage numbers are work packages, not claims that the work is already complete. The R15 release gate stays BLOCKED until every acceptance condition below is met.

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

**Capture, WAL and replay-hardening code added — not yet built or tested.**

- Replaced the unconditional spawn rejection with a placement-local Terra entity facade. It creates no live PNX entity while Terra feature scripts are still executing.
- Root commit derives deterministic UUIDs from the durable PlacementKey and feature-local spawn order, then appends typed EntitySpawnIntent records to WAL v4.
- Nested placements merge staged captures into the root savepoint. Journal-less legacy construction rejects entity spawning rather than creating non-durable entities.
- Entity movement is validated against the root placement envelope both when Terra changes position and immediately before WAL serialization.
- Extended the PNX record factory and committer. Added PNX entity lifecycle-state accessors so replay can distinguish already-active entities from entities durably staged in the chunk but not yet activated.
- The entity applier now reactivates persisted-but-not-yet-active entities idempotently and publishes each entity after activation. Rollback removes only entities created by the failed attempt.
- Reordered finalization so any non-empty BlockEntity/entity/physics projection is durably saved before physics updates are queued or staged entities are exposed. If a later physics operation fails, durable entities are retained for WAL replay instead of being incorrectly rolled back.
- Capture currently records type and position with default yaw/pitch and empty NBT payload because the pinned Terra spawn API exposes no orientation or NBT parameters.
- Added the PNX core lifecycle-query patch, adapter capture/replay patch and ordered `apply-stages1-47.sh` runner. Build, cancellation, restart/replay, factory-failure and live-server behavior remain unverified.

## Stage 48 — Complete structure bounds, state aliases and diagnostics

**Partial implementation committed in R15; Stage 48 is NOT complete.**

- Changed unknown-script resolution from a permissive generic envelope to a deny-all profile: zero read/write/entity budgets.
- Changed `PNXServerWorld.beginPlacement` to reject roots without a verified profile before opening a placement transaction or journal ticket. The warning includes stage, feature, structure, world key, chunk anchor and origin coordinates.
- Added enabled-root validation to `BoundsCatalogValidator.validateEnabled(...)`. Release validation can reject any enabled structure without an audited profile and reject wildcard dependencies in audited profiles.
- Added `apply-stages1-48.sh` to install the tightened catalog, validator and root-admission overlay into the pinned adapter after stages 1–47.
- Exact pinned inventory is now generated in CI against Terra ConfigPack commit `445848bc…` and PNX commit `c574993…`. It finds 287 TerraScripts, 28 YAML structure definitions, 333 feature YAML files and 222 feature roots; all roots resolve, but all 287 scripts remain UNKNOWN for bounds. Only 7 of 222 roots match the 12 runtime profiles; 215 roots remain unprofiled and fail closed. Static block-state audit finds 486 literals, all compatible with PNX states (223 require PNX default-property completion); zero unsupported variants/IDs. `PNXBlockStateDelegate.getBlockType()` was corrected to expose the actual registered PNX default state. Twelve dynamic state expressions still need runtime validation. 107 scripts have dynamic nested targets. Full evidence and workflow artifact details are in `history/reports/STAGE48-BOUNDS-COVERAGE-R15.md`.
- Still open: certify bounds and dynamic nested-call envelopes for every enabled script/YAML structure; wire enabled-root validation into pack startup; runtime-validate dynamic block-state expressions; complete world-aware diagnostics and rollback verification.
- Acceptance: the exact enabled-root set has one verified profile per root, no enabled root uses the deny-all fallback, all alias mappings are registry-verified, and diagnostic/rollback integration is complete.

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
