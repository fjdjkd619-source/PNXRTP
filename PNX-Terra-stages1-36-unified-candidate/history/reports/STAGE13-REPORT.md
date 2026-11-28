# Stage 13 — Origen 2.2.0 structure-boundary inventory

Source: `origen-v2.2.0.zip`  
Pinned Git HEAD: `228426bb192a8d6029952aae1a77008dea5f3cda`

## Scope and counts

- Archive integrity: ZIP CRC check passed.
- TerraScript files inspected: 159.
- YAML files scanned for feature structure references: 608.
- Feature `structures:` references extracted: 158.
- Nested `structure()` calls found: 123.
- Nested calls with a statically identifiable string target in the 4th argument: 96.
- Calls whose target is dynamic/mask-based or otherwise not statically identifiable: 27.
- Scripts using loops: 102.
- Scripts using `getBlock()`: 47.
- Scripts with root feature references resolved: 73.

## Safety result

**No finite AABB has been certified for any script in this pass.** All 159 scripts are registered as `UNKNOWN`. This is intentional and correct: the TerraScript language includes loops, runtime variables, random values, dynamic structure targets, and world-dependent `getBlock()` branches. A naive scan of `block(x,y,z)` tokens cannot prove an upper bound.

In particular:
- `waterfall.tesf` searches downward up to 50 blocks and writes a variable horizontal span.
- `mangrove_tree_procedural.tesf` uses randomized roots and branches; roots can extend up to 30 blocks and the branch/leaf substructures are nested.
- `boulder*.tesf` use randomized radii and nested palette/ore structures.
- Several tree scripts choose nested leaf structures through variables and rotation alternatives.
- `dripstone_column.tesf` uses a replaceable-mask structure target and dynamic coordinate expressions.

## Registry behavior expected by the scheduler

1. A structure can be scheduled with `VERIFIED` bounds only after its complete transitive nested-call closure has been bounded.
2. A dynamic nested target is not treated as an empty dependency. It propagates `UNKNOWN` to the parent.
3. Bounds compose by translating child AABBs by the nested call offset, then applying the selected Terra rotation.
4. Any loop bound that depends on runtime/world/random values must have an explicit conservative maximum or remain `UNKNOWN`.
5. `UNKNOWN` must prevent sealing every target chunk that could be affected. If the affected set itself cannot be bounded, the safe behavior is to delay sealing within the configured maximum dependency horizon or fail generation; never guess.
6. Once all placements from every source anchor capable of touching a target chunk are durably committed, the target may enter finalization. A late journal append invalidates the target digest and requires re-application before durable acknowledgement.

## Important implementation boundary

This is a source inventory and conservative registry, **not** a Terra runtime instrumentation patch. It does not claim that Terra's `Structure` API exposes extents. A later integration needs one of:
- explicit bounds metadata attached to each configured structure/feature;
- TerraScript AST/interpreter instrumentation that records and enforces a declared maximum;
- a conservative pack-level maximum extent validated against every script path.

The source pack has not been modified. No PNX/Terra build or runtime server test was performed.
