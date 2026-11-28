# Stages 1–25 reconstruction and validation report
Date: 2026-11-28

## Result
**NOT ACCEPTED — stages 1–25 are not yet a complete integrated system.** The current recovered candidate is an overlay/source audit, not a clean, self-contained Gradle checkout. Do not deploy it.

## Work completed in this pass

1. Re-ran the independent Java 21 test harness ten consecutive times.
   - Compiler: OpenJDK 21.0.12.1
   - Flags: --release 21 -Xlint:all -Werror
   - 11 test classes per round; 110/110 test executions passed.
   - Scope is limited to independent core. PNX/Terra adapter classes are excluded by validation/run-core-tests.sh.
2. Parsed all 29 patch files with `git apply --stat`: 29/29 parse successfully.
   - This validates patch syntax only; it does not prove applicability to pinned upstream trees.
3. Validated CRC/readability of all 13 preserved original stage archives: 13/13 passed.
4. Inspected the consolidated source overlay.
   - 33 Java source files.
   - 11 test classes.
   - Source contains the Stage 26 ordinal and durable-save corrections: PNXServerWorld uses journalTicket.ordinal(); PnxChunkHandle calls writeChunkDurable().
5. Verified the GitHub Actions gate run:
   - Run ID: 46096537147
   - Conclusion: failure, by design.
   - The workflow's gate step intentionally rejects STATUS=BLOCKED. This is not a Gradle build failure and not a successful build.
6. Confirmed the branch remains separate from main. No server/world files were changed.

## Blocking source gaps discovered

The candidate README's own patch inventory says:
- Stages 3–6 are represented in original ZIP snapshots, but a complete portable patch sequence is missing.
- Stages 12–14 have bounds/footprint source and reports, but no complete patch set for all upstream changes.
- PNX Core, Terra Core and PNX-Terra are not present as clean pinned source checkouts in this candidate.
- Therefore no full clean apply-check can currently be claimed.

## Blocking implementation gaps

- Block Entity NBT encoding/decoding and actual PNX BlockEntity factory integration are not implemented.
- Entity creation is not implemented; entity-bearing procedural structures remain fail-closed.
- Runtime physics executor is not connected; physics intents remain fail-closed.
- WaterLayerPolicy exists but is not wired into the live PNX update executor.
- Complete Terra-to-PNX block/property mapping is absent.
- Diagnostics do not wrap and report every stage globally.
- Origen's safe bounds catalogue covers only a small verified subset; unknown profiles remain blocked.
- No full Gradle build for PNX, Terra or PNX-Terra.
- No complete runtime integration, crash/restart persistence, or load test.
- No integrated JAR.

## Seven-task gate

| Task | Result |
|---|---|
| 1. Reconstruct stages 1–25 | Partial: preserved source and patches inventoried; gaps remain |
| 2. Transfer project to isolated GitHub branch | Done: `worldgen/stages1-25` exists; this audit report committed |
| 3. Obtain clean pinned upstream checkouts | Blocked: not present in the recovered candidate/workspace |
| 4. Apply/fix all patches against upstream | Blocked: missing complete stage 3–6 and 12–14 patch history; no clean target trees |
| 5. Connect missing runtime subsystems | Not done: BE/NBT, entities, physics, complete mapping, global diagnostics and Origen profiles remain incomplete |
| 6. Run full GitHub Actions build | Not done: only the intentional acceptance-gate workflow ran and failed while STATUS=BLOCKED |
| 7. Iterate to green integrated build | Not possible yet: prerequisites above are absent |

## Next required input/state
The missing original stage 3–6 and 12–14 source patches or exact upstream diffs must be recovered, and clean checkouts of the four pinned repositories must be available to run apply-checks and builds. Once these are available, continue without changing main or production.
