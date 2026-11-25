package ru.leshaxarisha.torchclean;

import org.powernukkitx.Player;
import org.powernukkitx.block.Block;
import org.powernukkitx.block.BlockID;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.format.IChunk;
import org.powernukkitx.level.format.LevelProvider;
import org.powernukkitx.math.BlockVector3;
import org.powernukkitx.plugin.PluginBase;
import org.powernukkitx.scheduler.TaskHandler;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Bounded torch cleanup for the explicitly configured 21x21 chunk area.
 *
 * Scan is read-only. Removal is a separate, explicit command and is allowed
 * only after a complete successful scan in the current plugin session.
 */
public final class TorchClean extends PluginBase {
    private static final int MIN_CHUNK_X = -12;
    private static final int MAX_CHUNK_X = 8;
    private static final int MIN_CHUNK_Z = -8;
    private static final int MAX_CHUNK_Z = 12;
    private static final int WIDTH = MAX_CHUNK_X - MIN_CHUNK_X + 1;
    private static final int TOTAL_CHUNKS = WIDTH * (MAX_CHUNK_Z - MIN_CHUNK_Z + 1);

    // Deliberately excludes redstone torches: they are functional redstone components.
    // Wall-mounted and floor-mounted variants share these identifiers.
    private static final Set<String> TARGET_IDS = Set.of(
            "minecraft:torch",
            "minecraft:soul_torch",
            "minecraft:copper_torch"
    );

    private static final int REMOVALS_PER_TICK = 12;

    private TaskHandler task;
    private Session session;
    private boolean scanReady;
    private int lastScanTorchCount;
    private String lastStatus = "Сканирование ещё не запускалось.";

    @Override
    public void onEnable() {
        getLogger().info("TorchClean 0.1.0 enabled. No automatic world edits.");
    }

    @Override
    public void onDisable() {
        stopSession(false, true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!label.equalsIgnoreCase("torchclean")) return false;

        if (!sender.hasPermission("torchclean.use")) {
            sender.sendMessage("§cНедостаточно прав: torchclean.use");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманды нужно выполнять в игре от имени игрока.");
            return true;
        }

        Level level = player.getLevel();
        if (level == null || !"world".equalsIgnoreCase(level.getName())) {
            sender.sendMessage("§cTorchClean работает только в основном мире world.");
            return true;
        }

        String mode = args.length == 0 ? "status" : args[0].toLowerCase();
        switch (mode) {
            case "scan" -> startScan(sender, level);
            case "remove" -> startRemoval(sender, level);
            case "status" -> sendStatus(sender);
            case "stop" -> {
                stopSession(true, true);
                sender.sendMessage("§eTorchClean: операция остановлена. Уже обработанные чанки сохранены.");
            }
            default -> sender.sendMessage("§eИспользование: /torchclean <scan|remove|status|stop>");
        }
        return true;
    }

    private void startScan(CommandSender sender, Level level) {
        if (session != null) {
            sender.sendMessage("§cОперация уже выполняется. Используй /torchclean status или /torchclean stop.");
            return;
        }
        scanReady = false;
        lastScanTorchCount = 0;
        startSession(sender, level, Mode.SCAN);
        sender.sendMessage("§eTorchClean: запущено безопасное сканирование 441 чанка. Блоки не изменяются.");
        sender.sendMessage("§7Область: X [-12..8], Z [-8..12], мир world.");
    }

    private void startRemoval(CommandSender sender, Level level) {
        if (session != null) {
            sender.sendMessage("§cОперация уже выполняется.");
            return;
        }
        if (!scanReady || lastScanTorchCount <= 0) {
            sender.sendMessage("§cСначала выполни полный скан: /torchclean scan. Удаление без предварительного скана запрещено.");
            return;
        }
        scanReady = false;
        startSession(sender, level, Mode.REMOVE);
        sender.sendMessage("§cTorchClean: запущено удаление. Будут затронуты только обычные, душевные и медные факелы.");
        sender.sendMessage("§7Красные факелы редстоуна и все остальные блоки не затрагиваются.");
        sender.sendMessage("§7Максимум " + REMOVALS_PER_TICK + " факелов за тик; каждый обработанный чанк сохраняется.");
    }

