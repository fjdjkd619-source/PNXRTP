# Stage 1–25 gate status

**Status: BLOCKED — not an integrated build and not deployable.**

Latest local verification (2026-11-28):
- Java 21 independent-core compile and 11 test classes: PASS for 10 consecutive rounds (110/110 executions).
- Patch syntax parse: 29/29 PASS using `git apply --stat`; no applicability claim.
- Original archive CRC/readability: 13/13 PASS.
- Source overlay: 33 Java source files and 11 tests.

GitHub Actions:
- Workflow run 46096537147: FAILURE by design at the acceptance-gate step because this status is BLOCKED. It did not run Gradle builds.

Unresolved blockers:
1. Complete portable patch/source history for stages 3–6 is missing.
2. Complete upstream patch history for stages 12–14 is missing.
3. Clean pinned PNX/Terra/PNX-Terra source checkouts are not included; full apply-check and Gradle builds cannot be performed.
4. Block Entity NBT and actual PNX BlockEntity factory integration absent.
5. Entity creation absent; entity-bearing structures fail closed.
6. Physics executor not connected; WaterLayerPolicy not wired to live PNX updates.
7. Complete Terra↔PNX block/property registry absent.
8. Global stage diagnostics incomplete.
9. Origen safe-bounds coverage incomplete; unknown scripts remain blocked.
10. No full runtime, restart/recovery, persistence or load integration tests.
11. No integrated JAR.

Do not start stages 26–34 or deploy. Continue stage 1–25 only after the missing source history and pinned upstream working trees are recovered.
