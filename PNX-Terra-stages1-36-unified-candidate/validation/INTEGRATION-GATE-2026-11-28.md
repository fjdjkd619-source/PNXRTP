# Integration gate — 2026-11-28

Status: BLOCKED. This is not a full build and is not deployable.

## Passed
- Stage 17 retry fix: accepted in-flight anchors are not resubmitted while PNX still owns the generation queue slot; if PNX releases that slot without a durable completion marker, the stale request is cleared and retried.
- Stage 17 self-dependency fix: an active target anchor is not recursively requested; its barrier waits for the active AnchorSession.
- Adapter safety fix: `PNXServerWorld.getChunkAt` uses PNX `Level.getChunkIfLoaded` and refuses to force-load missing/uninitiated chunks. PNX source at the pinned commit confirms `getChunk(int,int)` invokes `forceLoadChunk` when the chunk is absent, while `getChunkIfLoaded` only reads the provider's loaded-chunk map.
- Regression fix: dependency retry now skips anchors whose PNX generation request was accepted; this prevents resubmitting a live anchor and resetting it to GENERATED. The regression test spans multiple 250 ms retry windows.
- OpenJDK 21 independent core compile with --release 21 -Xlint:all -Werror.
- 11 test classes x 10 rounds = 110 successful executions after the bounded-fallback and failed-request retry changes.
- 22 active patch files parsed with git apply --stat; Stage 26 placement ordinal is now active.
- Supplemental stages 15–25 hardening patch applies cleanly to the reconstructed Stage 21–25 baseline overlay.
- PnxGenerationJournalRuntime Java 21 contract-stub compile passes against the pinned Level.isChunkGenerating API.
- PNXBlockStateDelegate, PNXChunkDelegate and PNXBlockEntityDelegate Java 21 contract compile/behavior tests pass for exact property schemas, nonzero chunk coordinates, read-only BlockEntity wrapping and fail-closed mutations.
- Fresh rerun in this work session: all 11 core test suites PASS; runtime journal Java 21 contract compile PASS; PNX delegate Java 21 contract compile/behavior test PASS.
- Pinned PNX 3.0.6 and Terra source audits completed for BlockEntity creation/registration/close lifecycle, registry lookup, chunk tile collision behavior, little-endian NBT codec, Level scheduler heartbeat, and TerraScript `state`/`loot` call paths. Detached staged Sign/MobSpawner/Container capture now journals intents without live BE mutation during population. See `BLOCK-ENTITY-UPSTREAM-API-AUDIT-2026-11-28.md` and `TERRA-STRUCTURE-BLOCKENTITY-CAPTURE-AUDIT-2026-11-28.md`.
- Added `PnxBlockEntityNbtCodec` with a durable payload hash marker, `PnxBlockEntityIntentApplier`, `PnxLevelExecutor`, and detached TerraScript delegates for signs, spawners, and loot containers. The placement transaction now journals their intents; the coordinator applies them on the PNX Level scheduler and performs an early durable save before a newly created spawner can tick. Contract tests cover NBT/hash integrity, sign state parsing, spawner property mapping, container item serialization, replay idempotency, type conflicts, swallowed-constructor rollback, early-save rollback, and scheduler dispatch. Full upstream dependency compilation/runtime tests remain pending.
- 13 original archives passed ZIP CRC/readability checks.

## Not passed / not run
- Clean pinned source checkouts and full git apply --check.
- Full Gradle build of the modified stages 1–26 candidate. Separate upstream baselines: Terra pinned build PASS; PNX successor ff86b15 + unmodified PNX-Terra 1.0.0 compatibility build PASS. Original PNX c574993 and floating-nightly PNX-Terra baselines fail for the recorded Protocol/Java 25 mismatches.
- Full adapter compile against upstream APIs.
- Full upstream BlockEntity NBT/factory compilation and Bedrock save/load/restart certification (the staged TerraScript capture and journal applier are implemented and narrow contract-tested).
- Entity spawn-intent capture/application integration.
- Runtime physics executor / WaterLayerPolicy integration.
- Complete block/property mapping: exact property schemas are supported by the delegate, but non-identical property names, Java types and value representations still require explicit verified aliases.
- Global diagnostics.
- Complete static Origen safe-bounds catalog. Three scripts have statically audited profiles; the remaining scripts use a strict runtime fallback of +/-32 horizontal and +/-128 vertical, with a 65,536 operation budget and zero entity budget. Runtime enforcement is tested in the core harness, but actual Terra adapter execution remains untested.
- Runtime generation, restart/recovery, durable persistence and load tests.
- Integrated JAR packaging.

The GitHub Actions run 46096537147 failed intentionally because the acceptance status was BLOCKED. It did not run Gradle.


## Stage 26 — deterministic placement identity (integrated candidate)

- Added `26-stage26-deterministic-placement-ordinal.patch` to the active patch chain and exact-ref apply script.
- `AnchorSession.beginPlacement()` allocates a unique monotonically increasing ordinal under the scheduler lock. It checks exhaustion before increment and uses `Math.incrementExact`; the counter is scoped to the anchor session and therefore restarts from zero when an incomplete anchor is replayed.
- `PlacementTicket.ordinal()` is included in `PNXServerWorld`'s `PlacementKey`; root placements no longer share the constant ordinal `0L`.
- Regression test asserts consecutive root tickets receive ordinals 0 and 1 and verifies aborting one ticket still releases the session correctly.
- The Stage 26 patch is path-rooted to the adapter Maven source/test layout.
- Stage 26 is integrated into the source overlay and core test suite, not yet certified by a full upstream build or live server run.

## Patch-path correction

The Stage 21–25 patch originally used package-root paths (`org/...`) while the adapter module compiles from `src/main/java`. The active patch has been corrected to `src/main/java/org/...`; Stage 26 uses `src/main/java/org/...` and `src/test/java/...`. A clean upstream patch-chain application remains required.
