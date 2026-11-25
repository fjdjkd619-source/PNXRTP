package ru.leshaxarisha.pnxrtp;

import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.powernukkitx.level.format.leveldb.LevelDBStorage;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Read-only offline structural scanner for PowerNukkitX 3.0.6 Overworld chunks.
 *
 * It intentionally does not delete, repair, or rewrite any LevelDB record.
 */
public final class OfflineChunkIntegrityScanner {
    private static final int CURRENT_CHUNK_VERSION = 40;
    private static final int LIGHT_ARRAY_BYTES = 2048;

    private static final int DATA_2D = '-';
    private static final int DATA_3D = '+';
    private static final int VERSION = ',';
    private static final int DATA_2D_LEGACY = '.';
    private static final int SUBCHUNK = '/';
    private static final int LEGACY_TERRAIN = '0';
    private static final int BLOCK_ENTITIES = '1';
    private static final int ENTITIES = '2';
    private static final int PENDING_TICKS = '3';
    private static final int BLOCK_EXTRA_DATA = '4';
    private static final int BIOME_STATE = '5';
    private static final int FINALIZED_STATE = '6';
    private static final int BORDER_BLOCKS = '8';
    private static final int HARDCODED_SPAWNERS = '9';
    private static final int LEGACY_VERSION = 'v';
    private static final int PNX_EXTRA_DATA = '|';
    private static final int PNX_LIGHT = '}';

    private static final Set<Integer> CHUNK_TYPES = Set.of(
            DATA_2D, DATA_3D, VERSION, DATA_2D_LEGACY, SUBCHUNK,
            LEGACY_TERRAIN, BLOCK_ENTITIES, ENTITIES, PENDING_TICKS,
            BLOCK_EXTRA_DATA, BIOME_STATE, FINALIZED_STATE,
            BORDER_BLOCKS, HARDCODED_SPAWNERS, LEGACY_VERSION,
            PNX_EXTRA_DATA, PNX_LIGHT
    );

    private static final class ChunkAudit {
        final int x;
        final int z;
        final Set<Integer> types = new HashSet<>();
        final Set<Integer> sectionYs = new HashSet<>();
        final Set<Integer> lightYs = new HashSet<>();
        final List<String> errors = new ArrayList<>();
        final List<String> reviews = new ArrayList<>();
        int records;
        boolean hasVersion;
        boolean hasLegacyVersion;

        ChunkAudit(int x, int z) {
            this.x = x;
            this.z = z;
        }

        void error(String issue) {
            if (!errors.contains(issue)) errors.add(issue);
        }

        void review(String issue) {
            if (!reviews.contains(issue)) reviews.add(issue);
        }

        String classification() {
            if (!errors.isEmpty()) return "CORRUPT_CANDIDATE";
            if (!reviews.isEmpty()) return "REVIEW";
            return "OK";
        }

        String issues() {
            List<String> all = new ArrayList<>(errors);
            all.addAll(reviews);
            return String.join("; ", all);
        }
    }

