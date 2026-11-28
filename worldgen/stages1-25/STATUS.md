# PNX–Terra Stage 1–26 status — 2026-11-28

**Integration gate: BLOCKED for production.** Stage 26 is integrated into the source overlay and exact-ref patch runner, but the complete modified PNX/Terra/PNX-Terra source chain has not yet passed a clean full upstream application, Gradle build, runtime generation, or crash/restart certification. Do not deploy.

## Completed in this candidate
- Stage 1–25 source overlay and 22 active patches retained.
- Stage 21–25 adapter patch paths corrected to the Maven source root (`src/main/java/org/...`).
- Stage 26 deterministic per-anchor root-placement ordinal integrated into `AnchorSession`, `PlacementTicket`, `PNXServerWorld` placement identity and regression tests.
- Ticket commit now rejects a `PlacementKey` whose ordinal differs from the ordinal allocated by that exact ticket; rejection happens before WAL append and the ticket remains abortable.
- Stage 19 recovery test verifies incomplete-anchor replay starts ordinal allocation at zero and replays the same placement identity idempotently.
- Stage 26 patch targets `src/main/java/org/...` and `src/test/java/...`; exact synthetic Maven-root application matches the integrated source overlay.
- Pinned Terra `FeatureGenerationStage.populate` traversal was inspected: its loops and feature-list iteration are ordered and not parallelized; custom add-ons still require concurrency audit.
- `apply-stages1-26.sh` applies the exact-ref chain; `apply-stages1-25.sh` remains a compatibility wrapper.
- Java 21 core tests: 11/11 suites pass in 10 complete runs for R10 (110/110 suite executions).
- Runtime contract compile, PNX delegate contract compile/behavior tests, PNX BlockEntity NBT/applier/staged delegate contract tests: PASS.
- Active patch syntax: 22/22 PASS.

## Remaining blockers
1. Apply the complete patch chain to clean exact-ref upstream checkouts and resolve all conflicts.
2. Full Gradle builds of modified Terra, PNX and PNX-Terra.
3. Full adapter compilation against actual pinned APIs.
4. Runtime BlockEntity persistence/restart tests; transactional entity-spawn intents; physics executor and water-layer integration.
5. Complete Terra↔PNX property alias mapping, diagnostics routing, and static Origen bounds catalog.
6. End-to-end generation, crash/restart/replay, persistence, lighting, cross-chunk and load tests.
7. Integrated JAR packaging.

The production server and existing world remain untouched.
