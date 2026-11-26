package ru.leshaxarisha.torchreplacer;

import org.iq80.leveldb.DBIterator;
import org.powernukkitx.block.Block;
import org.powernukkitx.block.BlockState;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.format.leveldb.LevelDBProvider;
import org.powernukkitx.level.format.leveldb.LevelDBStorage;
import org.powernukkitx.level.util.LevelDBKeyUtil;
import org.powernukkitx.plugin.PluginBase;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Replaces torches in every existing saved chunk without intentionally generating chunks.
 * Targets PowerNukkitX 3.0.6 / API 3.0.6 and Java 21.
 */
public final class TorchReplacer extends PluginBase {
    private static final int COLUMNS_PER_TICK = 64;

    private final ArrayDeque<ChunkCursor> chunks = new ArrayDeque<>();

    private volatile boolean running;
    private volatile CompletableFuture<List<WorldPlan>> scanFuture;

    private ChunkCursor current;
    private long totalChunks;
    private long processedChunks;
    private long loadFailures;
    private long torchesFound;
    private long torchesReplaced;
    private long replacementFailures;
    private long unrecoverableFailures;
    private long chunksNotUnloaded;

    @Override
    public void onEnable() {
        getLogger().info("TorchReplacer 0.1.0 enabled for PowerNukkitX API 3.0.6");
    }

    @Override
    public void onDisable() {
        stopProcessing();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!label.equalsIgnoreCase("torchreplace")) {
            return false;
        }
        if (!sender.hasPermission("torchreplacer.admin")) {
            sender.sendMessage("§cНедостаточно прав. Требуется torchreplacer.admin.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendStatus(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> start(sender, args);
            case "cancel" -> {
                if (!running) {
                    sender.sendMessage("§eЗамена сейчас не выполняется.");
                } else {
                    stopProcessing();
                    sender.sendMessage("§cЗамена остановлена. Уже обработанные чанки сохранены.");
                }
            }
            default -> sender.sendMessage("§eИспользование: /torchreplace <start|status|cancel> [world]");
        }
        return true;
    }

    private void start(CommandSender sender, String[] args) {
        if (running) {
            sender.sendMessage("§eЗамена уже выполняется. Используй /torchreplace status.");
            return;
        }

        List<Level> selected = new ArrayList<>();
        if (args.length >= 2) {
            Level requested = getServer().getLevelByName(args[1]);
            if (requested == null) {
                for (Level level : getServer().getLevels().values()) {
                    if (level.getFolderName().equalsIgnoreCase(args[1])) {
                        requested = level;
                        break;
                    }
                }
            }
            if (requested == null) {
                sender.sendMessage("§cМир не найден среди загруженных: " + args[1]);
                return;
            }
            selected.add(requested);
        } else {
            selected.addAll(getServer().getLevels().values());
        }

        if (selected.isEmpty()) {
            sender.sendMessage("§cНет загруженных миров для обработки.");
            return;
        }

        chunks.clear();
        current = null;
        totalChunks = processedChunks = loadFailures = 0;
        torchesFound = torchesReplaced = replacementFailures = unrecoverableFailures = 0;
        chunksNotUnloaded = 0;
        running = true;

        sender.sendMessage("§aTorchReplacer: индексирую существующие чанки в " + selected.size() + " мире(ах).");
        getLogger().info("TorchReplacer: indexing saved chunks in " + selected.size() + " loaded world(s)");

        scanFuture = CompletableFuture.supplyAsync(() -> indexWorlds(selected));
        getServer().getScheduler().scheduleRepeatingTask(this, this::tick, 1);
    }

