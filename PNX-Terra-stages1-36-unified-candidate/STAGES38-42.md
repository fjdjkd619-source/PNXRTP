# Stages 38–42 — WAL recovery and runtime hardening

These stages are implemented in the source overlay under `source/org/powernukkitx/terra/crosschunk/journal/PlacementJournal.java`. They preserve the existing WAL frame format (PXJ1, versioned payload, CRC32C) and do not rewrite prior records.

## Stage 38 — fail-closed recovery

- Recovery now distinguishes a demonstrably incomplete final frame from a complete invalid frame.
- A tail is truncatable only when a matching partial magic/header is present, or a complete valid header declares a frame that extends beyond EOF.
- A complete frame with bad magic, invalid length, CRC mismatch, malformed payload, or sequence gap throws `IOException`; the original WAL remains byte-for-byte unchanged.
- Before truncating a torn tail, its exact suffix is preserved in a sibling `.torn-<offset>` evidence file created with `CREATE_NEW`. Collisions use a numeric suffix and never overwrite prior evidence.
- Completed-anchor state is made consistent between live append and recovery.

## Stage 39 — bounded recovery memory

- Recovery reads the payload into one bounded byte array and reads the four-byte CRC separately; it no longer allocates a second frame-sized buffer.
- The existing 64 MiB frame ceiling remains enforced before allocation.
- CRC and event decoding are performed only after the frame bounds prove that the complete frame exists.

## Stage 40 — constant-time idempotency indexes

- Placement, anchor-start, anchor-complete and applied-chunk sequence lookups now use indexes instead of rescanning the entire event list.
- Indexes are populated both on durable append and WAL replay.
- Existing idempotency and stable-key collision rules remain in force.

## Stage 41 — journal health snapshot

- `PlacementJournal.stats()` exposes WAL bytes, event/placement/anchor/applied counts, last sequence, recovery duration and bytes removed from a proven torn tail.
- Metrics are observational and do not change journal ordering or persistence semantics.

## Stage 42 — lifecycle and append-failure safety

- Closing the journal is idempotent and attempts force, lock release and channel close while preserving suppressed I/O failures.
- Appends after close are rejected.
- If an append fails and rollback/truncation also fails, the journal becomes poisoned and rejects later writes rather than appending behind an uncertain partial frame.

## Regression coverage

`tests/org/powernukkitx/terra/crosschunk/Stage37WalRecoveryTest.java` covers complete payload/CRC corruption, invalid magic, non-matching trailing garbage, valid torn-tail truncation, evidence preservation and collision handling, recovered idempotency indexes, metrics, idempotent close and post-close write rejection.

`Stage16JournalTest` now asserts that a complete final frame with a bad CRC is rejected without changing the WAL. Existing Stage19 recovery coverage continues to exercise a declared frame extending beyond EOF.

## Build boundary

The repository's candidate is a source overlay plus patch chain and validation scripts; this directory does not contain a `build.gradle`, Gradle wrapper, or complete upstream PNX/Terra/adapter checkout. The independent Java 21 core test runner is available at `validation/run-core-tests.sh`, but a fresh execution and exact-ref upstream Gradle build are still required before production integration can be claimed.
