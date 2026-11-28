# Stages 43–50 — completion plan for R15

**Current verified state (2026-11-28):** Stage 1–47 and Stage 1–48 patch validation pass on R15 commit `77f54148276163528beb1a4081b17847d8525db7`. The pinned ConfigPack audit reports 228 feature roots: 41 statically certified and 187 deny-all; all 6 new Stage 49 structures have certified profiles. YAML errors, duplicate script IDs, unresolved roots and unresolved feature YAML are zero. Full upstream compile run [46177313702](https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46177313702) is still in progress for that commit; no current JAR artifact exists yet. The prior compile-only success on `61e60d5` is historical and does not certify this candidate.

**Stage 49 is partial, not complete.** Six small procedural structures are wired into the pinned Terra ConfigPack: floating rock islands, crystal clusters, ruined watchtowers, castle keeps, timber homesteads and underground crypts. Large fortresses/castles, villages, road networks, bridges, waterfalls, large cave systems, underground rivers/lakes, ancient cities, temples, sanctuaries and landmark combinations remain outstanding.

**Stage 50 acceptance harness is prepared on branch `R15-stage50-prep`, not yet run or merged.** It checks the new content and bounds, exact Stage 37–42 patch application, Stage 35/36 PNX API lifecycle, Java 21 core regression suites and the adapter/PNX contract suites. It is static/contract acceptance only; live world generation, persistence, restart/replay, lighting, cross-chunk, water, physics, entities and soak testing still require separate runtime certification.

The release gate remains **BLOCKED**. Do not deploy until the exact current commit builds, Stage 50 tests pass, the complete content scope is implemented, and live server/runtime certification is complete. Stage numbers are work packages, not claims that every stage is complete.


## Stage 43 — WAL checkpoint format and snapshot model

**Compile-only upstream build: PASS; regression test and runtime acceptance remain unverified.**

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

**Compile-only upstream build: PASS; retention remains incomplete and regression tests remain unrun.**

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

**Compile-only upstream build: PASS; live PNX physics scheduling and failure/retry behavior remain unverified.**

- PnxChunkHandle now dispatches deterministic PhysicsIntentBatch work through the owning Level scheduler path used by PnxCrossChunkFinalizer.
- Before calling PNX updateAround(), it verifies that the target and all cross-chunk neighbour chunks needed by the six-direction update are already loaded; it refuses to force-load missing neighbours.
- Added the ordered adapter patch and apply-stages1-45.sh runner. Runtime correctness and failure/retry behavior remain unverified.
- Connect PhysicsIntentBatch to the actual PNX Level scheduler/executor used by the target chunk.
- Keep deterministic ordering, deduplication and target-chunk ownership checks.
- Never read or mutate unloaded neighboring chunks; schedule dependencies explicitly or fail the placement transaction closed.
- Propagate executor failures into placement rollback/recovery and diagnostics.
- Acceptance: exact pinned PNX compile succeeds; physics intents are applied on the correct Level thread; duplicate, cross-chunk, unloaded-neighbor and executor-failure cases are covered.

## Stage 46 — Bedrock two-layer and water-state integration

**Compile-only upstream build: PASS; Bedrock two-layer/water runtime behavior remains unverified.**

- Added a PNX BlockManager layer-state accessor that reads the active placement cache first and never force-loads a missing chunk.
- Corrected Terra population writes to distinguish intrinsic water plants, blocks that support waterlogging, explicit waterlogged state, and ordinary non-waterloggable blocks.
- Preserves the existing source/flowing liquid state when replacing water with a waterloggable block; clears only stale water in layer one and leaves unrelated second-layer content untouched.
- Applied the same policy to PNX ProtoChunk terrain writes, covering both terrain and structure/population generation paths.
- Added the explicit PNX two-layer overload to `WaterLayerPolicy`, plus separate PNX-core and adapter patches and `apply-stages1-46.sh`.
- Updated the manual compile-only workflow to apply stages 1–46. All Terra, PNX and adapter Gradle commands continue to pass `-x test`; no workflow was launched because tests/build certification remain deferred until Stage 50.
- Acceptance remains open: compile the exact pinned sources, then verify round trips for flowing/still water, kelp, seagrass, waterlogged blocks, replacement/clearing semantics and unloaded-neighbor boundaries.

## Stage 47 — Transactional Terra entity capture and finalization

**Compile-only upstream build: PASS; entity capture, restart/replay and live-server behavior remain unverified.**

