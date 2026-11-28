# Stage 48 — Terra bounds and PNX block-state coverage (R15)

**Acceptance: NOT PASSED.** This is an exact source inventory and compatibility audit, not a claim that the Terra scripts have certified placement bounds.

## Exact pinned inputs

- Terra ConfigPack: `PolyhedralDev/TerraOverworldConfig`
- ConfigPack commit: `445848bc9f9f28fab1d760f01f9a552d93944681`
- PowerNukkitX commit: `c57499365737654eca32df008751ef6bca5af142`
- PNX block registry: `src/main/resources/mappings/blocks.json`
- Inventory workflow run: [R15 Stage 1–48 patch validation](https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46167478936)
- Artifact: `stage48-terra-configpack-inventory`

The previously checked-in `structure-bounds-registry.json` was generated from a different source head (`228426bb192a8d6029952aae1a77008dea5f3cda`) and contained stale paths. It is not used as the source of truth. The Stage 48 workflow now checks out both exact pinned commits, verifies their HEADs, regenerates the inventory and uploads it as an artifact.

## Exact inventory results

| Measure | Result |
|---|---:|
| TerraScript files | 287 |
| YAML structure definitions | 28 |
| Feature YAML files | 333 |
| Unique feature structure roots | 222 |
| Roots resolving to TerraScripts | 198 |
| Roots resolving to YAML structure definitions | 24 |
| Unmatched feature roots | 0 |
| Unresolved feature YAML files | 0 |
| Duplicate TerraScript IDs | 0 |
| Scripts with dynamic nested targets | 107 |
| TerraScripts with certified bounds | 0 |

All 287 TerraScripts remain `UNKNOWN` for bounds. The exact inventory resolves all 222 feature roots, but resolving an ID is not proof that its footprint, read halo, write budget, vertical reach or nested-call envelope is safe.

## Runtime bounds coverage

The runtime catalog currently has 12 profiles. Only 7 of the 222 feature roots match those profiles. Therefore:

- 7 roots have a runtime bounds profile.
- 215 roots do not have a runtime bounds profile.
- The other 5 catalog entries are not current feature roots.
- Roots without profiles are rejected by `PNXServerWorld.beginPlacement` before a placement transaction or journal ticket is opened.
- Unknown-script fallback also has zero read/write/entity budgets.

This is intentionally fail-closed. It prevents unsafe placement but also means the current full TerraOverworldConfig pack is not yet functionally enabled under the guard. Do not treat this as a release-ready generator.

## Terra block-state compatibility against pinned PNX

The inventory scans TerraScript source, feature YAML, structure YAML and palette YAML and compares static block-state literals with the exact PNX `blocks.json` registry.

| Measure | Result |
|---|---:|
| Static block-state literals | 486 |
| Exact or property-order-normalized PNX matches | 263 |
| Of those, property-order-only matches | 7 |
| Additional states compatible as a subset of PNX properties | 223 |
| Unmatched property variants | 0 |
| Missing PNX block identifiers | 0 |
| Dynamic block-state expressions | 12 |
| Entity/NBT identifiers excluded from block mapping | 5 |

The 223 subset-compatible states omit properties that PNX supplies from the block's default state. A source-level adapter defect was found and corrected: `PNXBlockStateDelegate.getBlockType()` now obtains `Registries.BLOCK.getBlockProperties(id).getDefaultState()` instead of wrapping the current state, so `PNXBlockType.getDefaultState()` exposes the actual registered default. `PNXBlockStateDelegate.set(...)` then changes the requested property while retaining the other defaults. This correction has not yet been compiled or runtime-tested. The 12 dynamically assembled states still require runtime validation; their property values must be checked by the PNX state setter and must not be silently coerced.

The two existing Terra-facing identifier aliases have now been centralized in `BlockStatePropertyBridge.vanillaDefaults()` and used by `PNXBlockStateDelegate.getAsString(...)`:

- Terra `minecraft:snow` → PNX `minecraft:snow_layer`
- Terra `minecraft:snow_block` → PNX `minecraft:snow`

The bridge now rejects conflicting identifier/property registrations and has a strict translation overload that verifies the target PNX block identifier against a supplied registry set.

## R15 changes committed

- Unknown structures receive a deny-all profile.
- Root placement admission rejects unprofiled structures before opening the placement transaction.
- Rejection diagnostics include the world key, feature/structure identifiers, chunk anchor and origin coordinates.
- `BoundsCatalogValidator.validateEnabled(...)` checks enabled roots against the audited catalog and rejects wildcard dependencies.
- `BlockStatePropertyBridge` now centralizes known identifier aliases, detects conflicting registrations and supports strict PNX identifier validation.
- `PNXBlockStateDelegate` now uses the centralized alias bridge instead of a hard-coded switch, and `getBlockType()` returns the actual PNX registered default state.
- `apply-stages1-48.sh` installs and compares all Stage 48 overlays after stages 1–47.
- The Stage 48 workflow checks shell syntax and patch application only, then generates the pinned inventory artifact. It does not run Gradle compilation or tests.

## Remaining work before Stage 48 can pass

1. Derive and certify conservative write AABBs, read halos, vertical ranges, operation budgets and entity budgets for every enabled TerraScript and YAML structure definition.
2. Audit all 107 dynamic nested-target scripts and ensure every nested call is constrained by the root placement envelope.
3. Decide the exact enabled root set for the final content pack; either provide a verified profile for every enabled root or disable it explicitly in the pack.
4. Wire `BoundsCatalogValidator.validateEnabled` into pack startup and fail startup if an enabled root has no verified profile.
5. Validate the 12 dynamic block-state expressions against runtime PNX property schemas.
6. Complete world-aware diagnostics for every generation stage and verify rollback leaves no durable partial placement.
7. Only after all of the above, proceed to the Stage 50 compile-only package, then re-enable and run the full regression suite.

## Acceptance decision

**NOT PASSED.** Source inventory and static block-state compatibility are now measured against the exact pinned repositories. Bounds coverage, dynamic nested-call certification, startup enforcement and complete diagnostic/rollback validation remain open.