    private List<WorldPlan> indexWorlds(List<Level> levels) {
        List<WorldPlan> plans = new ArrayList<>();
        for (Level level : levels) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            plans.add(indexWorld(level));
        }
        return plans;
    }

    private WorldPlan indexWorld(Level level) {
        if (!(level.getProvider() instanceof LevelDBProvider provider)) {
            return new WorldPlan(level, List.of(), "Провайдер мира не является LevelDB.");
        }

        Set<Long> unique = new HashSet<>();
        DBIterator iterator = null;
        try {
            LevelDBStorage storage = provider.getStorage();
            byte[] template = LevelDBKeyUtil.VERSION.getKey(0, 0, provider.getDimensionData());
            byte typeByte = template[template.length - 1];

            iterator = storage.getDb().iterator();
            iterator.seekToFirst();
            while (iterator.hasNext()) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                Map.Entry<byte[], byte[]> entry = iterator.next();
                byte[] key = entry.getKey();
                if (key.length != template.length || key[key.length - 1] != typeByte) {
                    continue;
                }

                boolean sameDimension = true;
                for (int i = 8; i < key.length - 1; i++) {
                    if (key[i] != template[i]) {
                        sameDimension = false;
                        break;
                    }
                }
                if (!sameDimension) {
                    continue;
                }

                ByteBuffer coordinates = ByteBuffer.wrap(key).order(ByteOrder.LITTLE_ENDIAN);
                int chunkX = coordinates.getInt();
                int chunkZ = coordinates.getInt();
                unique.add(pack(chunkX, chunkZ));
            }
        } catch (Throwable error) {
            getLogger().error("TorchReplacer: failed to index world " + level.getFolderName(), error);
            return new WorldPlan(level, List.of(), error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            if (iterator != null) {
                try {
                    iterator.close();
                } catch (IOException ignored) {
                }
            }
        }

        return new WorldPlan(level, new ArrayList<>(unique), null);
    }

    private void tick() {
        if (!running) {
            return;
        }

        if (scanFuture != null && !scanFuture.isDone()) {
            return;
        }

        if (scanFuture != null) {
            try {
                List<WorldPlan> plans = scanFuture.join();
                scanFuture = null;
                for (WorldPlan plan : plans) {
                    if (plan.error != null) {
                        getLogger().error("TorchReplacer: skipped world " + plan.level.getFolderName() + ": " + plan.error);
                        continue;
                    }
                    for (long packed : plan.chunkCoordinates) {
                        chunks.addLast(new ChunkCursor(plan.level, (int) (packed >> 32), (int) packed));
                    }
                }
                totalChunks = chunks.size();
                getLogger().info("TorchReplacer: found " + totalChunks + " existing chunk records");
            } catch (Throwable error) {
                getLogger().error("TorchReplacer: indexing failed", error);
                stopProcessing();
                return;
            }
        }

        if (current == null) {
            current = chunks.pollFirst();
            if (current == null) {
                finish();
                return;
            }
        }

        if (!current.started) {
            current.wasLoaded = current.level.isChunkLoaded(current.chunkX, current.chunkZ);
            if (!current.level.loadChunk(current.chunkX, current.chunkZ, false)) {
                loadFailures++;
                processedChunks++;
                current = null;
                return;
            }
            current.loadedByPlugin = !current.wasLoaded;
            current.started = true;
        }

        int processedColumns = 0;
        int minY = current.level.getMinHeight();
        int maxY = current.level.getMaxHeight();

        while (current.columnIndex < 256 && processedColumns < COLUMNS_PER_TICK) {
            int localX = current.columnIndex & 15;
            int localZ = current.columnIndex >> 4;
            int x = (current.chunkX << 4) + localX;
            int z = (current.chunkZ << 4) + localZ;

            for (int y = minY; y < maxY; y++) {
                Block oldBlock = current.level.getBlock(x, y, z);
                String id = oldBlock.getId();
                if (!isTorch(id)) {
                    continue;
                }

                torchesFound++;
                replaceTorch(current.level, x, y, z, oldBlock);
            }

            current.columnIndex++;
            processedColumns++;
        }

        if (current.columnIndex >= 256) {
            releaseChunk(current);
            processedChunks++;
            current = null;
        }
    }

    private void replaceTorch(Level level, int x, int y, int z, Block oldBlock) {
        BlockState originalState = oldBlock.getBlockState();
        String originalId = oldBlock.getId();

        // Block.get(state, level, coordinates) asks the PNX registry for a fresh Block instance.
        // The immutable state preserves torch type and all orientation properties.
        Block replacement = Block.get(originalState, level, x, y, z);
        if (!replacement.getId().equals(originalId)
                || !replacement.getBlockState().equals(originalState)) {
            replacementFailures++;
            return;
        }

        boolean removed = level.setBlock(x, y, z, Block.get("minecraft:air"), true, true);
        if (!removed) {
            replacementFailures++;
            return;
        }

        if (level.setBlock(x, y, z, replacement, true, true)) {
            torchesReplaced++;
            return;
        }

        replacementFailures++;
        Block recovery = Block.get(originalState, level, x, y, z);
        if (!level.setBlock(x, y, z, recovery, true, true)) {
            unrecoverableFailures++;
            getLogger().error("TorchReplacer: failed to reinstall torch at " + x + "," + y + "," + z);
        }
    }

    private boolean isTorch(String id) {
        return id != null && id.toLowerCase().endsWith("torch");
    }

    private void releaseChunk(ChunkCursor cursor) {
        if (cursor.loadedByPlugin) {
            boolean unloaded = cursor.level.unloadChunk(cursor.chunkX, cursor.chunkZ, true, true);
            if (!unloaded) {
                chunksNotUnloaded++;
            }
        }
    }

    private void finish() {
        running = false;
        getServer().getScheduler().cancelTask(this);
        getLogger().info("TorchReplacer finished: chunks=" + processedChunks
                + "/" + totalChunks
                + ", found=" + torchesFound
                + ", replaced=" + torchesReplaced
                + ", failures=" + replacementFailures
                + ", unrecoverable=" + unrecoverableFailures
                + ", loadFailures=" + loadFailures);
    }

    private void stopProcessing() {
        running = false;
        if (scanFuture != null && !scanFuture.isDone()) {
            scanFuture.cancel(true);
        }
        scanFuture = null;
        if (current != null && current.started) {
            releaseChunk(current);
        }
        current = null;
        getServer().getScheduler().cancelTask(this);
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage("§e----- TorchReplacer 0.1.0 -----");
        sender.sendMessage("§eСтатус: " + (running ? "§aвыполняется" : "§cостановлен"));
        sender.sendMessage("§eЧанки: §f" + processedChunks + " / " + totalChunks);
        sender.sendMessage("§eФакелов найдено: §f" + torchesFound);
        sender.sendMessage("§eНовых факелов установлено: §f" + torchesReplaced);
        sender.sendMessage("§eОшибки замены: §f" + replacementFailures);
        sender.sendMessage("§eНе удалось восстановить после ошибки: §f" + unrecoverableFailures);
        sender.sendMessage("§eОшибки загрузки чанков: §f" + loadFailures);
        sender.sendMessage("§eЧанки, которые не удалось выгрузить: §f" + chunksNotUnloaded);
    }

    private static boolean isTorchRecordKey(byte[] key, byte[] template, byte typeByte) {
        if (key.length != template.length || key[key.length - 1] != typeByte) {
            return false;
        }
        for (int i = 8; i < key.length - 1; i++) {
            if (key[i] != template[i]) {
                return false;
            }
        }
        return true;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static final class WorldPlan {
        private final Level level;
        private final List<Long> chunkCoordinates;
        private final String error;

        private WorldPlan(Level level, List<Long> chunkCoordinates, String error) {
            this.level = level;
            this.chunkCoordinates = chunkCoordinates;
            this.error = error;
        }
    }

    private static final class ChunkCursor {
        private final Level level;
        private final int chunkX;
        private final int chunkZ;
        private int columnIndex;
        private boolean started;
        private boolean wasLoaded;
        private boolean loadedByPlugin;

        private ChunkCursor(Level level, int chunkX, int chunkZ) {
            this.level = level;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }
}
