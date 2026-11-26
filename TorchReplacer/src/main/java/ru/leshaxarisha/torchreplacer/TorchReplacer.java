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
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final List<String> detailedLog = new ArrayList<>();
    private final List<String> chunkLog = new ArrayList<>();
    private final List<String> worldErrors = new ArrayList<>();
    private String lastReportPath = "";
    private String runStartedAt = "";
    private String runFinishedAt = "";

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
            case "export" -> {
                if (running) {
                    sender.sendMessage("§eОтчёт можно экспортировать после остановки или завершения.");
                } else {
                    String path = writeReportFile();
                    sender.sendMessage(path == null ? "§cНе удалось создать отчёт. Проверь консоль."
                            : "§aОтчёт сохранён: §f" + path);
                }
            }
            case "broadcast" -> broadcastFullReport();
            default -> sender.sendMessage("§eИспользование: /torchreplace <start|status|cancel|export|broadcast> [world]");
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
        detailedLog.clear();
        chunkLog.clear();
        worldErrors.clear();
        lastReportPath = "";
        runStartedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        runFinishedAt = "";
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
                iterator.close();
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
                        String message = "WORLD ERROR | world=" + plan.level.getFolderName() + " | " + plan.error;
                        worldErrors.add(message);
                        detailedLog.add(message);
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
                String message = "INDEXING FAILED | " + error.getClass().getSimpleName() + ": " + error.getMessage();
                worldErrors.add(message);
                detailedLog.add(message);
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
            current.torchesFoundAtStart = torchesFound;
            current.torchesReplacedAtStart = torchesReplaced;
            current.failuresAtStart = replacementFailures;
        }

        if (!current.started) {
            current.wasLoaded = current.level.isChunkLoaded(current.chunkX, current.chunkZ);
            if (!current.level.loadChunk(current.chunkX, current.chunkZ, false)) {
                loadFailures++;
                String failed = "CHUNK LOAD FAILED | world=" + current.level.getFolderName()
                        + " | chunkX=" + current.chunkX + " | chunkZ=" + current.chunkZ;
                chunkLog.add(failed);
                detailedLog.add(failed);
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
                try {
                    replaceTorch(current.level, x, y, z, oldBlock);
                } catch (Throwable error) {
                    replacementFailures++;
                    String failure = "REPLACE EXCEPTION | world=" + current.level.getFolderName()
                            + " | x=" + x + " | y=" + y + " | z=" + z
                            + " | block=" + id + " | " + error.getClass().getSimpleName()
                            + ": " + error.getMessage();
                    detailedLog.add(failure);
                    getLogger().error("TorchReplacer: exception replacing block at " + x + "," + y + "," + z, error);
                }
            }

            current.columnIndex++;
            processedColumns++;
        }

        if (current.columnIndex >= 256) {
            String summary = "CHUNK DONE | world=" + current.level.getFolderName()
                    + " | chunkX=" + current.chunkX + " | chunkZ=" + current.chunkZ
                    + " | torchesFound=" + (torchesFound - current.torchesFoundAtStart)
                    + " | replaced=" + (torchesReplaced - current.torchesReplacedAtStart)
                    + " | failures=" + (replacementFailures - current.failuresAtStart);
            chunkLog.add(summary);
            detailedLog.add(summary);
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
        if (replacement == null || !replacement.getId().equals(originalId)
                || !replacement.getBlockState().equals(originalState)) {
            replacementFailures++;
            detailedLog.add("REPLACE FAILED | world=" + level.getFolderName() + " | x=" + x + " | y=" + y
                    + " | z=" + z + " | block=" + originalId + " | reason=state_mismatch");
            return;
        }

        boolean removed = level.setBlock(x, y, z, Block.get("minecraft:air"), true, true);
        if (!removed) {
            replacementFailures++;
            detailedLog.add("REMOVE FAILED | world=" + level.getFolderName() + " | x=" + x + " | y=" + y
                    + " | z=" + z + " | block=" + originalId);
            return;
        }

        if (level.setBlock(x, y, z, replacement, true, true)) {
            torchesReplaced++;
            detailedLog.add("REPLACED | world=" + level.getFolderName() + " | x=" + x + " | y=" + y
                    + " | z=" + z + " | block=" + originalId + " | state=" + originalState);
            return;
        }

        replacementFailures++;
        Block recovery = Block.get(originalState, level, x, y, z);
        if (!level.setBlock(x, y, z, recovery, true, true)) {
            unrecoverableFailures++;
            detailedLog.add("UNRECOVERABLE | world=" + level.getFolderName() + " | x=" + x + " | y=" + y
                    + " | z=" + z + " | block=" + originalId + " | reason=reinstall_failed");
            getLogger().error("TorchReplacer: failed to reinstall torch at " + x + "," + y + "," + z);
        } else {
            detailedLog.add("RESTORED_OLD_STATE | world=" + level.getFolderName() + " | x=" + x + " | y=" + y
                    + " | z=" + z + " | block=" + originalId);
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
        runFinishedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        getServer().getScheduler().cancelTask(this);
        lastReportPath = writeReportFile();
        getLogger().info("TorchReplacer finished: chunks=" + processedChunks
                + "/" + totalChunks
                + ", found=" + torchesFound
                + ", replaced=" + torchesReplaced
                + ", failures=" + replacementFailures
                + ", unrecoverable=" + unrecoverableFailures
                + ", loadFailures=" + loadFailures
                + ", report=" + (lastReportPath == null ? "WRITE_FAILED" : lastReportPath));
    }

    private void stopProcessing() {
        running = false;
        if (scanFuture != null && !scanFuture.isDone()) {
            scanFuture.cancel(true);
        }
        scanFuture = null;
        if (current != null && current.started) {
            String partial = "CHUNK INTERRUPTED | world=" + current.level.getFolderName()
                    + " | chunkX=" + current.chunkX + " | chunkZ=" + current.chunkZ
                    + " | columnsProcessed=" + current.columnIndex + "/256";
            chunkLog.add(partial);
            detailedLog.add(partial);
            releaseChunk(current);
        }
        current = null;
        runFinishedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        lastReportPath = writeReportFile();
        getServer().getScheduler().cancelTask(this);
    }

    private String writeReportFile() {
        try {
            Path folder = getDataFolder().toPath();
            Files.createDirectories(folder);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path report = folder.resolve("torch-replacer-" + stamp + ".log");
            try (BufferedWriter writer = Files.newBufferedWriter(report, StandardCharsets.UTF_8)) {
                writer.write("TorchReplacer 0.1.0 detailed report");
                writer.newLine();
                writer.write("Started: " + runStartedAt);
                writer.newLine();
                writer.write("Finished: " + (runFinishedAt.isEmpty() ? "not finished" : runFinishedAt));
                writer.newLine();
                writer.write("Status: " + (running ? "RUNNING" : "STOPPED_OR_FINISHED"));
                writer.newLine();
                writer.newLine();
                writer.write(summaryText());
                writer.newLine();
                writer.write("=== WORLD ERRORS ===");
                writer.newLine();
                for (String line : worldErrors) { writer.write(line); writer.newLine(); }
                writer.write("=== CHUNK RESULTS ===");
                writer.newLine();
                for (String line : chunkLog) { writer.write(line); writer.newLine(); }
                writer.write("=== EVERY TORCH CHANGE / FAILURE ===");
                writer.newLine();
                for (String line : detailedLog) { writer.write(line); writer.newLine(); }
            }
            return report.toAbsolutePath().toString();
        } catch (IOException error) {
            getLogger().error("TorchReplacer: cannot write detailed report", error);
            return null;
        }
    }

    private String summaryText() {
        return "Chunks indexed: " + totalChunks + "\n"
                + "Chunks processed: " + processedChunks + "\n"
                + "Chunk load failures: " + loadFailures + "\n"
                + "Torches found: " + torchesFound + "\n"
                + "New torches installed: " + torchesReplaced + "\n"
                + "Replacement failures: " + replacementFailures + "\n"
                + "Unrecoverable placements: " + unrecoverableFailures + "\n"
                + "Chunks not unloaded: " + chunksNotUnloaded + "\n"
                + "Detailed log entries: " + detailedLog.size();
    }

    private void broadcastFullReport() {
        getServer().broadcastMessage("§6===== TorchReplacer: FULL REPORT =====");
        getServer().broadcastMessage("§eStatus: " + (running ? "RUNNING" : "STOPPED / FINISHED"));
        for (String line : summaryText().split("\\n")) getServer().broadcastMessage("§e" + line);
        getServer().broadcastMessage("§6--- World errors ---");
        if (worldErrors.isEmpty()) getServer().broadcastMessage("§aNone");
        else for (String line : worldErrors) getServer().broadcastMessage("§c" + line);
        getServer().broadcastMessage("§6--- Chunk results ---");
        if (chunkLog.isEmpty()) getServer().broadcastMessage("§7No completed chunks yet");
        else for (String line : chunkLog) getServer().broadcastMessage("§f" + line);
        getServer().broadcastMessage("§6--- Individual torch changes / failures ---");
        if (detailedLog.isEmpty()) getServer().broadcastMessage("§7No changes recorded yet");
        else for (String line : detailedLog) getServer().broadcastMessage("§f" + line);
        getServer().broadcastMessage("§6Report file: " + (lastReportPath == null || lastReportPath.isEmpty()
                ? "not created yet" : lastReportPath));
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
        sender.sendMessage("§eЗаписей подробного журнала: §f" + detailedLog.size());
        sender.sendMessage("§eФайл отчёта: §f" + (lastReportPath == null || lastReportPath.isEmpty()
                ? "ещё не создан" : lastReportPath));
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
        private long torchesFoundAtStart;
        private long torchesReplacedAtStart;
        private long failuresAtStart;

        private ChunkCursor(Level level, int chunkX, int chunkZ) {
            this.level = level;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }
}
