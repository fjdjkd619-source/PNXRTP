# Stage 35 — PNX entity-intent finalization adapter

## Implemented

- Added a PNX-side rollback-only close method that bypasses the cancellable `EntityDespawnEvent` path. It is intended only for entities created during a generation transaction that has not yet been acknowledged in the durable placement journal.
- Added `PnxEntitySpawnIntentApplier` for one loaded/generated/initiated chunk. It validates chunk and height ownership, rejects opaque payload bytes until a canonical NBT decoder exists, creates entities with the journal UUID written into PNX's `uuid` NBT field, detects conflicting existing UUIDs, and reuses an identical entity on replay.
- Entity packets are not published until `confirmEntitySpawnIntents`, which the finalization coordinator calls after synchronous chunk persistence. Rollback closes only entities created by the current batch; pre-existing replay matches are never removed.
- Wired the applier into `PnxChunkHandle` and added a Stage 35 patch runner.

## Verification

- The adapter compiles under Java 21 against a contract stub that mirrors the inspected PNX 3.0.6 entity/chunk API signatures.
- Contract cases cover delayed visibility, UUID-idempotent replay, rollback ownership, opaque payload rejection, and cross-chunk rejection.
- The PNX source seam was checked against both pinned c574993 and successor ff86b15 `Entity.java`: both contain the same private `close(boolean)` lifecycle path, so the rollback bridge hunk is based on a verified common API seam.

## Still blocked

- Terra's `ServerWorld.spawnEntity` still fails closed in `PNXServerWorld`. A deferred Terra entity proxy/transaction capture path is not yet implemented; therefore Origen entity-bearing structures remain disabled.
- The entity adapter has not been built inside a clean full PNX/PNX-Terra checkout. Contract stubs are not an upstream Gradle build.
- Physics execution remains fail-closed pending durable neighbor-update semantics.
- No live-server crash/restart/replay or client visibility test has been run.

This stage does not authorize production deployment.
