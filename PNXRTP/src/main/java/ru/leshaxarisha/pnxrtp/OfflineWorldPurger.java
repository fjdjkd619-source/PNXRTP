package ru.leshaxarisha.pnxrtp;

import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.WriteBatch;
import org.iq80.leveldb.WriteOptions;
import org.powernukkitx.level.format.leveldb.LevelDBStorage;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

/**
 * Offline full-world chunk-record purger.
 * Preserves a 21x21 chunk square in every recognized dimension.
 * Uses disk-backed staging so memory stays bounded and never mutates LevelDB
 * while its iterator is open.
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
        Path stagedKeys = apply ? Files.createTempFile(worldFolder, ".pnx-purge-", ".keys") : null;
        long scannedKeys = 0;
        long protectedKeys = 0;
        long candidateKeys = 0;
        long deletedKeys = 0;
        boolean completed = false;

        try {
            LevelDBStorage storage = new LevelDBStorage(1, worldFolder.toString());
            try {
                DB db = storage.getDb();
                try (BufferedWriter writer = Files.newBufferedWriter(manifest, StandardCharsets.UTF_8);
                     DataOutputStream spool = apply
                             ? new DataOutputStream(Files.newOutputStream(stagedKeys))
                             : null;
                     DBIterator iterator = db.iterator()) {
                    writer.write("dimension_id,chunk_x,chunk_z,action");
                    writer.newLine();
                    iterator.seekToFirst();
                    while (iterator.hasNext()) {
                        byte[] key = iterator.next().getKey();
                        ChunkIdentity chunk = parseChunkKey(key);
                        if (chunk == null) continue;
                        scannedKeys++;
                        boolean keep = isProtected(chunk.x, chunk.z, centerX, centerZ);
                        if (keep) {
                            protectedKeys++;
                        } else {
                            candidateKeys++;
                            if (spool != null) {
                                spool.writeInt(key.length);
                                spool.write(key);
                            }
                        }
                        writer.write(chunk.dimensionId + "," + chunk.x + "," + chunk.z + ","
                                + (keep ? "KEEP" : "DELETE"));
                        writer.newLine();
                    }
                }
                if (scannedKeys == 0) {
                    throw new IllegalStateException("No recognized chunk records found; refusing to continue.");
                }

                System.out.println("PNXRTP full-world purge");
                System.out.println("World: " + worldFolder);
                System.out.println("Protected center chunk: " + centerX + "," + centerZ);
                System.out.println("Protected square: 21x21 chunk positions in every recognized dimension");
                System.out.println("Recognized per-chunk keys scanned: " + scannedKeys);
                System.out.println("Keys in protected square: " + protectedKeys);
                System.out.println("Keys staged for deletion: " + candidateKeys);
                System.out.println("Manifest: " + manifest);

                if (apply) {
                    deletedKeys = deleteStagedKeys(db, stagedKeys);
                }
            } finally {
                storage.close();
            }

            if (apply && pluginDataFolder != null) {
                writeGeneratorResetMarker(pluginDataFolder, worldFolder.getFileName().toString(), centerX, centerZ);
            }
            completed = true;
            if (apply) {
                System.out.println("Deleted per-chunk LevelDB keys: " + deletedKeys);
                System.out.println("Protected square excluded in every recognized dimension.");
                System.out.println("Unknown key layouts and global/world/player records were left untouched.");
            } else {
                System.out.println("PREVIEW ONLY. No LevelDB records were changed.");
                System.out.println("After checking this manifest and confirming a complete world backup, rerun with --apply --backup-confirmed.");
            }
        } finally {
            if (stagedKeys != null) Files.deleteIfExists(stagedKeys);
            if (!completed) System.err.println("Purge did not complete. If apply had begun, restore the full world-folder backup before starting PNX.");
        }
    }

    private static long deleteStagedKeys(DB db, Path stagedKeys) throws IOException {
        long deleted = 0;
        int inBatch = 0;
        WriteBatch batch = db.createWriteBatch();
        try (DataInputStream input = new DataInputStream(Files.newInputStream(stagedKeys))) {
            while (true) {
                int length;
                try {
                    length = input.readInt();
                } catch (EOFException end) {
                    break;
                }
                if (length < 1 || length > 64) {
                    throw new IOException("Invalid staged LevelDB key length: " + length);
                }
                byte[] key = input.readNBytes(length);
                if (key.length != length) throw new EOFException("Truncated staged key file");
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

    private static void writeGeneratorResetMarker(Path pluginDataFolder, String worldFolder,
                                                   int centerX, int centerZ) throws IOException {
        Files.createDirectories(pluginDataFolder);
        Path marker = pluginDataFolder.resolve("world-purge.marker");
        String content = worldFolder + System.lineSeparator()
                + centerX + System.lineSeparator() + centerZ + System.lineSeparator()
                + System.currentTimeMillis() + System.lineSeparator();
        Path temporary = marker.resolveSibling(marker.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, marker, StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println("Generator reset marker written: " + marker);
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
        int expectedLength = typeIndex == 8 ? (sectionRecord ? 10 : 9)
                : (sectionRecord ? 14 : 13);
        if (key.length != expectedLength) return null;
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
        System.out.println("Optional marker: append --plugin-data <PNXRTP-plugin-data-folder> after apply flags.");
        System.out.println("STOP PowerNukkitX and make a complete copy of the entire world folder first.");
    }
}
