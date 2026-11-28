# Stage 1–25 gate status

**Status: BLOCKED — not an integrated stages 1–25 build and not deployable.**

## Newly completed local source fixes (2026-11-28)

The preserved local candidate overlay received three additional correctness fixes:

1. **Stage 17 retry idempotency:** retryMissingAnchors skips requests already accepted by PNX. Previously, retrying an accepted in-flight anchor could re-enter the runtime requester, which may reset a started chunk to GENERATED and risk duplicate PopulateStage execution.
2. **Stage 17 self-dependency:** PopulateStage opens its AnchorSession before registering its own target dependency envelope. The scheduler skips anchors with an active session and waits for their durable completion marker instead of recursively requesting the chunk currently being populated.
3. **No force-load in Terra chunk lookup:** PNXServerWorld.getChunkAt uses the pinned PNX Level.getChunkIfLoaded API and rejects absent/uninitiated chunks. PNX c574993 source confirms Level.getChunk(x,z) calls forceLoadChunk if absent, even with create=false; getChunkIfLoaded only queries loaded chunks.

Regression tests were added for both scheduler races. The independent Java 21 core harness passed after the changes, including 3 consecutive post-fix full test runs (33/33 suite executions). Patch syntax and original archive readability checks were repeated.

Local candidate archive:
- `PNX-Terra-stages1-25-unified-candidate-2026-11-28.zip`
- SHA-256: `f4fcbd129180e3b5d32115afbfb206ad00ec9065f37eeca72826e40e1db98e89`

This archive remains a source overlay, not a complete upstream checkout or installable plugin.

## Verified upstream build results (2026-11-28)

### Terra 6.6.1-BETA — PASS

Pinned Terra commit `05ddc8b8a66e17412670647cce78ef837b091256` completed its Gradle build successfully in run:
https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46098232420

The serialized `--no-parallel --max-workers=1` retry cleared the previous Loom `ClosedFileSystemException` failure.

### PNX 3.0.6 successor + PNX-Terra 1.0.0 compatibility baseline — PASS

Compatibility run:
https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46098134148

It checked out PNX commit `ff86b15d18e3699459c7913eb1ebec16521b7ad0` (API 3.0.6 successor; Protocol Beta8), built and published the exact PNX server artifact to Maven Local as `3.0.6-SNAPSHOT`, then compiled and packaged the pinned PNX-Terra 1.0.0 source against it using Java 21.

Both Gradle builds reported BUILD SUCCESSFUL. The workflow verified `PNX-Terra-1.0.0.jar` exists and uploaded artifact:
https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46098134148/artifacts/15243649817

Artifact SHA-256: `23d9ca6edf536ef0e4619f7a0d617905c02e943b320d8eacac1140b09a1348cf`. It contains baseline PNX and PNX-Terra outputs, not the stages 1–25 candidate overlay. This is a compatibility baseline, not the finished generator. The user's running c574993 server is not changed by this result.

### Original pinned baseline remains incompatible

Run:
https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46098232420

- PNX c574993 fails Java compilation because its old camera API does not match the resolved Protocol Beta7 snapshot (9 unresolved camera symbols).
- PNX-Terra pinned 1.0.0 fails against floating `server:nightly-SNAPSHOT`, which resolves Java 25 class files (69) while build target is Java 21 (65).
- These failures are distinct from the successful successor compatibility experiment above.

## Pinned source audit

Exact original refs:
- PNX: `PowerNukkitX/PowerNukkitX@c57499365737654eca32df008751ef6bca5af142`
- PNX-Terra: `PowerNukkitX-Bundle/PNX-Terra@74318b4a1eae95322550f12d5eb8a76d92a93731`
- Terra: `PolyhedralDev/Terra@05ddc8b8a66e17412670647cce78ef837b091256`

Source inspection confirmed:
- PNX LevelDB normal chunk writes use `WriteOptions.sync(false)`.
- PNX-Terra `PNXServerWorld.getBlockEntity` and `PNXProtoWorld.getBlockEntity` return null.
- PNX-Terra block-state property methods are stubs (`has=false`, `get=null`, `set=null`).
- PNX-Terra chunk delegate `getX/getZ` return 0.
- PNX-Terra uses floating `server:nightly-SNAPSHOT` rather than a pinned PNX artifact.

Detailed evidence: [UPSTREAM-SOURCE-AUDIT.md](UPSTREAM-SOURCE-AUDIT.md).

## Local validation

- Independent Java 21 core compile: PASS, `--release 21 -Xlint:all -Werror`.
- 11 independent core test suites: PASS; 110 successful executions in the recorded repeated run, plus 33 successful executions after the latest scheduler fixes.
- Patch syntax parse: 29/29 PASS using `git apply --stat`; this does not prove applicability.
- Original archive readability: 13/13 PASS using ZIP integrity checks.
- Candidate overlay: 33 Java source files and 11 test classes.

The harness deliberately excludes upstream-dependent adapter classes. This is not a full build of the modified stages 1–25 source.

## Not passed / not run

- Full clean pinned upstream checkout and complete `git apply --check` sequence.
- Recovery of portable source history for stages 3–6 and complete patch history for stages 12–14.
- Applying the complete stages 1–25 overlay to the successful PNX ff86b15 + PNX-Terra baseline and compiling the combined result.
- Full adapter compile for the modified candidate.
- Block Entity NBT and actual PNX BlockEntity factory integration.
- Entity creation integration and failure handling.
- Runtime physics executor / WaterLayerPolicy integration.
- Complete block/property mapping and correction of the PNX-Terra coordinate delegate.
- Global stage diagnostics.
- Complete Origen safe-bounds catalogue.
- Runtime generation, restart/recovery, durable persistence and load tests.
- Integrated stages 1–25 JAR packaging and installation test.

## Next required work

Use the successful PNX ff86b15 compatibility baseline as a build target only after preserving the original c574993 baseline as a separate reference. Then reconstruct and apply the full stage patch history to clean upstream checkouts, fix all API conflicts, integrate the remaining runtime subsystems, and run full compile/integration/recovery tests. No production server or world changes are authorized.

The acceptance gate intentionally remains BLOCKED. Do not start stages 26–34 or deploy.