    private OfflineChunkIntegrityScanner() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            usage();
            System.exit(2);
        }

        Path worldFolder = Path.of(args[0]).toAbsolutePath().normalize();
        Path dbFolder = worldFolder.resolve("db");
        if (!Files.isDirectory(dbFolder)) {
            throw new IllegalArgumentException("Existing world db directory not found: " + dbFolder);
        }
        Path report = args.length == 2
                ? Path.of(args[1]).toAbsolutePath().normalize()
                : worldFolder.resolve("PNXRTP-integrity-report.csv");

        Options options = new Options()
                .createIfMissing(false)
                .paranoidChecks(true)
                .compressionType(CompressionType.ZLIB_RAW)
                .blockSize(64 * 1024);

        LevelDBStorage storage = null;
        try {
            storage = new LevelDBStorage(1, worldFolder.toString(), options);
            Map<Long, ChunkAudit> chunks = scan(storage.getDb());
            validateCrossReferences(chunks);
            writeReport(report, chunks);
            printSummary(report, chunks);
        } finally {
            if (storage != null) storage.close();
        }
    }

    private static Map<Long, ChunkAudit> scan(DB db) throws IOException {
        Map<Long, ChunkAudit> chunks = new TreeMap<>();
        long scannedRecords = 0;
        try (DBIterator iterator = db.iterator()) {
            iterator.seekToFirst();
            while (iterator.hasNext()) {
                Map.Entry<byte[], byte[]> entry = iterator.next();
                byte[] key = entry.getKey();
                if (key.length < 9 || key.length > 10) continue;

                int type = key[8] & 0xff;
                if (!CHUNK_TYPES.contains(type)) continue;

                ByteBuffer coords = ByteBuffer.wrap(key, 0, 8).order(ByteOrder.LITTLE_ENDIAN);
                int x = coords.getInt();
                int z = coords.getInt();
                long packed = pack(x, z);
                ChunkAudit audit = chunks.computeIfAbsent(packed, ignored -> new ChunkAudit(x, z));
                audit.records++;
                audit.types.add(type);
                scannedRecords++;

                byte[] value = entry.getValue();
                boolean sectionType = type == SUBCHUNK || type == PNX_LIGHT;
                if (sectionType != (key.length == 10)) {
                    audit.error("invalid key length for record type " + printable(type));
                    continue;
                }

                if (key.length == 10) {
                    int sectionY = key[9]; // signed byte, as used by PNX section coordinates
                    if (type == SUBCHUNK) {
                        audit.sectionYs.add(sectionY);
                        validateSubchunk(audit, sectionY, value);
                    } else {
                        audit.lightYs.add(sectionY);
                        validateLight(audit, value);
                    }
                    continue;
                }

                switch (type) {
                    case VERSION -> {
                        audit.hasVersion = true;
                        if (value == null || value.length != 1) {
                            audit.error("VERSION value must contain exactly one byte");
                        } else if ((value[0] & 0xff) != CURRENT_CHUNK_VERSION) {
                            audit.review("VERSION value is " + (value[0] & 0xff)
                                    + ", current PNX 3.0.6 expects " + CURRENT_CHUNK_VERSION);
                        }
                    }
                    case LEGACY_VERSION -> {
                        audit.hasLegacyVersion = true;
                        if (value == null || value.length == 0) {
                            audit.error("LEGACY_VERSION value is empty");
                        }
                    }
                    case FINALIZED_STATE -> validateFinalizedState(audit, value);
                    case DATA_3D -> {
                        if (value == null || value.length < 512) {
                            audit.error("DATA_3D is shorter than the 256-entry height map");
                        }
                    }
                    case DATA_2D -> {
                        if (value == null || value.length < 768) {
                            audit.error("DATA_2D is shorter than height map plus biome bytes");
                        }
                    }
                    case DATA_2D_LEGACY -> {
                        if (value == null || value.length < 1024) {
                            audit.review("DATA_2D_LEGACY has an unexpectedly short payload");
                        }
                    }
                    default -> {
                        // Other records are counted, but their NBT/palette payloads are not fully decoded here.
                    }
                }
            }
        }

        for (ChunkAudit audit : chunks.values()) {
            if (!audit.hasVersion && !audit.hasLegacyVersion) {
                audit.review("chunk records exist without VERSION or LEGACY_VERSION");
            }
        }
        return chunks;
    }

    private static void validateFinalizedState(ChunkAudit audit, byte[] value) {
        if (value == null || (value.length != 1 && value.length != 4)) {
            audit.error("CHUNK_FINALIZED_STATE must contain 1 or 4 bytes");
            return;
        }
        int state = value.length == 1
                ? value[0]
                : ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).getInt();
        // PNX stores ChunkState.ordinal() - 1; deserialization indexes values()[state + 1].
        if (state < -1 || state > 3) {
            audit.error("CHUNK_FINALIZED_STATE value is outside the valid range -1..3");
        }
    }

    private static void validateSubchunk(ChunkAudit audit, int keySectionY, byte[] value) {
        if (value == null || value.length == 0) {
            audit.error("subchunk payload is empty");
            return;
        }
        int version = value[0] & 0xff;
        if (version == 1) {
            if (value.length < 2) audit.error("legacy subchunk payload is truncated");
            return;
        }
        if (version != 8 && version != 9) {
            audit.error("unsupported subchunk format version " + version);
            return;
        }
        int minimum = version == 9 ? 3 : 2;
        if (value.length < minimum) {
            audit.error("subchunk header is truncated");
            return;
        }
        int layers = value[1] & 0xff;
        if (layers < 1 || layers > 16) {
            audit.error("subchunk layer count is outside the accepted range 1..16");
        }
        if (version == 9 && value[2] != (byte) keySectionY) {
            audit.error("subchunk section-Y header does not match its LevelDB key");
        }
    }

    private static void validateLight(ChunkAudit audit, byte[] value) {
        if (value == null || value.length < 8) {
            audit.error("PNX_LIGHT payload is shorter than its two length headers");
            return;
        }
        ByteBuffer buffer = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN);
        int blockLength = buffer.getInt();
        if (blockLength != LIGHT_ARRAY_BYTES || buffer.remaining() < blockLength + 4) {
            audit.error("PNX_LIGHT block-light array length is invalid");
            return;
        }
        buffer.position(buffer.position() + blockLength);
        int skyLength = buffer.getInt();
        if (skyLength != LIGHT_ARRAY_BYTES || buffer.remaining() != skyLength) {
            audit.error("PNX_LIGHT sky-light array length or payload size is invalid");
        }
    }

    private static void validateCrossReferences(Map<Long, ChunkAudit> chunks) {
        for (ChunkAudit audit : chunks.values()) {
            for (int lightY : audit.lightYs) {
                if (!audit.sectionYs.contains(lightY)) {
                    audit.error("PNX_LIGHT section " + lightY + " has no matching subchunk record");
                }
            }
        }
    }

    private static void writeReport(Path report, Map<Long, ChunkAudit> chunks) throws IOException {
        Path parent = report.getParent();
        if (parent != null) Files.createDirectories(parent);
        try (BufferedWriter writer = Files.newBufferedWriter(report, StandardCharsets.UTF_8)) {
            writer.write("chunk_x,chunk_z,classification,record_count,record_types,issues");
            writer.newLine();
            for (ChunkAudit audit : chunks.values()) {
                writer.write(audit.x + "," + audit.z + ","
                        + audit.classification() + "," + audit.records + ","
                        + csv(recordTypes(audit.types)) + ","
                        + csv(audit.issues()));
                writer.newLine();
            }
        }
    }

    private static void printSummary(Path report, Map<Long, ChunkAudit> chunks) {
        long ok = 0;
        long review = 0;
        long corrupt = 0;
        for (ChunkAudit audit : chunks.values()) {
            switch (audit.classification()) {
                case "OK" -> ok++;
                case "REVIEW" -> review++;
                case "CORRUPT_CANDIDATE" -> corrupt++;
                default -> throw new IllegalStateException("Unknown classification");
            }
        }
        System.out.println("PNXRTP offline integrity scan (read-only)");
        System.out.println("Unique Overworld chunk coordinates: " + chunks.size());
        System.out.println("OK: " + ok);
        System.out.println("REVIEW: " + review);
        System.out.println("CORRUPT_CANDIDATE: " + corrupt);
        System.out.println("CSV report: " + report);
        System.out.println("No LevelDB records were changed.");
        System.out.println("Note: NBT/entity/palette payloads are not fully deserialized by this structural scan.");
    }

    private static String recordTypes(Set<Integer> types) {
        List<Integer> sorted = new ArrayList<>(types);
        sorted.sort(Integer::compareTo);
        List<String> names = new ArrayList<>();
        for (int type : sorted) names.add(printable(type));
        return String.join(" ", names);
    }

    private static String printable(int type) {
        return Character.toString((char) type);
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static void usage() {
        System.out.println("Usage: OfflineChunkIntegrityScanner <world-folder> [report.csv]");
        System.out.println("Stop PowerNukkitX first. This scanner is read-only and never repairs or deletes data.");
    }
}
