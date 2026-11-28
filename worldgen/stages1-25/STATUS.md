# Stage 1–25 gate status

**Status: BLOCKED — not an integrated build and not deployable.**

## Newly completed upstream source audit (2026-11-28)

The exact pinned Git objects and recursive source trees have now been verified through GitHub:

- PNX: `PowerNukkitX/PowerNukkitX@c57499365737654eca32df008751ef6bca5af142`
- PNX-Terra: `PowerNukkitX-Bundle/PNX-Terra@74318b4a1eae95322550f12d5eb8a76d92a93731`
- Terra: `PolyhedralDev/Terra@05ddc8b8a66e17412670647cce78ef837b091256`

The pinned PNX-Terra source tree is present and includes the wrapper/build files and Java adapter. Source inspection confirmed:
- PNX LevelDB normal chunk writes use `WriteOptions.sync(false)`.
- PNX-Terra `PNXServerWorld.getBlockEntity` and `PNXProtoWorld.getBlockEntity` return null.
- PNX-Terra block-state property methods are stubs (`has=false`, `get=null`, `set=null`).
- PNX-Terra chunk delegate `getX/getZ` return 0.
- PNX-Terra compiles against `server:nightly-SNAPSHOT`, not a dependency explicitly pinned to the selected PNX commit.

Detailed evidence: [UPSTREAM-SOURCE-AUDIT.md](UPSTREAM-SOURCE-AUDIT.md).

A reproducible GitHub Actions workflow has been added to checkout these exact commits and assert the known baseline gaps. It is a source audit only, not a build. Its run result is pending.

## Previously passed local checks

- Java 21 independent-core compile and 11 test classes: PASS for 10 consecutive rounds (110/110 executions).
- Patch syntax parse: 29/29 PASS using `git apply --stat`; no applicability claim.
- Original archive CRC/readability: 13/13 PASS.
- Source overlay: 33 Java source files and 11 tests.

## Not passed / not run

- Full local clean checkout of all pinned upstream repos and complete `git apply --check` for the whole historical patch sequence.
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

## Actions

- The earlier `stages1-25.yml` workflow is an acceptance gate that intentionally fails while status is BLOCKED. It is not a Gradle build.
- The previous apparent green run was caused by a faulty status-marker match and must not be treated as a build.
- The corrected gate has failed as intended on the BLOCKED state.
- The new pinned-upstream source audit workflow has been added; its result is not yet confirmed.

Do not start stages 26–34 or deploy. Continue stages 1–25 only.


## Pinned baseline Gradle build attempt (run 46097043541)

GitHub Actions successfully checked out all three exact pinned commits and started independent Gradle builds under Java 21.

- **PNX 3.0.6:** FAILED at `:compileJava`. Nine unresolved symbols are from the Bedrock camera API (`CameraSetInstruction`, `CameraFadeInstruction`, `EasingType`) in the resolved `3.0.0.Beta7-Debug-SNAPSHOT` protocol dependency. This is a dependency/source mismatch in the pinned PNX build environment; it is not evidence that the generator overlay caused the failure.
- **PNX-Terra 1.0.0:** FAILED at `:compileJava`. Its `server:nightly-SNAPSHOT` dependency resolved to Java class-file version 69 (Java 25), while this project is compiled with Java 21 (class-file version 65). This independently confirms that the floating nightly dependency is not suitable for a reproducible Java 21 build.
- **Terra 6.6.1-BETA:** build job was still running when this status snapshot was recorded.

No integrated stage patch was applied in this run. No final JAR was produced. The next build experiment must first resolve the PNX protocol dependency mismatch, then publish the exact PNX source artifact locally as `org.powernukkitx:server:3.0.6-SNAPSHOT` and compile PNX-Terra against it instead of `nightly-SNAPSHOT`.


## Follow-up compatibility experiment (run 46097388977)

The first failed PNX baseline is now understood more precisely. PNX commit `c574993...` imports the pre-migration camera protocol API (`CameraSetInstruction`, `CameraFadeInstruction`, `EasingType` under `data.camera`, and the old builder/nested-option methods). The currently resolved `3.0.0.Beta7-Debug-SNAPSHOT` artifact no longer matches that source shape: the protocol classes were moved to `data.camera.instruction`, `EasingType` became `EasingFunction`, and the instruction/packet APIs changed. This is a stale/mismatched snapshot dependency, not merely a missing import.

PNX commit `ff86b15d18e3699459c7913eb1ebec16521b7ad0` is an API 3.0.6 successor that explicitly migrates PNX camera code and switches the dependency to `3.0.0.Beta8-SNAPSHOT`. A separate compatibility workflow now:
1. checks out that exact PNX commit and the pinned PNX-Terra 1.0.0 source;
2. builds and publishes the exact PNX server artifact to Maven Local under `3.0.6-SNAPSHOT`;
3. replaces PNX-Terra's floating `nightly-SNAPSHOT` dependency with that exact local artifact;
4. attempts to build the adapter on Java 21 and package its JAR.

Run: https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46097388977

At the last status query, the workflow had passed checkout, Java setup, Gradle setup, and the pinned-ref/protocol-version assertions. The PNX `publishToMavenLocal` build was still running. No adapter build result or JAR has been confirmed yet.

This experiment does not change the pinned c574993 reference or the live server. If it passes, it establishes a viable later API 3.0.6 compatibility target; it does not automatically authorize changing the user's server.


## Local regression rerun after CI repository-endpoint correction (2026-11-28)

The preserved candidate workspace was re-tested after the CI workflow correction:

- validation/run-core-tests.sh: **10/10 rounds PASS**.
- Each round ran all 11 suites: Stage15AdapterGuardContractTest, Stage16JournalTest, Stage17DependencySchedulerTest, Stage18FinalizationTest, Stage19RecoveryTest, Stage20StressTest, Stage21StateBridgeTest, Stage22IntentJournalTest, Stage23WaterPhysicsTest, Stage24DiagnosticsTest, and Stage25IntentFinalizationTest.
- This is 110 successful suite executions in this rerun.

The CI workflow now rewrites the PNX-Terra adapter's OpenCollab repository URLs to the canonical Maven endpoints used by the PNX build. The correction was committed as 115c3e7b0cea687ce28c55fcbeafd04b2cf954c2; a follow-up audit-note commit 1a2ff39500fbd05eb73c8d3d28d9050ee4275d7c triggered another compatibility workflow attempt.

The available GitHub connector in this session does not expose a list-workflow-runs endpoint for push-triggered runs, so the new run ID/result cannot be confirmed through that connector at this point. Do not infer success from the trigger commit. The previous confirmed compatibility run 46097388977 failed at dependency resolution and produced no JAR.

**Overall remains BLOCKED.** The 10-round local test pass does not replace full PNX/Terra/adapter builds, upstream patch application, missing runtime subsystem integration, or runtime/recovery testing.
