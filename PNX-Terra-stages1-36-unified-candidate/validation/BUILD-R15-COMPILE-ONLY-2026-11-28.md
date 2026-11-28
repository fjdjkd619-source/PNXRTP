# R15 compile-only upstream build — 2026-11-28

## Result

- Repository: `fjdjkd619-source/PNXRTP`
- Branch: `R15`
- Commit: `61e60d573217793d62747266e76538c9a9132f68`
- Workflow: [R15 full Gradle build and JAR artifacts](https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46174452474)
- Workflow run: `46174452474`
- Conclusion: **SUCCESS**
- Gradle build duration: 48 seconds
- Java: 21
- Tests: intentionally skipped using `-x test`

The workflow applied the Stage 1–47 patch chain and Stage 48 source overlays to the pinned upstream checkouts, then compiled and packaged Terra 6.6.1, PowerNukkitX and the PNX-Terra adapter. The compile/package, JAR collection and artifact upload steps all completed successfully.

## Primary JARs verified inside the uploaded artifact

Artifact: `pnx-terra-r15-jars`  
Artifact ID: `15275510935`  
Artifact ZIP SHA-256: `a2191f7eb0edfdd444222ec1809b707b4d9149e56d553bad38b597440f51cdc6`

| Component | File | Size (bytes) | SHA-256 |
|---|---|---:|---|
| PNX-Terra adapter | `adapter/PNX-Terra-1.0.0.jar` | 11,912,188 | `cf7b9d4ce89ec4796aa5baffeea51e1a0803760d0cf9442a9087ef4777a5dfb8` |
| PowerNukkitX | `pnx/powernukkitx-nightly-SNAPSHOT.jar` | 9,792,794 | `8d6888aa3542d91d1f21664ca9035dcf7417cc5fb169f9cf5b313cfad379ed01` |
| Terra 6.6.1 | `terra/Terra-common-6.6.1-BETA+016961c-shaded.jar` | 11,645,474 | `67e1e48f1ead288699c493ec5c1f47c4c9c859ecf2eaa3d5fe6027359719fe6e` |

The ZIP contains 90 JAR files in total: the three primary component JARs and 87 additional PNX dependency JARs. The adapter JAR contains `plugin.yml`, `TerraPlugin`, `TerraGenerator`, the journal/finalization classes and the Stage 48 bounds classes.

## What this result proves

- The exact pinned upstream Terra, PNX and adapter checkouts accepted the active patch/overlay chain.
- Java compilation and JAR packaging completed successfully.
- The adapter JAR was produced and contains the PNX-Terra plugin descriptor and integrated source classes.
- The build artifact was uploaded and its archive digest and primary JAR hashes were independently checked.

## What this result does NOT prove

- No Gradle tests were run; the build intentionally used `-x test`.
- No PNX server was launched and no world/chunk generation, save, restart or replay test was run.
- No medieval-fantasy Terra ConfigPack is included in the artifact.
- No final combined generator distribution or release manifest exists yet.
- Stage 48 remains blocked: the pinned inventory still reports 287 TerraScripts without certified bounds, 215 of 222 feature roots without an accepted runtime profile, and 12 dynamic block-state expressions requiring runtime validation.
- Stage 49 content acceptance and Stage 50 release acceptance have not passed.

**Decision:** compile-only integration PASS; production/release gate BLOCKED. Do not deploy this artifact as the finished generator. The next implementation priority is Stage 49: build a version-pinned medieval-fantasy ConfigPack with an explicitly bounded enabled-root set, then wire it into Stage 50 packaging.