    private void startSession(CommandSender sender, Level level, Mode mode) {
        session = new Session(sender, level, mode);
        task = getServer().getScheduler().scheduleRepeatingTask(this, this::tick, 1);
        lastStatus = (mode == Mode.SCAN ? "сканирование запущено" : "удаление запущено");
    }

    private void tick() {
        Session current = session;
        if (current == null) {
            cancelTask();
            return;
        }

        if (current.activeChunk != null) {
            processRemovalBatch(current);
            return;
        }

        if (current.chunkIndex >= TOTAL_CHUNKS) {
            finish(current);
            return;
        }

        int chunkX = MIN_CHUNK_X + (current.chunkIndex % WIDTH);
        int chunkZ = MIN_CHUNK_Z + (current.chunkIndex / WIDTH);
        current.chunkIndex++;

        processOneChunk(current, chunkX, chunkZ);

        if (current.chunkIndex % 35 == 0) {
            current.sender.sendMessage("§7TorchClean: " + current.chunkIndex + "/" + TOTAL_CHUNKS +
                    " чанков; найдено=" + current.found +
                    (current.mode == Mode.REMOVE ? "; удалено=" + current.removed : "") +
                    "; ошибок=" + current.errors + ".");
        }
    }

    private void processOneChunk(Session current, int chunkX, int chunkZ) {
        Level level = current.level;
        LevelProvider provider = level.getProvider();
        boolean wasLoaded = provider.getLoadedChunk(chunkX, chunkZ) != null;

        try {
            if (!wasLoaded && !level.loadChunk(chunkX, chunkZ, false)) {
                current.missing++;
                current.report.add("chunk=" + chunkX + "," + chunkZ + " result=MISSING");
                return;
            }

            IChunk chunk = provider.getLoadedChunk(chunkX, chunkZ);
            if (chunk == null) {
                current.missing++;
                current.report.add("chunk=" + chunkX + "," + chunkZ + " result=NOT_LOADED");
                return;
            }

            if (!chunk.isGenerated()) {
                current.ungenerated++;
                current.report.add("chunk=" + chunkX + "," + chunkZ + " result=UNGENERATED");
                return;
            }

            int minY = provider.getDimensionData().getMinHeight();
            int maxY = provider.getDimensionData().getMaxHeight();
            int minX = chunkX << 4;
            int minZ = chunkZ << 4;

            List<Block> torches = chunk.scanBlocks(
                    new BlockVector3(minX, minY, minZ),
                    new BlockVector3(minX + 15, maxY, minZ + 15),
                    (position, state) -> TARGET_IDS.contains(state.getIdentifier())
            ).toList();

            current.found += torches.size();
            current.report.add("chunk=" + chunkX + "," + chunkZ + " result=SCANNED torches=" + torches.size());

            if (current.mode == Mode.SCAN || torches.isEmpty()) {
                return;
            }

            current.activeChunk = chunk;
            current.activeChunkX = chunkX;
            current.activeChunkZ = chunkZ;
            current.activeWasLoaded = wasLoaded;
            current.activeTorches = torches;
            current.activeTorchIndex = 0;
            current.activeRemoved = 0;
            current.activeFailed = 0;
            return;
        } catch (Throwable error) {
            current.errors++;
            current.report.add("chunk=" + chunkX + "," + chunkZ + " result=ERROR type=" +
                    error.getClass().getSimpleName() + " message=" + safeMessage(error));
            getLogger().error("TorchClean failed at chunk " + chunkX + "," + chunkZ, error);
        } finally {
            if (current.activeChunk == null) {
                releaseChunk(level, chunkX, chunkZ, wasLoaded);
            }
        }
    }

