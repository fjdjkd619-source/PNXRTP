# Stage 26 — ticket/record ordinal binding audit

## Change

A placement ticket now validates that the `PlacementKey.placementOrdinal` on the record being committed equals the ordinal allocated by that exact ticket. World and anchor ownership checks remain in place. A mismatch is rejected before the journal append, and the ticket remains abortable so the anchor's in-flight count can be released safely. The regression test also snapshots the WAL sequence and verifies that a rejected mismatch writes no journal frame.

## Why

Allocating a unique ordinal is not sufficient if a caller can accidentally commit a record with another ordinal. That would silently break the one-ticket/one-root-placement identity contract and could create duplicate or non-replayable journal identities. The commit boundary is the correct place to enforce the invariant.

## Replay semantics

A new `AnchorSession` starts its per-anchor ordinal sequence at zero. If a process stops after some placements have been committed but before the anchor completion marker, replay re-executes the deterministic Terra feature traversal from the beginning. Matching root placements therefore receive the same ordinal sequence and journal identity. `Stage19RecoveryTest` now explicitly asserts that replay starts at ordinal zero and commits the same record idempotently.

The pinned Terra `FeatureGenerationStage.populate` implementation traverses resolution cells, ranges, subcells, feature lists, and suitable Y coordinates with ordinary ordered loops/`Iterable.forEach`; it does not parallelize this feature traversal. This supports stable per-anchor ordering for that stage. Other Terra add-ons or custom feature implementations must still be audited for asynchronous/concurrent root placement before production certification.

## Regression coverage

- `Stage17DependencySchedulerTest`: ordinals 0 and 1 are distinct; a ticket rejects a record with a mismatched ordinal; rejected ticket can be aborted; valid ticket then commits; mismatch rejection leaves the WAL sequence unchanged.
- `Stage19RecoveryTest`: an incomplete anchor replay starts at ordinal zero and replays the same placement idempotently.
- Stage 26 patch was regenerated from a clean synthetic Maven-root baseline, passed `git apply --check`, and the applied scheduler, adapter, and test files byte-match the integrated source overlay.

## Limits

This is a local candidate-level regression check. It does not replace full patch application to exact upstream checkouts, full Gradle builds, or runtime crash/restart tests.
