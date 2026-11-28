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
