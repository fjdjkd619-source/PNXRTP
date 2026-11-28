# Validation report

Environment: OpenJDK 21.0.12.1, `javac 21.0.12.1`.

Command used:

```bash
./validation/run-core-tests.sh
```

Compilation flags: `--release 21 -Xlint:all -Werror`.

Result: PASS for the independent core and 12 test classes. The 11 test classes were run for 10 complete rounds in this candidate revision (110 suite executions total), all PASS. The suites include regression tests proving that an already-accepted in-flight anchor is not resubmitted while PNX owns the queue slot, that an accepted request is retried after PNX releases the slot without a durable completion marker, that an anchor with an active AnchorSession is not recursively requested by its own target barrier, and that unknown Origen scripts are constrained by a hard runtime envelope (x/z +/-32, y +/-128) with entity spawning disabled.

The core compile deliberately excludes classes requiring the complete PNX/Terra upstream classpaths: `TerraGenerator`, `PNXServerWorld`, `PnxChunkHandle`, `PnxCrossChunkFinalizer`, and `journal/pnx/*`. Separate Java 21 contract-stub checks include `PnxGenerationJournalRuntime` (confirming the exact pinned `Level.isChunkGenerating(x,z)` API) and the actual `PNXBlockStateDelegate`/`PNXChunkDelegate`/`PNXBlockEntityDelegate` source files. The delegate behavior test verifies exact property-schema matching, property get/set, canonical property serialization, rejection of mismatched value schemas, real chunk X/Z delegation, loaded BlockEntity read wrapping, and fail-closed mutation/update methods. These are narrow contract checks, not a full adapter compile. Therefore these checks are **not** a PNX/Terra Gradle build and do not prove that the entire adapter compiles against the full pinned repositories.

Separate upstream baseline builds were run in GitHub Actions (not from this local harness): Terra 6.6.1-BETA pinned commit built successfully; PNX successor `ff86b15d18e3699459c7913eb1ebec16521b7ad0` plus the unmodified PNX-Terra 1.0.0 adapter built successfully on Java 21 and produced a baseline artifact. The original PNX `c574993...` baseline failed due to the camera Protocol Beta7 API mismatch, and original PNX-Terra failed because floating `nightly-SNAPSHOT` resolved Java 25 class files. None of those builds included this candidate overlay.

Additional checks: all 22 active patch files parse with `git apply --stat`; all 13 preserved original ZIP archives pass CRC/readability checks; the supplemental hardening patch applies cleanly to the reconstructed Stage 21–25 baseline overlay. `apply-stages1-26.sh` passes shell syntax validation and applies the Stage 26 ordinal patch; `apply-stages1-25.sh` remains as a compatibility wrapper and pins the exact upstream refs.

Not run: complete active patch application to clean full upstream checkouts, full Gradle build of the modified stages 1–26 candidate, server runtime test, integrated candidate JAR packaging.

## Block Entity integration source audit

The exact PNX 3.0.6 and Terra source lifecycles have been audited. `BlockEntity.createBlockEntity` immediately registers the object in both chunk and level; `Chunk.addBlockEntity` closes an existing tile at the same coordinate. Stage 22 now captures supported TerraScript Sign/MobSpawner/Container state into detached journal intents, validates canonical NBT/hash markers and collisions, applies intents through the owning Level scheduler, durably saves before the next spawner heartbeat, and rolls back new registrations—including constructor failures swallowed by the PNX factory—if creation or the early save fails. Narrow contract tests pass for sign text, spawner properties, inventory serialization, replay, collision refusal, and rollback. Full upstream dependency compilation and runtime behavior remain unverified. See `BLOCK-ENTITY-UPSTREAM-API-AUDIT-2026-11-28.md` and `TERRA-STRUCTURE-BLOCKENTITY-CAPTURE-AUDIT-2026-11-28.md`.

The `validation/run-pnx-be-contract-compile.sh` harness compiles the NBT codec/applier, staged Sign/MobSpawner/Container delegates, and Level executor against narrow API contracts. It checks hash tampering, state mapping, inventory serialization, rollback, and dispatch. It is not a substitute for the full PNX/Terra dependency build.


## Stage 26 regression validation

The Stage 26 patch adds the per-anchor ordinal to `AnchorSession`/`PlacementTicket`, writes it into `PlacementKey`, and enforces that a ticket can only commit a record carrying its own ordinal. Tests cover consecutive ordinals (0, 1), mismatch rejection, abort after rejection, and deterministic replay from ordinal zero. The exact patch was reconstructed against a clean synthetic Maven-root baseline, applies cleanly there, and the resulting scheduler, adapter and test files byte-match the integrated overlay. The pinned Terra feature-stage traversal was inspected: its placement iteration uses ordered loops and ordinary `forEach`, not parallel streams. Custom add-ons still require concurrency audit. The Stage 21–25 and Stage 26 patch path roots were corrected to Maven source/test roots; full clean upstream application remains pending. See `STAGE26-TICKET-ORDINAL-INVARIANT-2026-11-28.md`.


## Stages 27–34 validation (R12 core overlay)

- Java 21 core compile: `--release 21 -Xlint:all -Werror` — PASS.
- R12 baseline: 18 test suites × 10 complete rounds = 180/180 suite executions PASS. R13 repeat: 18 suites × 6 complete rounds = 108/108 PASS.
- Stage 27: WAL v4 entity spawn round-trip, v3 backward-read fixture, stable UUID collision rejection across placements, entity finalization and rollback on durable-save failure — PASS.
- Stage 28: target-scoped physics ordering, deduplication and cross-chunk rejection — PASS.
- Stage 29: explicit property alias mapping and unknown-value refusal — PASS.
- Stage 30: structured diagnostic publication, exactly-once report and original exception propagation — PASS.
- Stage 31: bounds manifest duplicate/evidence validation — PASS.
- Stage 32: close-all sequencing and suppressed close failures — PASS.
- Stage 27–34 overlay patch: `git apply --check`, synthetic Maven-root apply and byte-for-byte source/test comparison — PASS.
- All 25 patch files parse with `git apply --stat`; all shell scripts pass `bash -n`.
- Release packager correctly refuses while the production gate is blocked — PASS.