    private void processRemovalBatch(Session current) {
        int end = Math.min(current.activeTorchIndex + REMOVALS_PER_TICK, current.activeTorches.size());
        for (; current.activeTorchIndex < end; current.activeTorchIndex++) {
            Block torch = current.activeTorches.get(current.activeTorchIndex);
            try {
                boolean changed = current.level.setBlock(
                        torch.getFloorX(),
                        torch.getFloorY(),
                        torch.getFloorZ(),
                        Block.get(BlockID.AIR),
                        true,
                        true
                );
                if (changed) {
                    current.activeRemoved++;
                    current.removed++;
                } else {
                    current.activeFailed++;
                    current.failed++;
                }
            } catch (Throwable error) {
                current.activeFailed++;
                current.failed++;
                getLogger().error("TorchClean could not remove torch at " +
                        torch.getFloorX() + "," + torch.getFloorY() + "," + torch.getFloorZ(), error);
            }
        }

        if (current.activeTorchIndex >= current.activeTorches.size()) {
            finishActiveChunk(current);
        }
    }

    private void finishActiveChunk(Session current) {
        int x = current.activeChunkX;
        int z = current.activeChunkZ;
        try {
            if (current.activeRemoved > 0) {
                current.level.getProvider().saveChunk(x, z);
            }
            current.report.add("chunk=" + x + "," + z + " removalComplete removed=" +
                    current.activeRemoved + " failed=" + current.activeFailed);
        } catch (Throwable error) {
            current.errors++;
            current.report.add("chunk=" + x + "," + z + " SAVE_ERROR type=" +
                    error.getClass().getSimpleName() + " message=" + safeMessage(error));
            getLogger().error("TorchClean could not save chunk " + x + "," + z, error);
        } finally {
            // Keep modified chunks resident so queued block-light updates can finish.
            // The server's normal unload queue will unload them when safe.
            if (current.activeRemoved == 0) {
                releaseChunk(current.level, x, z, current.activeWasLoaded);
            }
            current.activeChunk = null;
            current.activeTorches = null;
            current.activeTorchIndex = 0;
            current.activeRemoved = 0;
            current.activeFailed = 0;
        }
    }

    private void releaseChunk(Level level, int x, int z, boolean wasLoaded) {
        if (!wasLoaded) {
            // Safe unload: chunks still in use by players/ticking areas remain loaded.
            level.unloadChunk(x, z, true, false);
        }
    }

    private void finish(Session completed) {
        if (session != completed) return;

        completed.report.add("");
        completed.report.add("SUMMARY mode=" + completed.mode +
                " checked=" + completed.chunkIndex +
                " missing=" + completed.missing +
                " ungenerated=" + completed.ungenerated +
                " found=" + completed.found +
                " removed=" + completed.removed +
                " failed=" + completed.failed +
                " errors=" + completed.errors);

        boolean saved = writeReport(completed.report);
        if (completed.mode == Mode.SCAN) {
            scanReady = completed.errors == 0 && completed.chunkIndex == TOTAL_CHUNKS;
            lastScanTorchCount = completed.found;
            lastStatus = "сканирование завершено: найдено " + completed.found +
                    ", ошибок " + completed.errors + (saved ? ", отчёт сохранён" : ", отчёт не сохранён");
            completed.sender.sendMessage("§aTorchClean: сканирование завершено.");
            completed.sender.sendMessage("§eНайдено факелов: " + completed.found +
                    "; отсутствующих чанков: " + completed.missing +
                    "; незавершённых: " + completed.ungenerated +
                    "; ошибок: " + completed.errors + ".");
            if (scanReady && completed.found > 0) {
                completed.sender.sendMessage("§cДля удаления введи отдельную команду: /torchclean remove");
            } else if (completed.found == 0) {
                completed.sender.sendMessage("§aЦелевых факелов не найдено. Удалять нечего.");
            } else {
                completed.sender.sendMessage("§cБыли ошибки сканирования. Удаление заблокировано до повторного успешного скана.");
            }
        } else {
            lastStatus = "удаление завершено: удалено " + completed.removed +
                    ", не удалось " + completed.failed + ", ошибок " + completed.errors +
                    (saved ? ", отчёт сохранён" : ", отчёт не сохранён");
            completed.sender.sendMessage("§aTorchClean: удаление завершено.");
            completed.sender.sendMessage("§eНайдено при проходе: " + completed.found +
                    "; удалено: " + completed.removed +
                    "; не удалось удалить: " + completed.failed +
                    "; ошибок: " + completed.errors + ".");
        }

        if (saved) completed.sender.sendMessage("§aОтчёт: plugins/TorchClean/torchclean-report.txt");
        cancelTask();
        session = null;
    }

