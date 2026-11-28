# Deliverable manifest — R14 — 2026-11-28

Archive: `PNX-Terra-stages1-36-unified-candidate-2026-11-28-r14.zip`

- Stage 1–26 source/patch overlay retained from R12/R13.
- Stage 27–34 core contracts and unified overlay.
- Stage 35 PNX entity intent adapter and rollback-only PNX lifecycle seam.
- Stage 36 deferred PNX entity construction, chunk-only durable-save staging, post-save Level activation/client publication, and finalization-order regression test.
- Java 21 core test suites: 18 PASS.
- PNX entity applier contract suite: PASS.
- PNX BlockEntity contract suite: PASS.
- PNX runtime/delegate contract suites: PASS.
- 27 patch files parse; shell syntax checks pass.

This is a source candidate, not an integrated or certified server generator. Terra entity spawn capture remains fail-closed, physics remains fail-closed, full upstream Gradle build and runtime certification remain outstanding. Do not deploy.
