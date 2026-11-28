# Pinned upstream source audit — 2026-11-28

## Scope and reproducibility

This audit reads the exact pinned Git objects through GitHub's Git Data API. It confirms the objects and inspects source files at those refs. It is **not** a local clean checkout, patch-application check, Gradle build, runtime test, or deployable artifact.

| Component | Repository | Pinned ref |
|---|---|---|
| PowerNukkitX | https://github.com/PowerNukkitX/PowerNukkitX | `c57499365737654eca32df008751ef6bca5af142` |
| PNX-Terra | https://github.com/PowerNukkitX-Bundle/PNX-Terra | `74318b4a1eae95322550f12d5eb8a76d92a93731` |
| Terra | https://github.com/PolyhedralDev/Terra | `05ddc8b8a66e17412670647cce78ef837b091256` |

All three pinned commit objects and recursive tree objects were found. The PNX-Terra tree at its pinned commit contains the Gradle wrapper, build file, plugin descriptor, and adapter sources.

## Confirmed upstream defects / integration contracts

### 1. Durable chunk persistence is not provided by the normal PNX save path

At the pinned PNX ref:

- `LevelDBStorage.writeChunk(IChunk)` creates a batch and calls `writeBatch`.
- `writeBatch` sets `WriteOptions.sync(false)`.
- Both `LevelDBProvider.saveChunk(int,int)` and `saveChunk(int,int,IChunk)` delegate to `storage.writeChunk(chunk)`.

Therefore the Stage 21–25 finalization ACK must not use the ordinary provider save path. A dedicated durable write using `sync(true)` is required, and the journal's `ChunkApplied` event must remain after successful durable persistence only.

Source:
- https://github.com/PowerNukkitX/PowerNukkitX/blob/c57499365737654eca32df008751ef6bca5af142/src/main/java/org/powernukkitx/level/format/leveldb/LevelDBStorage.java
- https://github.com/PowerNukkitX/PowerNukkitX/blob/c57499365737654eca32df008751ef6bca5af142/src/main/java/org/powernukkitx/level/format/leveldb/LevelDBProvider.java

### 2. PNX-Terra baseline does not expose BlockEntities to Terra

At the pinned PNX-Terra ref:

- `PNXServerWorld.getBlockEntity(...)` reads the PNX BlockEntity into a local variable but returns `null`.
- `PNXProtoWorld.getBlockEntity(...)` unconditionally returns `null`.

The Stage 22 intent model therefore cannot be considered runtime-integrated until both world paths have a deliberate conversion contract and tests for supported BlockEntity types. Unsupported types must remain fail-closed.

Source:
- https://github.com/PowerNukkitX-Bundle/PNX-Terra/blob/74318b4a1eae95322550f12d5eb8a76d92a93731/src/main/java/org/powernukkitx/terra/delegate/PNXServerWorld.java
- https://github.com/PowerNukkitX-Bundle/PNX-Terra/blob/74318b4a1eae95322550f12d5eb8a76d92a93731/src/main/java/org/powernukkitx/terra/delegate/PNXProtoWorld.java

### 3. PNX-Terra block-state properties are currently stubs

At the pinned PNX-Terra ref, `PNXBlockStateDelegate.has(Property)` returns `false`, `get(Property)` returns `null`, and `set(Property, value)` returns `null`. The string conversion contains a TODO for property support.

This is a concrete blocker for property-sensitive Terra features and structures. A complete canonical mapping/property bridge and round-trip tests are required; silently returning null is not acceptable for the final adapter.

Source:
- https://github.com/PowerNukkitX-Bundle/PNX-Terra/blob/74318b4a1eae95322550f12d5eb8a76d92a93731/src/main/java/org/powernukkitx/terra/delegate/PNXBlockStateDelegate.java

### 4. PNX-Terra chunk coordinates are stubbed

At the pinned PNX-Terra ref, `PNXChunkDelegate.getX()` and `getZ()` both return `0`, rather than the wrapped chunk's coordinates. This must be corrected and tested before any coordinate-sensitive Terra logic is trusted.

Source:
- https://github.com/PowerNukkitX-Bundle/PNX-Terra/blob/74318b4a1eae95322550f12d5eb8a76d92a93731/src/main/java/org/powernukkitx/terra/delegate/PNXChunkDelegate.java

### 5. PNX-Terra is not dependency-pinned to the selected PNX build

The pinned PNX-Terra `build.gradle.kts` declares `compileOnly("org.powernukkitx:server:nightly-SNAPSHOT")`, while the Terra dependency is `org.powernukkitx:Terra:6.6.1-BETA`. This does not prove binary compatibility with PNX 3.0.6 commit `c574993...`. The integrated build must compile against the exact PNX source/API ref or a reproducibly published artifact built from that ref.

Source:
- https://github.com/PowerNukkitX-Bundle/PNX-Terra/blob/74318b4a1eae95322550f12d5eb8a76d92a93731/build.gradle.kts

## Required corrections before acceptance

1. Apply the Stage 1–25 patch sequence to clean checkouts of the three exact refs.
2. Resolve and record missing portable history for stages 3–6 and 12–14; do not invent substitute patches.
3. Implement and test durable LevelDB chunk writes with sync=true.
4. Implement supported BlockEntity NBT encode/decode and PNX factory placement, or keep those intents rejected.
5. Implement entity placement with explicit null/failure handling, or reject unsupported entity-bearing structures.
6. Implement the live physics/update executor and wire WaterLayerPolicy, or keep physics intents rejected.
7. Complete block-state/property mapping and correct chunk coordinate delegates.
8. Add stage-wide diagnostics and a verified Origen bounds catalogue.
9. Run full PNX, Terra, and PNX-Terra Gradle builds; then runtime generation, persistence, restart/recovery, and load tests.
10. Produce and inspect the final JAR before any deployment.

## Acceptance state

**BLOCKED.** The source audit is now reproducible, but no integrated build or deployable JAR has been produced. Do not start stages 26–34 and do not deploy this candidate to the live server.


The automated source-baseline check is defined in `.github/workflows/pinned-upstream-audit.yml`; it checks out all three exact commits and validates these known source conditions. A successful result from that workflow is not an integrated build.


A separate workflow `.github/workflows/pinned-upstream-builds.yml` now attempts independent Gradle builds of all three pinned upstream repositories. These are baseline builds only; they do not apply the stage 1–25 patches or establish compatibility of the integrated generator.


## Compatibility-path experiment

The PNX 3.0.6 API successor commit `ff86b15d18e3699459c7913eb1ebec16521b7ad0` updates the protocol dependency to `3.0.0.Beta8-SNAPSHOT` and migrates the server's camera code to the corresponding camera-instruction API. GitHub's release metadata identifies this snapshot as API 3.0.6. A separate workflow now tests building that server commit, publishing its exact server artifact to Maven Local, and compiling the pinned PNX-Terra adapter against that artifact under Java 21.

This is a compatibility experiment, not a silent replacement of the original c574993 reference. The live server remains untouched. If the experiment passes, the user will still need to decide whether to target the later API 3.0.6 snapshot or keep c574993 and reconstruct its matching historical protocol dependency.


## 2026-11-28 compatibility-build follow-up

The first API-successor adapter build reached dependency resolution but could not find several transitive artifacts because the adapter's repository declarations used legacy OpenCollab endpoints. The CI workflow now rewrites these to the canonical `maven-releases` and `maven-snapshots` endpoints used by the PNX build. A fresh CI run is triggered by this audit update; this is a build-environment correction, not yet a successful build.