    private void sendStatus(CommandSender sender) {
        if (session == null) {
            sender.sendMessage("§eTorchClean: " + lastStatus +
                    (scanReady ? "; предварительный скан действителен, найдено " + lastScanTorchCount : ""));
            return;
        }
        sender.sendMessage("§eTorchClean: " + session.mode +
                "; чанки " + session.chunkIndex + "/" + TOTAL_CHUNKS +
                "; найдено=" + session.found +
                "; удалено=" + session.removed +
                "; ошибок=" + session.errors + ".");
    }

    private void stopSession(boolean notify, boolean saveActiveChunk) {
        Session current = session;
        if (current == null) {
            cancelTask();
            return;
        }

        if (saveActiveChunk && current.activeChunk != null) {
            finishActiveChunk(current);
        }

        current.report.add("STOPPED at=" + current.chunkIndex + "/" + TOTAL_CHUNKS);
        boolean saved = writeReport(current.report);
        lastStatus = "остановлено на " + current.chunkIndex + "/" + TOTAL_CHUNKS +
                (saved ? "; отчёт сохранён" : "; отчёт не сохранён");
        scanReady = false;
        lastScanTorchCount = 0;

        cancelTask();
        session = null;
        if (notify && saved) current.sender.sendMessage("§7Частичный отчёт: plugins/TorchClean/torchclean-report.txt");
    }

    private void cancelTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private boolean writeReport(List<String> lines) {
        File folder = getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) return false;
        try {
            Files.write(new File(folder, "torchclean-report.txt").toPath(), lines, StandardCharsets.UTF_8);
            return true;
        } catch (IOException error) {
            getLogger().error("Could not write TorchClean report", error);
            return false;
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null ? "(no message)" : message.replace('\n', ' ').replace('\r', ' ');
    }

    private enum Mode { SCAN, REMOVE }

    private static final class Session {
        final CommandSender sender;
        final Level level;
        final Mode mode;
        final List<String> report = new ArrayList<>();
        int chunkIndex;
        int missing;
        int ungenerated;
        int found;
        int removed;
        int failed;
        int errors;

        IChunk activeChunk;
        int activeChunkX;
        int activeChunkZ;
        boolean activeWasLoaded;
        List<Block> activeTorches;
        int activeTorchIndex;
        int activeRemoved;
        int activeFailed;

        Session(CommandSender sender, Level level, Mode mode) {
            this.sender = sender;
            this.level = level;
            this.mode = mode;
            report.add("TorchClean 0.1.0");
            report.add("Started: " + Instant.now());
            report.add("Mode: " + mode);
            report.add("World: " + level.getName());
            report.add("Area: chunk X [" + MIN_CHUNK_X + ".." + MAX_CHUNK_X +
                    "], Z [" + MIN_CHUNK_Z + ".." + MAX_CHUNK_Z + "], total=" + TOTAL_CHUNKS);
            report.add("Target block identifiers: " + TARGET_IDS);
            report.add("Only generated existing chunks are scanned; chunks are never generated.");
            report.add("Only layer 0 is scanned. Redstone torches and all non-target blocks are excluded.");
        }
    }
}
