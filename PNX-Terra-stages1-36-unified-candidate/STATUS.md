# PNX–Terra Stage 1–42 status — 2026-11-28

**Integration gate: BLOCKED for production. Do not deploy.** The complete Stage 1–42 patch chain now applies successfully to clean pinned PNX, Terra and PNX-Terra checkouts; Java 21 overlay and contract validation passes. The full Gradle build is still in progress/retry after a prior Terra Loom `ClosedFileSystemException`, and no server runtime/recovery certification exists.

## Added in Stage 27–36 candidate
- Stage 27: immutable entity-spawn intents; deterministic UUID from placement identity + spawn ordinal; global UUID ownership collision rejection; WAL version 4 with backwards read compatibility for versions 1–3; target projection and digest coverage; fail-closed finalization apply/rollback/confirm hooks.
- Stage 28: deterministic target-scoped physics batch dispatch on an injected Level executor; duplicates collapse and coordinate order is stable.
- Stage 29: strict explicit property alias regression coverage; unknown property values remain rejected.
- Stage 30: structured stage guard emits one diagnostic and rethrows the original failure; interrupt status is restored.
- Stage 31: bounds manifest duplicate/evidence/dependency validator.
- Stage 32: aggregate resource close sequence; runtime close now attempts both scheduler and WAL close.
- Stage 33: exact-ref upstream Gradle verification script.
- Stage 34: release packager refuses to run until upstream build and runtime certification markers exist and STATUS is unblocked.
- Combined Stage 1–34 runner, Stage 27–34 adapter overlay, Stage 1–35 runner, and Stage 1–36 runner are included.
- Stage 35 adds a PNX rollback-only entity close seam and an idempotent finalization applier.
- Stage 36 corrects the Stage 35 lifecycle gap: PNX entities are constructed and spawn-event validated without chunk/Level registration or ticking; they enter only the chunk save set before durable write, then join Level ticking and become network-visible only after the save succeeds. Replay matches by durable UUID; rollback owns only entities created by the current batch.

## Core validation
- Java 21 `javac --release 21 -Xlint:all -Werror` core compile: PASS (R12 10 rounds; R13 repeat 6 rounds).
- Existing Stage 15–25 suites: PASS.
- Stage 27 WAL v4 round-trip, v3 backward-read fixture, deterministic UUID ownership collision, entity finalization success and rollback-on-save-failure: PASS.
- Stage 28 deterministic physics ordering/dedup/target guard: PASS.
- Stage 29 explicit property alias/unknown-value rejection: PASS.
- Stage 30 diagnostic/rethrow behavior: PASS.
- Stage 31 bounds manifest validation: PASS.
- Stage 32 close-all/suppressed-failure behavior: PASS.
- Stage 27–34 patch parse and synthetic Maven-root apply/byte comparison: PASS.
- Stage 35–36 PNX entity-applier contract compile/tests: PASS (pre-save isolation, chunk-only save staging, post-save Level activation/client publication, UUID replay, rollback ownership, payload rejection, cross-chunk rejection).
- Stage 36 finalization-order regression: PASS (`apply → chunk save staging → durable save → activation`).
- Stage 35 adapter patch synthetic application: PASS.
- Stage 36 deferred PNX API patch synthetic context application after the Stage 35 rollback seam: PASS.
- Stage 35–36 entity-applier contract suite also rejects cancelled factories and rolls back earlier entities when a later batch intent fails.

## Still not implemented or certified
1. `PNXServerWorld.spawnEntity` remains fail-closed. Stage 35–36 wires durable PNX entity finalization, but Terra spawn-call capture and a deferred Terra entity proxy are not implemented, so entity-bearing structures remain disabled.
2. `PnxChunkHandle` still rejects physics intents unless a verified PNX physics executor is supplied; the generic dispatcher is a contract, not the live Level callback.
3. The generic property alias bridge is not populated with a complete verified Terra↔PNX alias catalog or wired to every PNX delegate property.
4. `GenerationStageGuard` is not yet wrapped around every Terra/PNX stage entry point.
5. The built-in Origen catalog still has only the previously audited profiles plus a strict runtime fallback.
6. Clean exact-ref Stage 1–42 patch application now passes. Full Gradle build of the modified upstream checkouts remains unconfirmed; latest serialized build run is pending.
7. Full runtime generation, BlockEntity/entity persistence, physics, crash/restart/replay, lighting, cross-chunk and load tests remain outstanding.
8. Integrated JAR packaging is blocked.