- Replaced the unconditional spawn rejection with a placement-local Terra entity facade. It creates no live PNX entity while Terra feature scripts are still executing.
- Root commit derives deterministic UUIDs from the durable PlacementKey and feature-local spawn order, then appends typed EntitySpawnIntent records to WAL v4.
- Nested placements merge staged captures into the root savepoint. Journal-less legacy construction rejects entity spawning rather than creating non-durable entities.
- Entity movement is validated against the root placement envelope both when Terra changes position and immediately before WAL serialization.
- Extended the PNX record factory and committer. Added PNX entity lifecycle-state accessors so replay can distinguish already-active entities from entities durably staged in the chunk but not yet activated.
- The entity applier now reactivates persisted-but-not-yet-active entities idempotently and publishes each entity after activation. Rollback removes only entities created by the failed attempt.
- Reordered finalization so any non-empty BlockEntity/entity/physics projection is durably saved before physics updates are queued or staged entities are exposed. If a later physics operation fails, durable entities are retained for WAL replay instead of being incorrectly rolled back.
- Capture currently records type and position with default yaw/pitch and empty NBT payload because the pinned Terra spawn API exposes no orientation or NBT parameters.
- Added the PNX core lifecycle-query patch, adapter capture/replay patch and ordered `apply-stages1-47.sh` runner. Build, cancellation, restart/replay, factory-failure and live-server behavior remain unverified.

## Stage 48 — Static structure bounds and fail-closed admission

**Partial acceptance passed; complete coverage remains open.**

- Unknown or unproven structure roots resolve to a zero-budget deny-all profile. They cannot silently execute under a permissive fallback.
- `PNXServerWorld.beginPlacement` rejects roots without a verified profile before opening the placement transaction/journal ticket.
- `BoundsCatalogValidator` rejects wildcard dependencies and unverified fallback evidence in the verified profile set.
- CI audits the exact Terra ConfigPack commit `445848bc9f9f28fab1d760f01f9a552d93944681` against pinned PNX `c57499365737654eca32df008751ef6bca5af142`.
- Current inventory: 228 unique feature roots, 41 static profiles, 187 unverified deny-all profiles, 0 YAML parse errors, 0 duplicate script IDs, 0 unresolved roots and 0 unresolved feature YAML.
- All six Stage 49 TerraScripts have finite static profiles and are present in the generated `OrigenBoundsCatalog`.
- The CI audit artifact is attached to run [46177313748](https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46177313748).

Still open: certify the remaining 187 roots or leave them explicitly disabled; verify every enabled alias and dynamic state expression; wire startup validation and complete diagnostics/rollback integration. Stage 48 is not complete while any required content root remains unverified.

Acceptance: every enabled root has one statically verified profile; all other roots are excluded; alias mappings are registry-verified; startup and failure diagnostics are exercised.
## Stage 49 — Procedural medieval-fantasy content pack

**Partial content slice committed; full world catalog and runtime loading remain open.**

Added and registered six Terra feature families in the pinned ConfigPack:
- Rare high-altitude floating rock islands.
- Underground amethyst/calcite crystal pockets.
- Sparse ruined stone watchtowers.
- Rare compact castle keeps.
- Small timber homesteads.
- Underground crypt chambers.

Each feature points to a finite TerraScript structure. The current bounds generator certifies all six scripts; CI verifies feature-to-script references, YAML parsing, root inventory and generated catalog membership.

Still to implement:
- Full castles, fortified compounds, walls, towers and gates.
- Villages, hamlets, roads, bridges and connected settlements.
- Waterfalls, river gorges, lakes, underground rivers and coast transitions.
- Large cave networks, underground halls and crystal caverns.
- Ancient cities, temples, ruins, sanctuaries and hidden complexes.
- Weighted rarity, terrain/biome suitability, spacing, multi-feature transitions and representative seed coverage.

Acceptance: the complete content pack loads through pinned Terra 6.6.1; every enabled script has certified bounds and valid block mappings; representative seeds demonstrate each required feature family and coherent transitions.
## Stage 50 — Unified integration, acceptance and release packaging

**Acceptance harness prepared on `R15-stage50-prep`; not yet executed or merged.**

The new `validation/run-stage50-acceptance.sh` runs:
- Stage 49 feature/structure manifest and static bounds checks.
- Exact Stage 37–42 patch apply/byte comparison.
- Stage 35/36 pinned PNX entity lifecycle API checks.
- Java 21 core regression suites with `-Xlint:all -Werror`.
- Delegate, entity-spawn, BlockEntity and runtime journal contract suites.

The build workflow on the preparation branch places this acceptance step after the compile-only upstream build and includes the resulting report/log in the artifact. This harness does not claim a live server test.

Remaining Stage 50 work:
- Run and fix the full acceptance suite on the exact build commit.
- Produce adapter, Terra, PNX and required dependency JARs plus the complete ConfigPack, manifest and SHA-256 inventory.
- Ensure release packaging rejects blocked/incomplete status and missing runtime certification markers.
- Verify the artifact loads in a clean PNX server and creates/saves/reloads worlds.
- Run restart/replay, cross-chunk, BlockEntity/entity, water, physics, lighting, diagnostics and long-soak tests.

Acceptance: the exact source commit passes compile and Stage 50 tests; all required artifacts and checksums exist; live server/runtime certification passes. Until then the release gate stays BLOCKED.
## Build and test policy

- Until Stage 50 is complete: no automatic test workflows and no test tasks during build.
- The first final packaging pass is compile/package only: Gradle receives -x test for Terra, PNX and the adapter.
- After a successful complete JAR and manifest exist: enable the test workflows, run the full suite, fix failures, rebuild and publish only the artifact that corresponds to the tested commit.
- A successful patch-application check or core-only compile is not a release certification.
