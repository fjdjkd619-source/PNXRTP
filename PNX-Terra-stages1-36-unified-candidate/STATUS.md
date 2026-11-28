# PNX–Terra Stage 1–36 status — 2026-11-28

**Integration gate: BLOCKED for production. Do not deploy.** Stages 27–36 core contracts and adapter overlay are added, but the complete patch chain has not been applied and built against clean exact-ref PNX/Terra/PNX-Terra repositories, and no server runtime/recovery certification exists.

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
6. The complete Stage 1–36 patch chain has not passed a clean exact-ref upstream application and Gradle build.
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

The full source-overlay audit is recorded in `validation/FULL-AUDIT-STAGES-1-42-R15.md`. Result: **BLOCKED for production**. Key findings: Stage 7/18 PNX patch overlap must be rebased and checked on clean upstream repositories; stages 37–42 have no active upstream apply patches/runner; WAL has no compaction or bounded retention; PNX physics and generic water-layer handling remain unwired; Terra entity spawning remains fail-closed; Origen static bounds and Terra↔PNX aliases/diagnostics are incomplete. The Java 21 core suite passes, but this does not certify the full upstream integration or live server behavior. Do not deploy.