The production server, existing world, player data and installed plugins remain untouched. Do not deploy this candidate.

## Stage 37-42 WAL hardening candidate (R15)

- Stage 37/38: WAL recovery now truncates only a proven incomplete final frame. Complete-frame CRC, header, payload and sequence corruption fail closed without modifying the original WAL. Torn suffixes are preserved through collision-safe evidence creation.
- Stage 39: recovery avoids a second frame-sized allocation while retaining the 64 MiB frame bound.
- Stage 40: sequence/idempotency lookups use indexes populated on append and replay.
- Stage 41: a journal health snapshot reports size, event counts, last sequence, recovery duration and recovered-tail bytes.
- Stage 42: close is idempotent; appends after close are rejected; failed append rollback poisons the journal.
- Added Stage37WalRecoveryTest and changed Stage16JournalTest so a complete bad-CRC frame must fail closed and remain byte-for-byte unchanged.

**Independent core validation: PASS.** GitHub Actions run 46146027245 compiled the source overlay with Java 21 (`javac --release 21 -Xlint:all -Werror`) and passed all 18 test suites, including Stage37WalRecoveryTest (28 assertions). Full output: validation/CORE-TESTS-2026-11-28-R15.txt. The R15 repository contains a Java source overlay and scripts, not a complete Gradle project: no build.gradle or Gradle wrapper is present in the candidate root. The exact-ref upstream PNX/Terra/adapter build, clean patch application and runtime certification remain outstanding. Production status remains BLOCKED; do not deploy.


## Full Stage 1–42 audit — R15

The full source-overlay audit is recorded in `validation/FULL-AUDIT-STAGES-1-42-R15.md`. Result: **BLOCKED for production**. Key findings: Stage 7/18 PNX patch overlap must be rebased and checked on clean upstream repositories; Stage 37–42 now have a WAL adapter patch and apply runner; that patch is validated against the extracted Stage 36 overlay baseline, but not yet against a full clean PNX-Terra checkout; WAL has no compaction or bounded retention; PNX physics and generic water-layer handling remain unwired; Terra entity spawning remains fail-closed; Origen static bounds and Terra↔PNX aliases/diagnostics are incomplete. The Java 21 core suite passes, but this does not certify the full upstream integration or live server behavior. Do not deploy.


## WAL encoder follow-up — 2026-11-28

- Placement serialization is now bounded during encoding at `MAX_FRAME` (64 MiB), before each write reaches the backing buffer. An oversized placement fails with `IOException` instead of first accumulating an arbitrarily large byte array.
- Added `PlacementJournalEncodingLimitTest` for exact-boundary acceptance, single-byte and bulk overflow, no backing-buffer growth on rejection, and zero-byte limits.
- Java 21 full source-overlay validation: [GitHub Actions 46146537903](https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46146537903) — SUCCESS. All workflow steps passed, including platform-neutral regression suites and all existing contract checks.
- This does not implement WAL compaction/checkpointing or bound retained journal history/indexes. Stages 37–42 now have `37-42-wal-hardening.patch` and are included in `apply-stages1-42.sh`. Production remains BLOCKED; do not deploy.


## R15 follow-up: WAL and pinned PNX patch integration

- Added `patches/37-42-wal-hardening.patch` and `apply-stages1-42.sh`. The Stage 37–42 patch applies to the Stage 36 adapter overlay baseline and byte-matches the R15 source/test overlay.
- Rebased Stage 18 on Stage 7, corrected/rebased Stage 11 durable LevelDB writes, and rebased Stage 36 deferred entity lifecycle on the exact pinned PNX `Entity.java` after Stage 35.
- GitHub Actions [46147409727](https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46147409727) — SUCCESS. The workflow applied PNX patches in order against pinned PNX `c57499365737654eca32df008751ef6bca5af142`: 7 → 11 → 18 → 22 → 35 → 36. Java 21 core/contract checks also passed; Stage37 WAL recovery test: 36 assertions; bounded encoder test: 6 assertions.
- WAL placement encoding is now bounded during serialization at `MAX_FRAME`, and append avoids a second full-size frame buffer allocation.

