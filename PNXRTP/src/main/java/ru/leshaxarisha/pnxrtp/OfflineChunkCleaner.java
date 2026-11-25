package ru.leshaxarisha.pnxrtp;

import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.WriteBatch;
import org.iq80.leveldb.WriteOptions;
import org.powernukkitx.level.format.leveldb.LevelDBStorage;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Offline-only LevelDB cleaner for PNXRTP.
 *
 * Usage:
 *   java -cp "PNXRTP-1.5.0.jar:powernukkitx.jar" ru.leshaxarisha.pnxrtp.OfflineChunkCleaner <world-folder> <spawn-chunk-x> <spawn-chunk-z>
 *   add --apply --backup-confirmed to actually delete candidate Overworld chunk records.
 *
 * The LevelDB lock is acquired by LevelDBStorage, so this utility cannot open a database
 * that is still locked by a running PowerNukkitX server.
 */
public final class OfflineChunkCleaner {
    private static final Set<Integer> CHUNK_RECORD_TYPES = Set.of(
            (int) '-', (int) '+', (int) ',', (int) '.', (int) '/',
            (int) '0', (int) '1', (int) '2', (int) '3', (int) '4',
            (int) '5', (int) '6', (int) '8', (int) '9', (int) 'v',
            (int) '|', (int) '}'
    );
    private static final int PROTECTED_RADIUS = 10;
    private static final int BATCH_LIMIT = 10_000;

    private OfflineChunkCleaner() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3 && args.length != 5) {
            usage();
            System.exit(2);
        }

        Path worldFolder = Path.of(args[0]).toAbsolutePath().normalize();
        int centerX = Integer.parseInt(args[1]);
        int centerZ = Integer.parseInt(args[2]);
        boolean apply = args.length == 5
                && "--apply".equals(args[3])
                && "--backup-confirmed".equals(args[4]);
        if (args.length == 5 && !apply) {
            usage();
            System.exit(2);
        }
        if (!Files.isDirectory(worldFolder.resolve("db"))) {
            throw new IllegalArgumentException("World DB folder not found: " + worldFolder.resolve("db"));
        }

        LevelDBStorage storage = new LevelDBStorage(1, worldFolder.toString());
        try {
            DB db = storage.getDb();
            Set<Long> chunks = collectOverworldChunks(db);
            if (chunks.isEmpty()) {
                throw new IllegalStateException("No Overworld chunk records found; refusing to continue.");
            }

            Set<Long> candidates = new HashSet<>();
            int protectedChunks = 0;
            for (long packed : chunks) {
                int x = (int) (packed >> 32);
                int z = (int) packed;
                if (isProtected(x, z, centerX, centerZ)) protectedChunks++;
                else candidates.add(packed);
            }

            Path manifest = worldFolder.resolve("PNXRTP-cleanup-manifest.csv");
            writeManifest(manifest, candidates);
            System.out.println("World: " + worldFolder);
            System.out.println("Protected center chunk: " + centerX + "," + centerZ);
            System.out.println("Protected square: 21x21 (441 chunk positions; center +/- 10)");
            System.out.println("Distinct chunks found: " + chunks.size());
            System.out.println("Chunks inside protected square: " + protectedChunks);
            System.out.println("Chunks outside protected square: " + candidates.size());
            System.out.println("Manifest: " + manifest);
            if (!apply) {
                System.out.println("PREVIEW ONLY. Nothing was deleted.");
                System.out.println("After a full world backup, rerun with --apply --backup-confirmed.");
                return;
            }

            long deletedKeys = deleteChunkRecords(db, candidates);
            System.out.println("Deleted Overworld chunk-record keys: " + deletedKeys);
            System.out.println("Protected chunks were excluded from the deletion set.");
        } finally {
            storage.close();
        }
    }

    private static Set<Long> collectOverworldChunks(DB db) throws IOException {
        Set<Long> chunks = new HashSet<>();
        try (DBIterator iterator = db.iterator()) {
            iterator.seekToFirst();
            while (iterator.hasNext()) {
                byte[] key = iterator.next().getKey();
                if (!isOverworldChunkRecordKey(key)) continue;
                ByteBuffer coordinates = ByteBuffer.wrap(key, 0, 8).order(ByteOrder.LITTLE_ENDIAN);
                chunks.add(pack(coordinates.getInt(), coordinates.getInt()));
            }
        }
        return chunks;
    }

    private static long deleteChunkRecords(DB db, Set<Long> candidates) throws IOException {
        long deleted = 0;
        int inBatch = 0;
        try (WriteBatch batch = db.createWriteBatch(); DBIterator iterator = db.iterator()) {
            iterator.seekToFirst();
            while (iterator.hasNext()) {
                byte[] key = iterator.next().getKey();
                if (!isOverworldChunkRecordKey(key)) continue;
                ByteBuffer coordinates = ByteBuffer.wrap(key, 0, 8).order(ByteOrder.LITTLE_ENDIAN);
                long packed = pack(coordinates.getInt(), coordinates.getInt());
                if (!candidates.contains(packed)) continue;
                batch.delete(key);
                deleted++;
                inBatch++;
                if (inBatch >= BATCH_LIMIT) {
                    db.write(batch, new WriteOptions().sync(true));
                    batch.clear();
                    inBatch = 0;
                }
            }
            if (inBatch > 0) db.write(batch, new WriteOptions().sync(true));
        }
        return deleted;
    }

    private static boolean isOverworldChunkRecordKey(byte[] key) {
        if (key.length != 9 && key.length != 10) return false;
        int type = key[8] & 0xff;
        if (!CHUNK_RECORD_TYPES.contains(type)) return false;
        return key.length != 10 || type == '/';
    }

    private static boolean isProtected(int x, int z, int centerX, int centerZ) {
        return Math.abs((long) x - centerX) <= PROTECTED_RADIUS
                && Math.abs((long) z - centerZ) <= PROTECTED_RADIUS;
    }

    private static void writeManifest(Path manifest, Set<Long> candidates) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(manifest, StandardCharsets.UTF_8)) {
            writer.write("chunk_x,chunk_z,action");
            writer.newLine();
            for (long packed : candidates) {
                writer.write(((int) (packed >> 32)) + "," + ((int) packed) + ",candidate");
                writer.newLine();
            }
        }
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static void usage() {
        System.out.println("Preview: OfflineChunkCleaner <world-folder> <spawn-chunk-x> <spawn-chunk-z>");
        System.out.println("Delete:  OfflineChunkCleaner <world-folder> <spawn-chunk-x> <spawn-chunk-z> --apply --backup-confirmed");
        System.out.println("Stop PowerNukkitX and make a complete world backup before using --apply.");
    }
}
