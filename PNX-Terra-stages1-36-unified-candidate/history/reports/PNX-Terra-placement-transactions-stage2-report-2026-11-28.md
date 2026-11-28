# PNX-Terra placement transactions — implementation stage 2
Date: 2026-11-28

## Target revisions
- PNX: `c57499365737654eca32df008751ef6bca5af142`
- PNX-Terra: `74318b4a1eae95322550f12d5eb8a76d92a93731`
- Terra API hook required from the companion Terra patch: `PlacementScope`, `PlacementScopeProvider`, `PlacementDescriptor`.

## What this patch implements
The patch `PNX-Terra-placement-transactions-stage2.patch` adds a first PNX-side transaction receiver.

1. `PNXProtoWorld` implements `PlacementScopeProvider` and forwards scope creation to its `ServerWorld`. This is necessary because Terra's feature stage receives the proto-world, not the underlying PNX server-world.
2. `PNXServerWorld` implements the provider and maintains a per-thread, LIFO stack of placement transactions.
3. Block writes made while a placement is open are staged rather than inserted into the PNX `BlockManager`.
4. A nested commit merges its ordered writes and read overlay into its parent. It does not publish to the BlockManager.
5. A nested abort discards only that nested placement.
6. A top-level commit publishes the ordered writes into the generation's existing `BlockManager`.
7. The staged read overlay preserves full PNX `BlockState` values for blocks written by the current transaction or its parents.
8. Entity creation during a transaction is explicitly rejected for now. This is intentional fail-closed behavior: PNX `Entity.createEntity()` registers the entity and fires events during construction, so creating then removing it cannot provide a clean rollback.

## Verified source facts used
- `PNXProtoWorld` is the world passed into Terra's configured generation stages.
- `PNXServerWorld` owns one `BlockManager` per PNX `PopulateStage`.
- `BlockManager.setBlockStateAt(x,y,z,layer,state)` stages a PNX block in that manager's caches/places.
- `BlockManager.applySubChunkUpdate()` currently obtains target chunks with `level.getChunk(..., true)`; it is not a safe cross-chunk deferred queue.
- `BlockManager.getBlockIfCachedOrLoaded()` avoids loading an unloaded chunk, but its fallback is AIR. It cannot distinguish unknown/unloaded from known air.
- PNX's `BlockManager.applySubChunkUpdate()` creates block entities for `BlockEntityHolder` blocks after applying the block states.
- PNX entity initialization registers the entity with its chunk and level and fires `EntitySpawnEvent`.

## What this patch does NOT solve
This is deliberately a transaction-buffering stage, not the completed cross-chunk generation system.

- It does not yet provide a persistent per-target-chunk queue.
- It does not solve generation-order independence for overlapping structures.
- It does not provide stable placement keys/ledger persistence or a conflict winner policy.
- It does not distinguish `UNKNOWN_UNLOADED_CHUNK` from real AIR for read decisions.
- It does not yet stage/defer block-entity loot, sign text, spawner configuration, or inventory changes.
- Entity-bearing structures will fail transactionally until a deferred entity proxy/commit action is implemented.
- The `physics` boolean passed by Terra remains unimplemented; the existing adapter already ignored it.
- The existing `PNXServerWorld.apply()` path still has the old neighboring-chunk access behavior and can force-load chunks. This patch does not claim otherwise.
- Water preservation is still heuristic. The staged implementation uses a non-loading read with AIR fallback; this is not a universal waterlogging bridge.

## Integration order
1. Apply the corrected Terra API/feature/TerraScript placement-scope patch to the pinned Terra source.
2. Build/publish that Terra fork artifact with a distinct version.
3. Apply this PNX-Terra patch against the pinned PNX-Terra source.
4. Point PNX-Terra's Terra dependency at the fork artifact.
5. Compile against the exact PNX 3.0.6 source/API.
6. Only after all code stages are complete, run isolated tests; do not install on the production server.

## Validation status
- Source signatures and relevant control flow were inspected at the pinned commits listed above.
- The patch file has been authored, but it has NOT been applied to a checkout, compiled, or runtime-tested.
- No production server files, world data, player data, plugins, or configuration were changed.

## Patch-file integrity check
Both patch files pass Git's unified-diff parser (`git apply --numstat`). This validates patch syntax only; it does not prove that the hunks apply cleanly to a checkout or that the code compiles. The Terra companion patch was corrected after detecting a malformed `BufferedWorld` hunk.
