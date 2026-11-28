# Terra world-generation integration (Stages 1–36)

This branch is reserved for integrating the PowerNukkitX/Terra procedural world-generation candidate. It does not replace the existing PNXRTP plugin under `PNXRTP/`.

## Current candidate

- Candidate archive: `PNX-Terra-stages1-36-unified-candidate-2026-11-28-r14.zip`
- Candidate SHA-256: `ca2443011d12ef9f988bd933c3510a51d1210134c4bb8cc96cbd127016aa5615`
- Archive inventory: 267 entries; ZIP integrity check passed.
- Contract checks recorded in the candidate: 18 suites × 10 rounds (180/180); runtime, delegate, PNX BlockEntity, entity-spawn, deferred-entity, shell-syntax, and patch-chain checks passed.

## Release status: BLOCKED — not production-ready

The candidate is an integration source overlay and patch chain, not a built deployable plugin/JAR. The following gates remain open:

1. Apply the complete Stage 1–36 patch chain to clean, pinned upstream checkouts and resolve all conflicts.
2. Run full Gradle builds against the exact PNX, Terra, and PNX-Terra revisions.
3. Complete physics-intent delivery with durable, replay-safe acknowledgement; current PNX adapter intentionally fails closed.
4. Complete Terra↔PNX property alias coverage, global diagnostics routing, and the full Origen safe-bounds catalog.
5. Run end-to-end generation, chunk-boundary, crash/restart, WAL replay, persistence, lighting, and load tests.
6. Produce and verify integrated release artifacts before any server deployment.

Pinned source references in the candidate:
- PowerNukkitX: `c57499365737654eca32df008751ef6bca5af142`
- PNX-Terra: `74318b4a1eae95322550f12d5eb8a76d92a93731`
- Terra 6.6.1-BETA: `05ddc8b8a66e17412670647cce78ef837b091256`
- Origen 2.2.0: `228426bb192a8d6029952aae1a77008dea5f3cda`

## Important

No production server files or existing world data have been changed. Do not deploy this candidate until every release gate above passes.

The R14 ZIP is not included in this commit: the connected GitHub interface available in this session can create text blobs and Git trees, but does not provide a binary-file transfer path from the working container. This file records the exact candidate identity and its verified limitations rather than pretending the source archive has been uploaded.