**Production remains BLOCKED.** This does not establish a complete Terra + PNX + PNX-Terra adapter patch-chain build, a full Gradle compile, integrated JAR packaging, live server generation, restart/replay correctness, or long-duration memory/disk stability. WAL compaction/checkpointing, PNX physics execution, generic water-layer policy, Terra entity-spawn capture, complete Origen bounds, and complete Terra↔PNX property aliases remain open.


## R15 exact-upstream integration recheck — 2026-11-28

- The earlier successful application against Terra `05ddc8b8a66e17412670647cce78ef837b091256` was against Terra 7.0.0 and is not the target runtime. It is superseded by the Terra 6.6.1 correction below.
- Stage 37–42 production patch is now included in `apply-stages1-42.sh`; its source is checked against the Stage 36 overlay baseline. Regression tests remain in the validation overlay rather than being injected into production source patch.
- Java 21 validation run [46150102841](https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46150102841): SUCCESS. This includes shell syntax, every active patch parse, exact Stage 37–42 patch application/byte comparison, Stage 7 then Stage 18 PNX patch checks, all platform-neutral tests, and PNX delegate/BlockEntity/runtime/entity-spawn/Stage 36 API contract checks.
- The first full Gradle attempt reached Terra's `:common:api:compileJava` but failed in upstream Loom task `:platforms:bukkit:common:validateAccessWidener` with `java.nio.file.ClosedFileSystemException`. This is an upstream build-tool failure, not a reported Java source compilation error. The verification script was changed to disable configuration cache, serialize Gradle workers (`--max-workers=1`) and emit stack traces; the rerun is [46150102841](https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46150102841) and must complete before marking upstream build PASS.
- Stage 15 follow-up also fixes forwarding of `BufferedWorld` offsets into the guarded placement origin. Unidentified structure implementations now retain a stable fallback ID via `StringIdentifiable` or class name instead of being skipped when the guard host is active.

**Still BLOCKED:** WAL compaction/checkpoint and retained-index growth; PNX physics execution; generic water-layer integration; Terra entity-spawn capture; full Origen bounds and Terra↔PNX property aliases; live PNX server generation/restart/replay/soak tests; medieval-fantasy ConfigPack content. Do not deploy.


## Corrected Terra target — 6.6.1, 2026-11-28

- Correct target identified: PNX-Terra's pinned adapter declares `org.powernukkitx:Terra:6.6.1-BETA`; the Terra source commit used for this version is `016961c19c41c2501737599175937ae40699beb1`. The former `05dd...` pin was Terra 7.0.0 and must not be used for this adapter.
- Rebased Stage 1 and Stage 15 Terra patches to the 6.6.1 API (`com.dfsek.terra.api.util.Rotation`, `com.dfsek.terra.api.util.vector.Vector3Int`, and `java.util.Random`). The runner now checks the 6.6.1 commit.
- GitHub Actions [46150876628](https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46150876628): the complete Stage 1–42 patch application step passed against clean pinned PNX `c57499365737654eca32df008751ef6bca5af142`, Terra 6.6.1 `016961c19c41c2501737599175937ae40699beb1`, and PNX-Terra `74318b4a1eae95322550f12d5eb8a76d92a93731` checkouts. The exact-upstream build step is running; it builds the patched Terra 6.6.1 Bukkit-common shaded jar, installs it under the adapter's expected Maven coordinates, publishes patched PNX to Maven Local, then builds the adapter against those local patched artifacts.
- The Java 21 overlay/contract job for this same run is still in progress at the time of this update. Do not mark the complete workflow PASS until both jobs finish successfully.

**Production remains BLOCKED.** Do not deploy until the full build, server runtime and recovery tests pass.
