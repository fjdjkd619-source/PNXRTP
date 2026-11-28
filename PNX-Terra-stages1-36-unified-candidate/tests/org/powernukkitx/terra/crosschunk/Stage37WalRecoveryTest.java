package org.powernukkitx.terra.crosschunk;

import org.powernukkitx.terra.crosschunk.journal.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

public final class Stage37WalRecoveryTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("stage37-wal-");
        Path payloadCorrupt = directory.resolve("payload.wal");
        createJournal(payloadCorrupt);
        byte[] payloadBytes = Files.readAllBytes(payloadCorrupt);
        payloadBytes[payloadBytes.length - 5] ^= 0x20;
        Files.write(payloadCorrupt, payloadBytes);
        assertRejectedUnchanged(payloadCorrupt, "complete final frame with damaged payload");

        Path crcCorrupt = directory.resolve("crc.wal");
        createJournal(crcCorrupt);
        byte[] crcBytes = Files.readAllBytes(crcCorrupt);
        crcBytes[crcBytes.length - 1] ^= 0x01;
        Files.write(crcCorrupt, crcBytes);
        assertRejectedUnchanged(crcCorrupt, "complete final frame with damaged CRC");

        Path badMagic = directory.resolve("magic.wal");
        createJournal(badMagic);
        byte[] magicBytes = Files.readAllBytes(badMagic);
        magicBytes[0] ^= 0x01;
        Files.write(badMagic, magicBytes);
        assertRejectedUnchanged(badMagic, "complete frame with invalid magic");

        Path torn = directory.resolve("torn.wal");
        createJournal(torn);
        long validSize = Files.size(torn);
        byte[] partialFrame = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN)
                .putInt(0x50584A31).putInt(128).put((byte) 0x01).put((byte) 0x04).array();
        Files.write(torn, partialFrame, StandardOpenOption.APPEND);
        try (PlacementJournal recovered = new PlacementJournal(torn)) {
            check(Files.size(torn) == validSize, "provably incomplete final frame is truncated");
            check(recovered.events().size() == 1, "valid frame prefix is retained");
            check(recovered.stats().recoveredTailBytes() == partialFrame.length, "recovery reports truncated tail bytes");
        }
        Path evidence = directory.resolve("torn.wal.torn-" + validSize);
        check(Files.size(evidence) == partialFrame.length, "torn suffix is preserved as evidence");
        check(Arrays.equals(Files.readAllBytes(evidence), partialFrame), "evidence contains exact discarded suffix");

        Path collision = directory.resolve("collision.wal");
        createJournal(collision);
        long collisionOffset = Files.size(collision);
        Path existingEvidence = directory.resolve("collision.wal.torn-" + collisionOffset);
        byte[] marker = new byte[]{9, 8, 7};
        Files.write(existingEvidence, marker);
        Files.write(collision, new byte[]{0x50, 0x58, 0x4A}, StandardOpenOption.APPEND);
        try (PlacementJournal recovered = new PlacementJournal(collision)) {
            check(recovered.events().size() == 1, "partial magic prefix recovers valid prefix");
        }
        check(Arrays.equals(Files.readAllBytes(existingEvidence), marker), "existing evidence is never overwritten");
        check(Files.exists(directory.resolve("collision.wal.torn-" + collisionOffset + "-1")),
                "evidence collision receives a unique suffix");

        Path partialHeader = directory.resolve("partial-header.wal");
        createJournal(partialHeader);
        long partialHeaderValidSize = Files.size(partialHeader);
        Files.write(partialHeader, new byte[]{0x50, 0x58, 0x4A, 0x31, 0x00, 0x00}, StandardOpenOption.APPEND);
        try (PlacementJournal recovered = new PlacementJournal(partialHeader)) {
            check(recovered.events().size() == 1, "matching partial frame header recovers valid prefix");
            check(Files.size(partialHeader) == partialHeaderValidSize, "partial header bytes are truncated");
        }

        Path garbage = directory.resolve("garbage.wal");
        createJournal(garbage);
        Files.write(garbage, new byte[]{0x11, 0x22, 0x33, 0x44}, StandardOpenOption.APPEND);
        assertRejectedUnchanged(garbage, "unrecognized trailing bytes");

        Path indexes = directory.resolve("indexes.wal");
        PlacementRecord record = sampleRecord();
        long placementSequence;
        try (PlacementJournal journal = new PlacementJournal(indexes)) {
            placementSequence = journal.appendPlacement(record);
            journal.appendAnchorStarted("world", new ChunkPos(0, 0));
            journal.appendAnchorCompleted("world", new ChunkPos(0, 0));
            ChunkPos target = new ChunkPos(0, 0);
            String digest = PlacementDigest.compute(journal.placementsFor(target), target);
            journal.appendChunkApplied("world", target, digest);
        }
        try (PlacementJournal reopened = new PlacementJournal(indexes)) {
            check(reopened.appendPlacement(record) == placementSequence, "placement sequence index survives recovery");
            check(reopened.appendAnchorStarted("world", new ChunkPos(0, 0)) == 2L,
                    "anchor-start sequence index survives recovery");
            check(reopened.appendAnchorCompleted("world", new ChunkPos(0, 0)) == 3L,
                    "anchor-complete sequence index survives recovery");
            ChunkPos target = new ChunkPos(0, 0);
            String digest = PlacementDigest.compute(reopened.placementsFor(target), target);
            check(reopened.appendChunkApplied("world", target, digest) == 4L,
                    "applied-chunk sequence index survives recovery");
            JournalStatsCheck.check(reopened.stats());
        }

        Path closed = directory.resolve("closed.wal");
        PlacementJournal journal = new PlacementJournal(closed);
        journal.close();
        journal.close();
        boolean appendRejected = false;
        try { journal.appendPlacement(sampleRecord()); }
        catch (IOException expected) { appendRejected = true; }
        check(appendRejected, "append after close is rejected");
        System.out.println("Stage37WalRecoveryTest: PASS (" + assertions + " assertions)");
    }

    private static void createJournal(Path path) throws Exception {
        try (PlacementJournal journal = new PlacementJournal(path)) {
            journal.appendPlacement(sampleRecord());
        }
    }

    private static PlacementRecord sampleRecord() {
        PlacementKey key = new PlacementKey("world", 123L, new ChunkPos(0, 0),
                "stage37-test", "test:stone", 1, 64, 1, 0, 0);
        return new PlacementRecord(key, List.of(new BlockWrite(1, 64, 1, 0, new byte[]{1, 2, 3, 4})));
    }

    private static void assertRejectedUnchanged(Path path, String label) throws Exception {
        byte[] before = Files.readAllBytes(path);
        boolean rejected = false;
        try (PlacementJournal ignored = new PlacementJournal(path)) {
            ignored.events();
        } catch (IOException expected) {
            rejected = true;
        }
        check(rejected, label + " is rejected");
        check(Arrays.equals(before, Files.readAllBytes(path)), label + " leaves WAL byte-for-byte unchanged");
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static final class JournalStatsCheck {
        private static void check(PlacementJournal.JournalStats stats) {
            Stage37WalRecoveryTest.check(stats.eventCount() == 4L, "journal stats report event count");
            Stage37WalRecoveryTest.check(stats.placementCount() == 1L, "journal stats report placement count");
            Stage37WalRecoveryTest.check(stats.lastSequence() == 4L, "journal stats report last sequence");
            Stage37WalRecoveryTest.check(stats.walBytes() > 0L, "journal stats report WAL bytes");
            Stage37WalRecoveryTest.check(stats.recoveryDurationNanos() >= 0L, "journal stats report recovery duration");
        }
    }
}
