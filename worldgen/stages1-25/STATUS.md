# Stage 1–25 gate status

**Status: BLOCKED — the complete modified stages 1–25 chain has not yet passed a full upstream build or runtime certification. It is not deployable.**

## Latest local source work (2026-11-28)

The candidate overlay has been updated with these corrections:

1. **Stage 17 retry liveness:** a previously accepted anchor request is not resubmitted while PNX still owns its generation-queue slot. If PNX releases that slot without a durable anchor-complete WAL marker, the scheduler clears the stale request and retries. The adapter uses the pinned `Level.isChunkGenerating(x,z)` API.
2. **Stage 17 self-dependency:** `PopulateStage` opens its `AnchorSession` before registering the target dependency envelope. The scheduler skips anchors with active sessions and waits for durable completion.
3. **No force-load during Terra reads:** `PNXServerWorld.getChunkAt` uses `Level.getChunkIfLoaded` and rejects absent/uninitiated chunks.
4. **Unknown Origen scripts:** unknown structure IDs use a hard runtime-bounded fallback (x/z +/-32, y +/-128, 65,536 reads/writes, zero entity spawns). All nested scripts share the same root guard; any read/write outside the envelope aborts the whole placement. Namespaced IDs such as `origen:waterfall` resolve to audited profiles where available. This is runtime containment, not static proof of every script's theoretical bounds.
5. **Durability and ordering:** the final overlay routes chunk persistence through LevelDB `WriteOptions.sync(true)` before WAL acknowledgement and uses the per-anchor placement ticket ordinal rather than a constant zero.

Regression coverage was added for failed accepted requests and bounded unknown structures.

## Local validation

- Java 21 core compile: PASS, `--release 21 -Xlint:all -Werror`.
- 11 core test suites x 10 complete rounds: **110/110 PASS** after the latest changes.
- Separate Java 21 contract-stub compile for `PnxGenerationJournalRuntime`: PASS, including use of the exact `Level.isChunkGenerating(x,z)` API.
- Active patch syntax: **16/16 PASS** using `git apply --stat`.
- Supplemental stages 15–25 hardening patch: `git apply --check` and application PASS against the reconstructed Stage 21–25 baseline overlay.
- Original archive readability: **13/13 PASS**.
- A source-only patch application script now pins all three exact upstream commits and applies the active chain in order. It has passed shell syntax validation; it has not yet been run against full upstream checkouts.

Latest local candidate archive:
- `PNX-Terra-stages1-25-unified-candidate-2026-11-28-r2.zip`
- SHA-256: `aa750184820601da21b8f1b1fcbc2a394f07bba6a9775cdeb974189740ad88d9`
- It is a source overlay with 33 Java source files, 11 test classes, 16 active patches, the apply script, validation outputs and 13 original archives. It is not a full upstream checkout or an installable JAR.

## Verified upstream baseline results

### Terra 6.6.1-BETA — PASS

Pinned Terra commit `05ddc8b8a66e17412670647cce78ef837b091256` completed its Gradle build:
https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46098232420

### PNX 3.0.6 successor + original PNX-Terra 1.0.0 — PASS

Compatibility run:
https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46098134148

PNX commit `ff86b15d18e3699459c7913eb1ebec16521b7ad0` built and published to Maven Local as `3.0.6-SNAPSHOT`; the unmodified pinned PNX-Terra 1.0.0 source compiled and packaged against it using Java 21. Artifact:
https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46098134148/artifacts/15243649817

This is a compatibility baseline only. It does not contain the stages 1–25 overlay.

### Original PNX c574993 baseline remains incompatible

- PNX `c574993` fails compilation against the resolved Protocol Beta7 snapshot due to nine camera API symbols.
- PNX-Terra's floating `server:nightly-SNAPSHOT` resolves Java 25 class files while the build targets Java 21.
- The successful successor experiment does not silently replace the user's pinned production server.

## Remaining blockers before completion

- Apply the complete active patch chain to clean full upstream checkouts and resolve every context/API conflict.
- Build the modified Terra, PNX and PNX-Terra source together; the successful upstream baseline build did not include this overlay.
- Compile the full adapter against actual pinned APIs; only the core and one runtime contract-stub slice have been compiled locally.
- Implement actual PNX BlockEntity NBT/factory integration.
- Implement transactional entity creation and failure/recovery handling.
- Connect the physics intent executor and complete water-layer/property mapping.
- Correct the PNX-Terra chunk delegate coordinate stubs and finish the full block/property mapping.
- Complete global diagnostics and statically audit the remaining Origen scripts.
- Run end-to-end generation, crash/restart/replay, persistence, lighting, cross-chunk and load tests.
- Produce and verify the integrated stages 1–25 JAR.

Production server and world remain untouched. Do not start stages 26–34 or deploy while the gate is BLOCKED.
