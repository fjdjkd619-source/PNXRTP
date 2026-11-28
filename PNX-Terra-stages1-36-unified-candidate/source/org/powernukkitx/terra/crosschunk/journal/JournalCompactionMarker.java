package org.powernukkitx.terra.crosschunk.journal;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Durable intent marker for Stage 44 WAL compaction.
 *
 * Its presence means the referenced checkpoint is already durable and the WAL
 * may be discarded in full. Recovery can therefore finish an interrupted
 * truncate without guessing whether any WAL bytes are still authoritative.
 */
final class JournalCompactionMarker {
    private static final int MAGIC = 0x5058434D; // PXCM
    private static final int VERSION = 1;
    private static final int BODY_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final int FILE_BYTES = BODY_BYTES + HASH_BYTES;

    private JournalCompactionMarker() {}

    static void writeAtomically(Path destination, long watermark) throws IOException {
        if (watermark < 0) throw new IllegalArgumentException("watermark must be non-negative");
        Path absolute = destination.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) throw new IOException("Compaction marker has no parent directory");
        Files.createDirectories(parent);
        byte[] body = ByteBuffer.allocate(BODY_BYTES).putInt(MAGIC).putInt(VERSION).putLong(watermark).array();
        byte[] bytes = ByteBuffer.allocate(FILE_BYTES).put(body).put(sha256(body)).array();
        Path temporary = absolute.resolveSibling(absolute.getFileName() + ".tmp-" + UUID.randomUUID());
        boolean published = false;
        try {
            try (FileChannel output = FileChannel.open(temporary,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                writeFully(output, ByteBuffer.wrap(bytes));
                output.force(true);
            }
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                throw new IOException("Atomic compaction-marker publication is not supported", unsupported);
            }
            published = true;
            forceDirectory(parent);
        } finally {
            if (!published) Files.deleteIfExists(temporary);
        }
    }

    static long read(Path marker) throws IOException {
        if (Files.size(marker) != FILE_BYTES) throw new IOException("Invalid compaction marker length");
        byte[] bytes = Files.readAllBytes(marker);
        if (bytes.length != FILE_BYTES) throw new IOException("Invalid compaction marker length");
        ByteBuffer frame = ByteBuffer.wrap(bytes);
        byte[] body = new byte[BODY_BYTES];
        frame.get(body);
        byte[] hash = new byte[HASH_BYTES];
        frame.get(hash);
        if (!MessageDigest.isEqual(hash, sha256(body)))
            throw new IOException("Compaction marker integrity check failed");
        ByteBuffer fields = ByteBuffer.wrap(body);
        if (fields.getInt() != MAGIC) throw new IOException("Invalid compaction marker magic");
        if (fields.getInt() != VERSION) throw new IOException("Unsupported compaction marker version");
        long watermark = fields.getLong();
        if (watermark < 0) throw new IOException("Invalid compaction marker watermark");
        return watermark;
    }

    private static byte[] sha256(byte[] bytes) throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 is unavailable", impossible);
        }
    }

    private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) channel.write(buffer);
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }
}
