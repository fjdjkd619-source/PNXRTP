# PNX–Terra World Generation — stages 1–25

This branch is reserved for the unified procedural world-generation project. It is isolated from `main`; no production server or world is modified.

## Current gate: BLOCKED

The current r5 source overlay is not a complete upstream checkout, full modified-source Gradle build, integrated JAR, or deployable package. The core and narrow contract tests pass, but full patch application, full adapter compilation, and runtime certification remain outstanding.

## Pinned upstream revisions

- PowerNukkitX: `c57499365737654eca32df008751ef6bca5af142`
- Terra: `05ddc8b8a66e17412670647cce78ef837b091256`
- PNX-Terra: `74318b4a1eae95322550f12d5eb8a76d92a93731`
- Origen: `228426bb192a8d6029952aae1a77008dea5f3cda`
- Java compile target: 21

## Candidate r5

The latest recovered candidate contains 38 Java source files, 11 core test classes, 17 active patch files, the exact-ref patch application script, validation scripts/results, 13 historical archives, and the pinned BlockEntity lifecycle audit.

The latest archive is retained in the persistent Library backup folder:
`PNX-Terra-stages1-25-unified-candidate-2026-11-28-r5.zip`

SHA-256:
`b3b5aed92678ffb37666036effef508077a873c22da7642a8dce0330233c475b`

The archive is a source overlay only. It is not an installable JAR.

## Verified baseline results

- Terra 6.6.1-BETA pinned build: PASS.
- PNX 3.0.6 successor plus unmodified PNX-Terra 1.0.0 baseline build: PASS.
- Original PNX `c574993` baseline: blocked by Protocol Beta7 camera API symbols.
- The successful successor build did not include the stages 1–25 overlay.

## Latest Stage 22 groundwork

Added to this branch:
- `source/org/powernukkitx/terra/crosschunk/journal/pnx/PnxBlockEntityNbtCodec.java`
- `source/org/powernukkitx/terra/crosschunk/runtime/PnxLevelExecutor.java`
- `patches/22-stage22-be-nbt-codec-and-level-executor.patch`
- `validation/PNX-BE-CONTRACT-2026-11-28-R5.txt`

The codec follows the pinned PNX LevelDB chunk serializer's little-endian NBT format. The executor queues work through the owning Level scheduler. Both pass a narrow Java 21 contract-stub test, but are not yet wired into finalization; full upstream dependency compilation and runtime tests remain pending.

## Required completion gates

1. Apply the full active patch chain to clean upstream checkouts and resolve all patch/API conflicts.
2. Build modified Terra, PNX and PNX-Terra together.
3. Compile the full adapter against the actual pinned APIs.
4. Finish transactional BlockEntity creation/update, entity intents, physics execution, water mapping, property aliases, diagnostics, and complete Origen bounds.
5. Run end-to-end generation, crash/restart/replay, persistence, lighting, cross-chunk and load tests.
6. Produce and verify the integrated stages 1–25 JAR.

## Safety

Do not deploy this branch to the production server. The existing world remains untouched. Do not start stages 26–34 before stages 1–25 pass their full acceptance gates.
