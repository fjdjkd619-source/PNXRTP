# Stages 27–34 — durable intent completion and release gates

These stages extend the Stage 1–26 source overlay. They are intentionally split into a core overlay patch and separate PNX-specific adapters. A passing core test does not certify PNX entity construction, physics callbacks, or a full server runtime.

## Stage 27 — durable entity-spawn intent format
- Adds immutable `EntitySpawnIntent`, canonical namespaced type validation, finite transforms, bounded payloads, deterministic UUID derivation from placement ID + local spawn ordinal.
- Extends `PlacementRecord`, target-chunk indexing, projection, digest and WAL to version 4. WAL versions 1–3 remain readable.
- Rejects duplicate entity UUID ownership across distinct placement keys.
- Extends finalization with fail-closed spawn-apply/rollback/confirm hooks. The actual PNX entity factory/registry codec remains disabled; `PNXServerWorld.spawnEntity` still fails closed until that exact adapter is verified.

## Stage 28 — deterministic physics dispatch contract
- Adds a target-chunk-scoped, deduplicated, coordinate-sorted dispatcher that runs only on the supplied Level executor.
- The PNX-specific physics callback is not wired into `PnxChunkHandle`; unsupported physics remains fail-closed. Callbacks must be safe to retry after a save failure.

## Stage 29 — strict property alias contract
- Adds regression coverage for explicit identifier/property aliases and rejects unknown values rather than coercing them.
- The generic bridge remains a core contract; the production PNX delegate still requires a fully audited alias table before non-identity mappings are enabled.

## Stage 30 — stage diagnostics boundary
- Adds `GenerationStageGuard`, which reports a structured diagnostic exactly once, preserves the original runtime/error failure, wraps checked failures, and restores interruption state.
- Global wrapping of every Terra/PNX stage still requires the exact upstream call-site patch and full build.

## Stage 31 — bounds manifest validation
- Adds duplicate/profile/evidence/dependency validation for externally supplied audited bounds manifests.
- The built-in Origen catalog still contains only its previously audited profiles plus the strict runtime fallback; no uninspected scripts are declared statically safe.

## Stage 32 — runtime shutdown sequencing
- Adds aggregate close sequencing that attempts every resource and retains suppressed failures.
- `PnxGenerationJournalRuntime.close()` now closes scheduler and WAL even if one close operation fails.

## Stage 33 — exact-ref upstream build gate
- Adds `verify-upstream-build.sh`, which checks the pinned source revisions and runs Terra, PNX and adapter Gradle tests/builds in dependency order.
- This script has not passed on the complete modified upstream checkouts in this candidate.

## Stage 34 — fail-closed release packaging
- Adds `package-release.sh`; it refuses to package while STATUS is blocked or upstream/runtime certification markers are absent.
- No production JAR is emitted by this candidate.

## Application
- Apply Stage 1–26 with `./apply-stages1-26.sh --pnx DIR --terra DIR --adapter DIR`.
- Apply Stage 27–34 core overlay with `./apply-stages27-34-overlay.sh --adapter DIR`.
- Combined sequence: `./apply-stages1-34.sh --pnx DIR --terra DIR --adapter DIR`.
- The Stage 27–34 overlay patch was applied to a clean synthetic Maven-shaped Stage 1–26 baseline; resulting `src/main/java` and `src/test/java` trees byte-matched this overlay. This does not substitute for application to the exact upstream repositories.
