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
 * Offline full-world chunk purger.
 *
 * Preserves a 21x21 chunk square in every recognized dimension and removes
 * recognized per-chunk LevelDB records outside it. Global/world/player records
 * and unknown key layouts are deliberately left untouched.
 *
 * Run only after PowerNukkitX has fully stopped and the complete world folder
 * has been copied to a separate backup location.
 */
public final class OfflineWorldPurger {
    private static final Set<Integer> CHUNK_RECORD_TYPES = Set.of(
            (int) '-', (int) '+', (int) ',', (int) '.', (int) '/',
            (int) '0', (int) '1', (int) '2', (int) '3', (int) '4',
            (int) '5', (int) '6', (int) '8', (int) '9', (int) 'v',
            (int) '|', (int) '}'
    );
    private static final int PROTECTED_RADIUS = 10;
    private static final int BATCH_LIMIT = 10_000;

    private OfflineWorldPurger() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3 && args.length != 5 && args.length != 7) {
            usage();
            System.exit(2);
        }

        Path worldFolder = Path.of(args[0]).toAbsolutePath().normalize();
        int centerX = Integer.parseInt(args[1]);
        int centerZ = Integer.parseInt(args[2]);
        boolean apply = args.length >= 5
                && "--apply".equals(args[3])
                && "--backup-confirmed".equals(args[4]);
        if (args.length >= 5 && !apply) {
            usage();
            System.exit(2);
        }
        Path pluginDataFolder = null;
        if (args.length == 7) {
            if (!"--plugin-data".equals(args[5])) {
                usage();
                System.exit(2);
            }
            pluginDataFolder = Path.of(args[6]).toAbsolutePath().normalize();
        }

        if (!Files.isDirectory(worldFolder.resolve("db"))
                || !Files.isRegularFile(worldFolder.resolve("level.dat"))) {
            throw new IllegalArgumentException("World folder must contain both db/ and level.dat: " + worldFolder);
        }

        Path manifest = worldFolder.resolve("PNXRTP-world-purge-manifest.csv");
        long uniqueChunks = 0;
        long protectedChunks = 0;
        long candidateChunks = 0;
        long deletedKeys = 0;

        LevelDBStorage storage = new LevelDBStorage(1, worldFolder.toString());
        try {
            DB db = storage.getDb();
            Set<ChunkIdentity> chunks = collectChunks(db);
            if (chunks.isEmpty()) {
                throw new IllegalStateException("No recognized chunk records found; refusing to continue.");
            }

            try (BufferedWriter writer = Files.newBufferedWriter(manifest, StandardCharsets.UTF_8)) {
                writer.write("dimension_id,chunk_x,chunk_z,action");
                writer.newLine();
                for (ChunkIdentity chunk : chunks) {
                    boolean keep = isProtected(chunk.x, chunk.z, centerX, centerZ);
                    if (keep) protectedChunks++;
                    else candidateChunks++;
                    writer.write(chunk.dimensionId + "," + chunk.x + "," + chunk.z + ","
                            + (keep ? "KEEP" : "DELETE"));
                    writer.newLine();
                }
            }

            uniqueChunks = chunks.size();
            System.out.println("PNXRTP full-world purge");
            System.out.println("World: " + worldFolder);
            System.out.println("Protected center chunk: " + centerX + "," + centerZ);
            System.out.println("Protected square: 21x21 chunks (441 positions) in every recognized dimension");
            System.out.println("Unique dimension/chunk coordinates: " + uniqueChunks);
            System.out.println("Coordinates inside protected square: " + protectedChunks);
            System.out.println("Coordinates outside protected square: " + candidateChunks);
            System.out.println("Manifest: " + manifest);

            if (!apply) {
                System.out.println("PREVIEW ONLY. No LevelDB records were changed.");
                System.out.println("After a full world-folder backup, rerun with --apply --backup-confirmed.");
                return;
            }

            deletedKeys = deleteOutsideProtectedSquare(db, centerX, centerZ);
            System.out.println("Deleted per-chunk LevelDB keys: " + deletedKeys);
            System.out.println("Protected square was excluded in every recognized dimension.");
            System.out.println("Unknown key layouts and global/world/player records were left untouched.");
        } finally {
            storage.close();
        }

        if (apply && pluginDataFolder != null) {
            Files.createDirectories(pluginDataFolder);
            Path marker = pluginDataFolder.resolve("world-purge.marker");
            String markerContent = worldFolder.getFileName() + System.lineSeparator()
                    + centerX + System.lineSeparator() + centerZ + System.lineSeparator()
                    + System.currentTimeMillis() + System.lineSeparator();
            Path temporary = marker.resolveSibling(marker.getFileName() + ".tmp");
            Files.writeString(temporary, markerContent, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, marker, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, marker, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            System.out.println("Generator reset marker written: " + marker);
            System.out.println("On next startup, PNXRTP will reset the saved spiral cursor for this world.");
            System.out.println("Start the generator with /rtpgen start (or enable auto-start separately).");
        }
    }

    private static Set<ChunkIdentity> collectChunks(DB db) throws IOException {
        Set<ChunkIdentity> chunks = new HashSet<>();
        try (DBIterator iterator = db.iterator()) {
            iterator.seekToFirst();
            while (iterator.hasNext()) {
                byte[] key = iterator.next().getKey();
                ChunkIdentity chunk = parseChunkKey(key);
                if (chunk != null) chunks.add(chunk);
            }
        }
        return chunks;
    }

    private static long deleteOutsideProtectedSquare(DB db, int centerX, int centerZ) throws IOException {
        long deleted = 0;
        int inBatch = 0;
        WriteBatch batch = db.createWriteBatch();
        try (DBIterator iterator = db.iterator()) {
            iterator.seekToFirst();
            while (iterator.hasNext()) {
                byte[] key = iterator.next().getKey();
                ChunkIdentity chunk = parseChunkKey(key);
                if (chunk == null || isProtected(chunk.x, chunk.z, centerX, centerZ)) continue;
                batch.delete(key);
                deleted++;
                inBatch++;
                if (inBatch >= BATCH_LIMIT) {
                    db.write(batch, new WriteOptions().sync(true));
                    batch.close();
                    batch = db.createWriteBatch();
                    inBatch = 0;
                }
            }
            if (inBatch > 0) db.write(batch, new WriteOptions().sync(true));
        } finally {
            batch.close();
        }
        return deleted;
    }

    private static ChunkIdentity parseChunkKey(byte[] key) {
        if (key == null) return null;
        final int typeIndex;
        final int dimensionId;
        if (key.length == 9 || key.length == 10) {
            typeIndex = 8;
            dimensionId = 0;
        } else if (key.length == 13 || key.length == 14) {
            typeIndex = 12;
            dimensionId = ByteBuffer.wrap(key, 8, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        } else {
            return null;
        }

        int type = key[typeIndex] & 0xff;
        if (!CHUNK_RECORD_TYPES.contains(type)) return null;
        boolean sectionRecord = type == '/' || type == '}';
        boolean correctLength = sectionRecord
                ? key.length == (typeIndex == 8 ? 10 : 14)
                : key.length == (typeIndex == 8 ? 9 : 13);
        if (!correctLength) return null;

        ByteBuffer coordinates = ByteBuffer.wrap(key, 0, 8).order(ByteOrder.LITTLE_ENDIAN);
        return new ChunkIdentity(dimensionId, coordinates.getInt(), coordinates.getInt());
    }

    private static boolean isProtected(int x, int z, int centerX, int centerZ) {
        return Math.abs((long) x - centerX) <= PROTECTED_RADIUS
                && Math.abs((long) z - centerZ) <= PROTECTED_RADIUS;
    }

    private record ChunkIdentity(int dimensionId, int x, int z) { }

    private static void usage() {
        System.out.println("Preview: OfflineWorldPurger <world-folder> <center-chunk-x> <center-chunk-z>");
        System.out.println("Delete:  OfflineWorldPurger <world-folder> <center-chunk-x> <center-chunk-z> --apply --backup-confirmed");
        System.out.println("Optional generator reset marker:");
        System.out.println("         add --plugin-data <PNXRTP-plugin-data-folder> after the apply flags.");
        System.out.println("STOP PowerNukkitX and make a complete copy of the entire world folder first.");
        System.out.println("The tool preserves 21x21 chunks in every recognized dimension, not only Overworld.");
    }
}
