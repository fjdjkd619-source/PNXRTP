# Stage 1–26 gate status — 2026-11-28

**Production gate: BLOCKED.** Stage 26 is integrated into the source overlay and exact-ref patch runner. The complete modified PNX/Terra/PNX-Terra chain has not yet passed clean full-upstream application, a full Gradle build, runtime generation, or crash/restart certification. This candidate is not deployable.

## Current r9 candidate

- Complete recovery archive: `PNX-Terra-stages1-26-unified-candidate-2026-11-28-r9.zip`
- SHA-256: `f6ad40571e087ae9879fff5aa473ef9f7167a01aab6b9d3bd1382213721efd03`
- 44 Java source files, 11 test classes, 22 active patches, exact-ref apply runner, validation harnesses, and 13 original archives.
- Library backup: `/PNX-Terra-Backups/PNX-Terra-stages1-26-unified-candidate-2026-11-28-r9(1).zip`; checksum sidecar stored beside it.
- Earlier r3–r8 archives are preserved; no prior copy was overwritten or deleted.

## Stage 26 integrated

- `AnchorSession.beginPlacement()` allocates a unique per-anchor ordinal under the scheduler lock.
- Exhaustion is rejected before overflow; `Math.incrementExact` provides a second guard.
- `PlacementTicket.ordinal()` is written into `PNXServerWorld`'s `PlacementKey`, replacing the repeated `0L`.
- Counter scope is one anchor session; an incomplete anchor replay starts from zero so identical ordered Terra placement calls reproduce the same keys.
- Regression assertion verifies ordinals 0 and 1 for consecutive tickets and verifies abort handling.
- Patch: [Stage 26 placement ordinal](patches/26-stage26-deterministic-placement-ordinal.patch).

## Corrected integration defect

The Stage 21–25 adapter patch in the r9 archive previously used package-root paths instead of Maven's `src/main/java` root. Its paths are now corrected. Stage 26 uses `src/main/java/org/...` and `src/test/java/org/...`. The exact Stage 26 patch was applied to a clean synthetic Maven-shaped tree and the resulting files byte-match the integrated overlay.

## Validation

- Java 21 core suite: 11/11 test classes pass; 10 complete runs = 110/110 suite executions.
- Runtime journal contract compile: PASS.
- PNX delegate contract compile/behavior tests: PASS.
- PNX BlockEntity NBT codec, intent applier, staged Sign/MobSpawner/Container contract tests: PASS.
- Active patch syntax: 22/22 PASS.
- Stage 26 Maven-root patch application and exact-file comparison: PASS.
- ZIP integrity: PASS.

These are overlay/contract validations, not a complete upstream build.

## Remaining blockers

1. Apply the full patch chain to clean pinned upstream checkouts and resolve all conflicts.
2. Build modified Terra, PNX and PNX-Terra together; compile the full adapter against actual upstream APIs.
3. Complete runtime BlockEntity persistence/restart tests.
4. Implement transactional entity-spawn intents and durable recovery.
5. Connect physics execution and water-layer mapping.
6. Complete Terra↔PNX property aliases, global diagnostics routing, and the static Origen bounds catalog.
7. Run end-to-end generation, crash/restart/replay, persistence, lighting, cross-chunk and load tests.
8. Produce and verify the integrated JAR.

Production server and existing world remain untouched. Do not deploy until the gate passes.

Historical r8 details are preserved in [STATUS-HISTORY-R8.md](STATUS-HISTORY-R8.md).
