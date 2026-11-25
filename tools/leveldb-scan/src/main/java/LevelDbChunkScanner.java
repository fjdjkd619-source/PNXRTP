import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;

import java.io.BufferedWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class LevelDbChunkScanner {
    private static final byte MODERN = (byte) ',';
    private static final byte LEGACY = (byte) 'v';

    private record ChunkKey(int dimension, int x, int z) implements Comparable<ChunkKey> {
        @Override public int compareTo(ChunkKey other) {
            int d = Integer.compare(dimension, other.dimension);
            if (d != 0) return d;
            int a = Integer.compare(x, other.x);
            return a != 0 ? a : Integer.compare(z, other.z);
        }
    }

    private static final class Counts {
        long modernRecords;
        long legacyRecords;
        final Set<ChunkKey> modern = new TreeSet<>();
        final Set<ChunkKey> legacy = new TreeSet<>();
        final Map<String, Long> modernValues = new TreeMap<>();
        final Map<String, Long> legacyValues = new TreeMap<>();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: LevelDbChunkScanner <db-directory> <report-file>");
            System.exit(2);
        }

        Path dbPath = Path.of(args[0]).toAbsolutePath();
        Path reportPath = Path.of(args[1]).toAbsolutePath();
        if (!Files.isDirectory(dbPath)) throw new IllegalArgumentException("Database directory not found: " + dbPath);

        Map<Integer, Counts> dimensions = new TreeMap<>();
        long totalKeys = 0;
        long modernMalformed = 0;
        long legacyMalformed = 0;
        long otherKeys = 0;
        long iteratorRecords = 0;
        DB db = null;
        DBIterator iterator = null;
        Options options = new Options().createIfMissing(false).paranoidChecks(true);

        try {
            db = Iq80DBFactory.factory.open(dbPath.toFile(), options);
            iterator = db.iterator();
            iterator.seekToFirst();
            while (iterator.hasNext()) {
                Map.Entry<byte[], byte[]> entry = iterator.next();
                iteratorRecords++;
                byte[] key = entry.getKey();
                byte[] value = entry.getValue();
                if (key.length == 0) {
                    otherKeys++;
                    continue;
                }

                byte type = key[key.length - 1];
                if (type != MODERN && type != LEGACY) {
                    otherKeys++;
                    continue;
                }

                if (key.length != 9 && key.length != 13) {
                    if (type == MODERN) modernMalformed++; else legacyMalformed++;
                    continue;
                }

                ByteBuffer buffer = ByteBuffer.wrap(key).order(ByteOrder.LITTLE_ENDIAN);
                int x = buffer.getInt();
                int z = buffer.getInt();
                int dimension = key.length == 9 ? 0 : buffer.getInt();
                ChunkKey chunk = new ChunkKey(dimension, x, z);
                Counts counts = dimensions.computeIfAbsent(dimension, ignored -> new Counts());

                String valueHex = HexFormat.of().formatHex(value);
                if (type == MODERN) {
                    counts.modernRecords++;
                    counts.modern.add(chunk);
                    counts.modernValues.merge(valueHex, 1L, Long::sum);
                } else {
                    counts.legacyRecords++;
                    counts.legacy.add(chunk);
                    counts.legacyValues.merge(valueHex, 1L, Long::sum);
                }
            }
            totalKeys = iteratorRecords;
        } finally {
            if (iterator != null) try { iterator.close(); } catch (Exception ignored) {}
            if (db != null) try { db.close(); } catch (Exception ignored) {}
            options = null;
        }

        try (BufferedWriter out = Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8)) {
            out.write("PNXRTP LevelDB chunk-key diagnostic");
            out.newLine();
            out.write("Database: " + dbPath);
            out.newLine();
            out.write("Read mode: opened extracted working copy; source archive was not opened or modified");
            out.newLine();
            out.write("Total LevelDB records iterated: " + totalKeys);
            out.newLine();
            out.write("Other key types (not VERSION/LEGACY_VERSION): " + otherKeys);
            out.newLine();
            out.write("Malformed VERSION keys (expected 9 or 13 bytes): " + modernMalformed);
            out.newLine();
            out.write("Malformed LEGACY_VERSION keys (expected 9 or 13 bytes): " + legacyMalformed);
            out.newLine();
            out.newLine();

            long totalModern = 0, totalLegacy = 0, totalUnique = 0, totalOverlap = 0;
            for (Map.Entry<Integer, Counts> entry : dimensions.entrySet()) {
                int dimension = entry.getKey();
                Counts c = entry.getValue();
                Set<ChunkKey> union = new TreeSet<>(c.modern);
                union.addAll(c.legacy);
                Set<ChunkKey> overlap = new TreeSet<>(c.modern);
                overlap.retainAll(c.legacy);
                long modernOnly = c.modern.size() - overlap.size();
                long legacyOnly = c.legacy.size() - overlap.size();

                totalModern += c.modernRecords;
                totalLegacy += c.legacyRecords;
                totalUnique += union.size();
                totalOverlap += overlap.size();

                out.write("=== DIMENSION ID " + dimension + " ===");
                out.newLine();
                out.write("VERSION records: " + c.modernRecords);
                out.newLine();
                out.write("Unique VERSION coordinates: " + c.modern.size());
                out.newLine();
                out.write("LEGACY_VERSION records: " + c.legacyRecords);
                out.newLine();
                out.write("Unique LEGACY_VERSION coordinates: " + c.legacy.size());
                out.newLine();
                out.write("Coordinates present in both formats: " + overlap.size());
                out.newLine();
                out.write("Modern-only coordinates: " + modernOnly);
                out.newLine();
                out.write("Legacy-only coordinates: " + legacyOnly);
                out.newLine();
                out.write("Unique coordinates across both formats: " + union.size());
                out.newLine();
                writeValueCounts(out, "VERSION value bytes (hex)", c.modernValues);
                writeValueCounts(out, "LEGACY_VERSION value bytes (hex)", c.legacyValues);
                out.newLine();
            }

            out.write("=== TOTALS ===");
            out.newLine();
            out.write("VERSION records: " + totalModern);
            out.newLine();
            out.write("LEGACY_VERSION records: " + totalLegacy);
            out.newLine();
            out.write("Unique coordinates across all dimensions: " + totalUnique);
            out.newLine();
            out.write("Coordinates duplicated across VERSION and LEGACY_VERSION: " + totalOverlap);
            out.newLine();
            out.write("Note: these are records present in this database snapshot. A missing coordinate is not proof that a chunk was deleted.");
            out.newLine();
        }
        System.out.println(Files.readString(reportPath));
    }

    private static void writeValueCounts(BufferedWriter out, String title, Map<String, Long> counts) throws Exception {
        out.write(title + ":");
        out.newLine();
        if (counts.isEmpty()) {
            out.write("  (none)");
            out.newLine();
        } else {
            for (Map.Entry<String, Long> entry : counts.entrySet()) {
                out.write("  " + (entry.getKey().isEmpty() ? "<empty>" : entry.getKey()) + " = " + entry.getValue());
                out.newLine();
            }
        }
    }
}