The Stage 27–34 tests validate platform-neutral contracts only. The Stage 35–36 contract suite validates the PNX entity adapter against signature-matched stubs, not a full upstream PNX runtime. Terra entity spawn-call capture, live Level physics callback, complete Terra↔PNX alias table, global stage wrappers, and full Origen catalog are not yet wired or runtime-certified. No full upstream Gradle build or server runtime certification has been performed.


## Stage 35 entity-spawn adapter

- `validation/run-entity-spawn-contract-compile.sh`: PASS under Java 21 (`-Xlint:all -Werror`).
- Contract cases: delayed publication, UUID-idempotent replay, rollback limited to newly created entities, opaque payload rejection, and cross-chunk rejection.
- PNX `Entity.close()`/private `close(boolean)` lifecycle seam was inspected at both `c57499365737654eca32df008751ef6bca5af142` and `ff86b15d18e3699459c7913eb1ebec16521b7ad0`; both contain the same rollback insertion point.
- The Stage 35 patches were synthetically applied to a clean R12 overlay baseline. No complete upstream Gradle build or live runtime certification was possible in this environment. Terra `spawnEntity` still fails closed until spawn-call capture/deferred Terra handles are implemented.


## Stage 36 — deferred entity lifecycle

- PNX `Entity.init()` source inspection identified that ordinary entity construction registers into the chunk and Level and schedules ticking before the durable chunk save.
- Stage 36 adds a scoped generation-only construction API. Accepted entities are staged in the chunk save set only, then added to Level ticking and published to clients after synchronous save success.
- Entity applier contract and finalization-order regression tests pass under Java 21.
- The PNX API patch is based on inspected pinned/successor source contexts but has not been applied to a complete upstream checkout or compiled by Gradle.
- Terra `ServerWorld.spawnEntity` capture remains fail-closed; no entity-bearing Origen structures are enabled.


## Stage 37 WAL encoder allocation guard — follow-up R15

- `PlacementJournal.encodePlacement` now serializes through a bounded output stream capped at `MAX_FRAME` (64 MiB). Previously, the complete placement payload was accumulated in an unbounded `ByteArrayOutputStream` and checked only afterward, so an oversized placement could allocate excessive heap before rejection.
- The bound is enforced before each byte/bulk write reaches the backing buffer; exactly-at-limit payloads remain valid and over-limit writes fail with `IOException`.
- `PlacementJournalEncodingLimitTest` covers exact-limit acceptance, single-byte overflow, bulk-write overflow, backing-buffer non-growth on rejection, and a zero-byte limit.
- GitHub Actions run [46146537903](https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46146537903), commit `0f1d6508b57581d4825e9e4591f163512e3ef69e`: SUCCESS. Java 21 compilation, platform-neutral suites, PNX delegate contract, BlockEntity contract, runtime contract, entity-spawn contract, Stage 36 API fixture and shell/patch checks all passed.
- This closes the pre-serialization allocation defect for placement frames. It does **not** solve append-only WAL growth, retained in-memory indexes, compaction/checkpointing, or the missing Stage 37–42 upstream patch/apply chain.


## Exact pinned PNX patch-chain validation — follow-up R15

GitHub Actions [46147409727](https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46147409727) — **SUCCESS** on commit `adc59b3a224bc4a9d3c19c752200074131d0e7a7`.

The workflow checks and applies the PNX patches against the exact pinned PowerNukkitX commit `c57499365737654eca32df008751ef6bca5af142` in the active order: Stage 7 async pipeline → Stage 11 durable LevelDB write → rebased Stage 18 async dependency API → Stage 22 spawner delay → Stage 35 rollback API → rebased Stage 36 deferred entity lifecycle. This confirms patch applicability and ordering on the pinned PNX source; it is **not** a PNX Gradle compile.

Corrections made to reach this result:
- Stage 18 PNX patch was rebased on top of Stage 7 rather than retaining conflicting pre-Stage-7 method bodies. It now adds the named-stage generation queue and future-returning API without reverting Stage 7 failure callbacks.
- Stage 11 durable write patch was rebased to the actual `LevelDBStorage` API and corrected to construct `WriteOptions`, call `sync(true)`, then pass it to `db.write`.
- Stage 36 entity patch was rebased on the actual pinned `Entity.java` after Stage 35. The verification checks staged spawn-event handling occurs before chunk/Level registration and that registration for durable save and activation methods exist.
- The Stage 37–42 adapter patch applies to the extracted Stage 36 overlay baseline and its output byte-matches the R15 source/test overlay.
- `apply-stages1-42.sh` now applies the full Stage 1–36 runner followed by the Stage 37–42 WAL patch.

Latest Java 21 source-overlay test output: `Stage37WalRecoveryTest: PASS (36 assertions)`; `PlacementJournalEncodingLimitTest: PASS (6 assertions)`. All other core and adapter contract jobs in the workflow also passed.

Still not certified: clean exact-ref Terra and PNX-Terra adapter patch application as one full chain, full Gradle builds of modified PNX/Terra/adapter, integrated JAR packaging, live server generation, crash/restart/replay tests, and long-duration memory/disk soak. WAL compaction/checkpointing and unbounded retained journal history remain open. Production gate remains BLOCKED.
