# Stage 37 WAL recovery review

Repository: fjdjkd619-source/PNXRTP
Target branch: R14
Base commit inspected: 621c2b9671cd76ffd23d7d11e22242fdf3fe8d8b

## Review of the supplied stage-37 patch

The patch improves two important cases:
- A damaged frame followed by another checksum-valid frame is treated as corruption and startup fails closed.
- A checksum-valid frame with an unsupported format version, malformed payload, or unexpected sequence is not silently discarded.

The added test matrix covers truncation at every byte offset, middle-file damage, future versions, sequence gaps, and legacy v1/v2 decoding.

## Blocking correctness issue

readFrame() returns null for several materially different conditions: incomplete frame/bounds, bad magic, invalid length, and CRC mismatch. recover() then sends every such result to failOrTruncateTail().

That means a complete final frame with a CRC mismatch is truncated and its placement event is discarded. A complete frame can be damaged by storage corruption or a bit flip; its position at EOF does not prove that it was an interrupted write. The test corruptTailFrameIsTruncatedAndPreserved() currently codifies this unsafe behavior.

Required correction before accepting stage 37:
1. Distinguish physically incomplete tail data from a complete but invalid frame.
2. Truncate only a provably incomplete final write (for example, a matching partial frame header whose declared frame extends past EOF, or a partial matching magic prefix).
3. Fail closed and leave the journal byte-for-byte unchanged for a complete frame with bad CRC, invalid complete header, or other complete-frame corruption, even when it is the last frame.
4. Add regression tests for a complete final frame with a flipped payload byte and a flipped CRC byte; both must fail closed and preserve the original WAL.
5. Preserve existing evidence files rather than overwriting a prior torn-offset evidence file. Evidence creation should be collision-safe.

## Performance / recovery review

hasValidFrameAfter() scans byte-by-byte for the magic value and calls readFrame() for each candidate. On adversarial or badly damaged data containing many magic-like byte sequences, this can cause repeated allocations and expensive random reads. Bound candidate work and avoid allocating a maximum-sized payload for every candidate; retain conservative fail-closed behavior if the scan budget is exceeded.

WalAudit.java and WalScale.java are diagnostic harnesses, not proof of production-scale suitability. The scale harness measures open time and retained heap, while WalAudit additionally measures idempotent append cost. These measurements still need to be run against the actual integrated source and recorded with file size, event count, heap, and elapsed time.

## Integration status

The R14 branch tree at the inspected base commit contains the candidate ZIP archive and a separate legacy PNXRTP plugin source tree; it does not expose the generator's unpacked Maven/Gradle source tree as ordinary text files. The GitHub text-file interface rejects the ZIP blob as non-UTF-8, so the archive could not be extracted or built in this environment during this pass.

No claim is made that stage 37 is applied, that stages 1-36 build, or that a server JAR is ready. The next implementation pass must work against the extracted R14 source tree, apply the corrected recovery logic, run the stage-37 tests, then perform the clean upstream build and the physical block-update/replay integration checks.