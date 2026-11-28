# PNX–Terra World Generation — stages 1–25

This branch is reserved for the unified world-generation project. It is isolated from `main`; no production server or world is modified.

## Current gate: BLOCKED

The current recovered source candidate is an audit/overlay, not yet a complete Gradle project. The independent Java 21 core tests pass (11 suites × 10 rounds in the latest rerun). Full upstream build attempts have been made but failed: pinned PNX source has a Bedrock Protocol snapshot API mismatch; pinned PNX-Terra resolves a Java 25 nightly artifact while targeting Java 21; pinned Terra's multi-platform build failed in an Architectury Loom access-widener task with ClosedFileSystemException. A separate PNX API 3.0.6 successor experiment built and published the server API, but the adapter then failed dependency resolution because of legacy OpenCollab repository endpoints. The CI workflow has been corrected to canonical OpenCollab Maven endpoints and a fresh run was triggered, but its result is not yet confirmed. No integrated JAR exists.

## Pinned upstream revisions

- PowerNukkitX: `c57499365737654eca32df008751ef6bca5af142`
- Terra: `05ddc8b8a66e17412670647cce78ef837b091256`
- PNX-Terra: `74318b4a1eae95322550f12d5eb8a76d92a93731`
- Origen: `228426bb192a8d6029952aae1a77008dea5f3cda`
- Java: 21

## Required acceptance criteria

1. Reconstruct stages 1–25 from the preserved source archives and patches.
2. Apply the full patch chain to clean checkouts of the pinned upstream repositories.
3. Resolve every compile/API conflict.
4. Implement and connect durable chunk persistence, deterministic placement identity, block mapping, Block Entity/NBT, physics, WaterLayerPolicy, diagnostics, and safe Origen bounds.
5. Run all three upstream Gradle builds and produce the integrated artifact.
6. Run integration/recovery/persistence and load tests.
7. Only when every required check passes may this branch be marked ready for stages 26–34.

## Safety

Do not deploy this branch to the production server. The existing world must remain untouched until separately authorized.
