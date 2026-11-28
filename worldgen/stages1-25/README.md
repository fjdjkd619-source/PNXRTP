# PNX–Terra World Generation — stages 1–25

This branch is reserved for the unified procedural world-generation project. It is isolated from `main`; no production server or world is modified.

## Current gate: BLOCKED

The r8 source overlay is not a complete upstream checkout, full modified-source Gradle build, integrated JAR, or deployable package. Core and narrow contract tests pass, but full patch application to clean upstream repositories, full adapter compilation, and runtime certification remain outstanding.

## Pinned upstream revisions

- PowerNukkitX: `c57499365737654eca32df008751ef6bca5af142`
- Terra: `05ddc8b8a66e17412670647cce78ef837b091256`
- PNX-Terra: `74318b4a1eae95322550f12d5eb8a76d92a93731`
- Origen: `228426bb192a8d6029952aae1a77008dea5f3cda`
- Java compile target: 21

## Candidate r8

The latest recovered candidate contains 44 Java source files, 11 core test classes, 21 active patch files, the exact-ref patch application script, validation scripts/results, 13 historical archives, and the pinned BlockEntity lifecycle audit.

Persistent Library archive:
`PNX-Terra-stages1-25-unified-candidate-2026-11-28-r8-complete.zip`

SHA-256:
`d845c9520d5e06fbfa97f59a04b5949974b0fd317569e5b0d4b6a150ba19a111`

The archive is a source overlay only. It is not an installable JAR.

## Verified baseline results

- Terra 6.6.1-BETA pinned build: PASS.
- PNX 3.0.6 successor plus unmodified PNX-Terra 1.0.0 baseline build: PASS.
- Original PNX `c574993` baseline: blocked by Protocol Beta7 camera API symbols.
- The successful successor build did not include the stages 1–25 overlay.

## Stage 22 BlockEntity integration increment

Added to this branch:
- `source/org/powernukkitx/terra/crosschunk/journal/pnx/PnxBlockEntityNbtCodec.java`
- `source/org/powernukkitx/terra/crosschunk/journal/pnx/PnxBlockEntityIntentApplier.java`
- `source/org/powernukkitx/terra/crosschunk/runtime/PnxLevelExecutor.java`
- Five patches: NBT/hash marker, transactional applier, finalizer/early-save wiring, TerraScript staged Sign/MobSpawner/Container capture, and PNX MobSpawner Delay persistence.
- Contract test result: `validation/PNX-BE-CONTRACT-2026-11-28-R8.txt`

The applier preflights all intents, resolves PNX registry types, rejects collisions, uses a SHA-256 intent marker to accept normalized persisted entities as replay-safe, and rolls back entities registered before a constructor failure is swallowed by the PNX factory. The coordinator now separates compute-pool block projection from PNX Level-scheduler intent application.

Contract tests pass, including constructor-failure rollback. TerraScript `state`/`loot` capture is now implemented for supported sign, spawner and inventory-container types. Still open: full upstream dependency compilation, Bedrock runtime generation, item-metadata fidelity, and durable save/restart/crash-window certification.

## Required completion gates

1. Apply the full active patch chain to clean upstream checkouts and resolve all patch/API conflicts.
2. Build modified Terra, PNX and PNX-Terra together.
3. Compile the full adapter against the actual pinned APIs.
4. Connect Terra structure BlockEntity capture; finish entity intents, physics execution, water mapping, property aliases, diagnostics, and complete Origen bounds.
5. Run end-to-end generation, crash/restart/replay, persistence, lighting, cross-chunk and load tests.
6. Produce and verify the integrated stages 1–25 JAR.

## Safety

Do not deploy this branch to the production server. The existing world remains untouched. Do not start stages 26–34 before stages 1–25 pass their full acceptance gates.


Stage 22 now includes detached TerraScript Sign, MobSpawner, and Container capture, journaled BlockEntity intents, PNX spawner Delay persistence, and an early durable save on the Level scheduler. See `TERRA-STRUCTURE-BLOCKENTITY-CAPTURE-AUDIT-2026-11-28.md` and the R8 contract reports. The full r8 ZIP is in the ChatGPT Library; the binary ZIP is not committed to this branch.


Stage 22 r8 also adds `PNXStagedSignDelegate`, `PNXStagedMobSpawnerDelegate`, `PNXStagedContainerDelegate`, and `PNXStagedInventory`. `PNXServerWorld` records their immutable BlockEntity intents in the active placement journal. The PNX spawner-delay patch and early Level-scheduler durable save/rollback are included. See `TERRA-STRUCTURE-BLOCKENTITY-CAPTURE-AUDIT-2026-11-28.md`. Full r8 ZIP is in the ChatGPT Library; binary ZIP is not committed to this branch.
