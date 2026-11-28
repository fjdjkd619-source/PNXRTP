# Stage 1–25 gate status

**Status: BLOCKED — not an integrated build and not deployable.**

## Newly completed local source fixes (2026-11-28)

The preserved local candidate overlay received three additional correctness fixes:

1. **Stage 17 retry idempotency:** retryMissingAnchors now skips requests already accepted by PNX. Previously, retrying an accepted in-flight anchor could re-enter the runtime requester, which may reset a started chunk to GENERATED and risk duplicate PopulateStage execution.
2. **Stage 17 self-dependency:** PopulateStage now opens its AnchorSession before registering its own target dependency envelope. The scheduler skips anchors with an active session and waits for their durable completion marker instead of recursively requesting the chunk currently being populated.
3. **No force-load in Terra chunk lookup:** PNXServerWorld.getChunkAt now uses the pinned PNX Level.getChunkIfLoaded API and rejects absent/uninitiated chunks. Source inspection at PNX c574993 confirms Level.getChunk(x,z) calls forceLoadChunk if the chunk is absent, even with create=false; getChunkIfLoaded only queries the loaded-chunk provider map.

Regression tests were added for both scheduler races. The independent Java 21 core harness passed after the changes, including 3 consecutive post-fix full test runs (33/33 suite executions). Patch syntax and original archive readability checks were also repeated.

The updated local candidate archive is:
- `PNX-Terra-stages1-25-unified-candidate-2026-11-28.zip`
- SHA-256: `adaf095ee3796981cb6d928d6950208c35aae9be8d9ae12536ab6d4b971aafdf`

This archive remains a source overlay, not a complete upstream checkout or installable plugin.

## Pinned upstream source audit (2026-11-28)

Exact pinned refs:
- PNX: `PowerNukkitX/PowerNukkitX@c57499365737654eca32df008751ef6bca5af142`
- PNX-Terra: `PowerNukkitX-Bundle/PNX-Terra@74318b4a1eae95322550f12d5eb8a76d92a93731`
- Terra: `PolyhedralDev/Terra@05ddc8b8a66e17412670647cce78ef837b091256`

Source inspection confirmed:
- PNX LevelDB normal chunk writes use `WriteOptions.sync(false)`.
- PNX-Terra `PNXServerWorld.getBlockEntity` and `PNXProtoWorld.getBlockEntity` return null.
- PNX-Terra block-state property methods are stubs (`has=false`, `get=null`, `set=null`).
- PNX-Terra chunk delegate `getX/getZ` return 0.
- PNX-Terra compiles against floating `server:nightly-SNAPSHOT`, not a dependency explicitly pinned to the selected PNX commit.

Detailed evidence: [UPSTREAM-SOURCE-AUDIT.md](UPSTREAM-SOURCE-AUDIT.md).

## Local validation

- Independent Java 21 core compile: PASS, `--release 21 -Xlint:all -Werror`.
- 11 independent core test suites: PASS; 110 successful executions in the recorded repeated run, plus 33 successful executions after the latest scheduler fixes.
- Patch syntax parse: 29/29 PASS using `git apply --stat`; this does not prove applicability.
- Original archive readability: 13/13 PASS using ZIP integrity checks.
- Candidate overlay: 33 Java source files and 11 test classes.

The harness deliberately excludes upstream-dependent adapter classes. This is not a full PNX/Terra Gradle build.

## Not passed / not run

- Full clean pinned upstream checkout and complete `git apply --check` sequence.
- Recovery of portable source history for stages 3–6 and complete patch history for stages 12–14.
- Full PowerNukkitX, Terra and PNX-Terra Gradle builds.
- Full adapter compile against pinned upstream APIs.
- Block Entity NBT and actual PNX BlockEntity factory integration.
- Entity creation integration and failure handling.
- Runtime physics executor / WaterLayerPolicy integration.
- Complete block/property mapping and correction of the PNX-Terra coordinate delegate.
- Global stage diagnostics.
- Complete Origen safe-bounds catalogue.
- Runtime generation, restart/recovery, durable persistence and load tests.
- Integrated JAR packaging.

## Earlier build attempts

- PNX c574993 baseline failed compilation due mismatch between its old Bedrock camera API and resolved Protocol Beta7 snapshot.
- PNX-Terra pinned baseline failed because floating nightly resolved Java 25 class files while build target was Java 21.
- Terra baseline failed at Architectury Loom `:validateAccessWidener` with `ClosedFileSystemException`; serialized retry was configured but success has not been confirmed.
- Compatibility workflow run 46097388977 failed dependency resolution and produced no JAR. Later push-triggered run result is not available from the current connector; do not infer success.

The acceptance gate intentionally remains BLOCKED until all required integrations and full builds pass. Do not start stages 26–34 or deploy. Production server and world remain untouched.
