# Terra Placement Scope — implementation stage 1

**Target Terra revision:** `05ddc8b8a66e17412670647cce78ef837b091256` (6.6.1-BETA source revision from the project dossier)

## What was inspected

The following files were fetched from the exact pinned revision:

- `common/api/.../world/WritableWorld.java`
- `common/api/.../world/ReadableWorld.java`
- `common/api/.../world/BufferedWorld.java`
- `common/api/.../structure/Structure.java`
- `common/api/.../registry/key/Keyed.java`
- `common/addons/generation-stage-feature/.../FeatureGenerationStage.java`
- `common/addons/config-feature/.../ConfiguredFeature.java`
- `common/addons/structure-terrascript-loader/.../StructureFunction.java`
- `common/addons/structure-terrascript-loader/.../StructureScript.java`

## Confirmed execution path

1. `FeatureGenerationStage.populate()` resolves the selected structure and calls `Structure.generate(...)`.
2. The boolean returned by `Structure.generate(...)` is currently ignored by `FeatureGenerationStage`.
3. TerraScript's `StructureFunction` creates a nested `BufferedWorld` with offsets and invokes the child structure.
4. `BufferedWorld` delegates writes to its underlying world after applying its offset.
5. `StructureScript.applyBlock()` catches `RuntimeException` and returns `false`; therefore the caller can use the existing boolean as a success/abort signal.

This confirms the correct transaction boundaries: one root scope around each feature placement, plus nested savepoints around each TerraScript `structure(...)` call.

## Prototype patch

`Terra-placement-scope-prototype.patch` adds:

- `PlacementScope`: explicit `commit`, `abort`, and abort-on-close semantics.
- `PlacementScopeProvider`: optional platform hook, so other Terra platforms retain the old behavior.
- `PlacementDescriptor`: feature/structure identity, anchor, and rotation.
- `BufferedWorld` propagation: placement scopes are delegated through every nested buffer while accumulating offsets.
- Root placement scope in `FeatureGenerationStage`: commits only when `Structure.generate()` returns `true`; aborts on `false` or exception.
- Nested savepoint in `StructureFunction`: commits or aborts according to the nested structure result.

## Important status and limitations

This is a **prototype source patch**, not a completed Terra fork and not a PNX-Terra implementation.

It has not been applied to a full Terra checkout, compiled, unit-tested, or runtime-tested. The patch also does not yet solve all deterministic identity requirements: repeated nested calls at the same absolute origin need a stable nested-call path/ordinal, and the PNX provider must inherit the parent feature identity rather than treating `terrascript:nested` as a globally unique feature. Those are mandatory before this becomes production-ready.

The patch alone does not change server behavior. PNX-Terra still needs the provider/coordinator implementation that buffers block writes, block-entity actions, and entity spawns; merges child savepoints into parents; and only publishes a committed root placement to the cross-chunk coordinator.

No upstream repository was modified. No server files, worlds, plugins, player data, or production configuration were touched.
