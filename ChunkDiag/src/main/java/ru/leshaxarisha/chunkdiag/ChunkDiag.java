package ru.leshaxarisha.chunkdiag;

import org.powernukkitx.Player;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.format.IChunk;
import org.powernukkitx.level.format.LevelProvider;
import org.powernukkitx.plugin.PluginBase;
import org.powernukkitx.scheduler.TaskHandler;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Bounded diagnostics for the protected area.
 * "full" loads only chunks already present in storage (create=false),
 * one chunk every two ticks. It never creates chunks or changes blocks/flags.
 */
public final class ChunkDiag extends PluginBase {
    private static final int MIN_CHUNK_X = -12;
    private static final int MAX_CHUNK_X = 8;
    private static final int MIN_CHUNK_Z = -8;
    private static final int MAX_CHUNK_Z = 12;

    private TaskHandler scanTask;
    private ScanSession activeScan;

    @Override
    public void onEnable() {
        getLogger().info("ChunkDiag 1.1.0 enabled (bounded full scan; no chunk generation or world edits).");
    }

    @Override
    public void onDisable() {
        stopScan(false);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!label.equalsIgnoreCase("chunkdiag")) return false;
        if (!sender.hasPermission("chunkdiag.use")) {
            sender.sendMessage("§cНедостаточно прав: chunkdiag.use");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманду нужно выполнить в игре от имени игрока.");
            return true;
        }

        String mode = args.length == 0 ? "area" : args[0].toLowerCase();
        Level level = player.getLevel();
        if (level == null) {
            sender.sendMessage("§cМир игрока не найден.");
            return true;
        }

        if (mode.equals("here")) {
            int cx = player.getFloorX() >> 4;
            int cz = player.getFloorZ() >> 4;
            inspectOne(sender, level, cx, cz);
            return true;
        }
        if (mode.equals("stop")) {
            stopScan(true);
            sender.sendMessage("§eChunkDiag: сканирование остановлено.");
            return true;
        }
        if (mode.equals("full")) {
            startFullScan(sender, level);
            return true;
        }
        if (mode.equals("area")) {
            writeLoadedOnlyReport(sender, level);
            return true;
        }
        sender.sendMessage("§eИспользование: /chunkdiag <area|full|here|stop>");
        return true;
    }

    private void inspectOne(CommandSender sender, Level level, int x, int z) {
        IChunk chunk = level.getProvider().getLoadedChunk(x, z);
        if (chunk == null) {
            sender.sendMessage("§eЧанк " + x + "," + z + " сейчас не загружен.");
            return;
        }
        String line = describe(x, z, chunk);
        sender.sendMessage("§eChunkDiag: §f" + line);
        getLogger().info("ChunkDiag here: " + line);
    }

    private void writeLoadedOnlyReport(CommandSender sender, Level level) {
        List<String> report = new ArrayList<>();
        report.add("ChunkDiag 1.1.0 - LOADED ONLY");
        report.add("Area: chunk X [" + MIN_CHUNK_X + ".." + MAX_CHUNK_X + "], Z [" +
                MIN_CHUNK_Z + ".." + MAX_CHUNK_Z + "]");
        int loaded = 0;
        for (int z = MIN_CHUNK_Z; z <= MAX_CHUNK_Z; z++) {
            for (int x = MIN_CHUNK_X; x <= MAX_CHUNK_X; x++) {
                IChunk chunk = level.getProvider().getLoadedChunk(x, z);
                if (chunk != null) {
                    loaded++;
                    report.add(describe(x, z, chunk));
                }
            }
        }
        report.add("SUMMARY loaded=" + loaded + "/441");
        if (writeReport(report, sender)) {
            sender.sendMessage("§aChunkDiag: проверено уже загруженных чанков: §f" + loaded + "/441");
        }
    }

    private void startFullScan(CommandSender sender, Level level) {
        if (activeScan != null) {
            sender.sendMessage("§eСканирование уже выполняется. Используй /chunkdiag stop для остановки.");
            return;
        }
        activeScan = new ScanSession(sender, level, level.getProvider());
        sender.sendMessage("§aChunkDiag: полная проверка начата. До 1 чанка каждые 2 тика.");
        sender.sendMessage("§eЗагружаются только уже существующие чанки (create=false); новые не генерируются.");
        scanTask = getServer().getScheduler().scheduleRepeatingTask(this, () -> scanStep(), 2);
    }

    private void scanStep() {
        ScanSession scan = activeScan;
        if (scan == null) {
            stopScan(false);
            return;
        }
        try {
            if (scan.index >= 441) {
                finishScan(scan);
                return;
            }

            int x = MIN_CHUNK_X + (scan.index % 21);
            int z = MIN_CHUNK_Z + (scan.index / 21);
            scan.index++;

            IChunk chunk = scan.provider.getLoadedChunk(x, z);
            boolean wasLoaded = chunk != null;
            if (chunk == null) {
                // LevelDBProvider.isChunkGenerated() is an index-level shortcut in this PNX version.
                // loadChunk(..., false) is the authoritative non-creating read: it returns false
                // when no stored chunk exists and never creates terrain.
                boolean requested = scan.provider.loadChunk(x, z, false);
                chunk = scan.provider.getLoadedChunk(x, z);
                if (!requested || chunk == null) {
                    scan.report.add("chunk=" + x + "," + z + " status=NOT_STORED_OR_LOAD_NOT_CONFIRMED");
                    scan.loadFailed++;
                    return;
                }
                scan.loadedByDiagnostic++;
            } else {
                scan.alreadyLoaded++;
            }

            scan.loaded++;
            String line = describe(x, z, chunk) + " loadedBeforeScan=" + wasLoaded;
            scan.report.add(line);
            if (chunk.isGenerated()) scan.generatedTrue++;
            else scan.generatedFalse++;
            if (chunk.isLightPopulated()) scan.lightTrue++;
            else scan.lightFalse++;
            if (chunk.hasLegacyStates()) scan.legacyStates++;
        } catch (Throwable error) {
            scan.errors++;
            scan.report.add("scanIndex=" + scan.index + " ERROR=" + error.getClass().getSimpleName() + ":" + error.getMessage());
            getLogger().error("ChunkDiag full scan step failed", error);
        }

        if (scan.index % 35 == 0) {
            scan.sender.sendMessage("§7ChunkDiag: " + scan.index + "/441, loaded=" + scan.loaded +
                    ", lightPopulated=false=" + scan.lightFalse + ".");
        }
        if (scan.index >= 441) finishScan(scan);
    }

    private void finishScan(ScanSession scan) {
        if (activeScan != scan) return;
        scan.report.add("");
        scan.report.add("SUMMARY total=441 loaded=" + scan.loaded + " generatedTrue=" + scan.generatedTrue +
                " generatedFalse=" + scan.generatedFalse + " lightTrue=" + scan.lightTrue +
                " lightFalse=" + scan.lightFalse + " legacyStates=" + scan.legacyStates +
                " notPresent=" + scan.notPresent + " loadFailed=" + scan.loadFailed +
                " errors=" + scan.errors + " alreadyLoaded=" + scan.alreadyLoaded +
                " loadedByDiagnostic=" + scan.loadedByDiagnostic);
        boolean saved = writeReport(scan.report, scan.sender);
        scan.sender.sendMessage("§aChunkDiag завершён: loaded=" + scan.loaded + "/441, light true/false=" +
                scan.lightTrue + "/" + scan.lightFalse + ", legacyStates=" + scan.legacyStates + ".");
        if (saved) scan.sender.sendMessage("§aОтчёт: plugins/ChunkDiag/chunkdiag-report.txt");
        stopScan(false);
    }

    private void stopScan(boolean notify) {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        if (activeScan != null && notify) {
            activeScan.sender.sendMessage("§eChunkDiag: сканирование остановлено на " + activeScan.index + "/441.");
        }
        activeScan = null;
    }

    private boolean writeReport(List<String> report, CommandSender sender) {
        File folder = getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            sender.sendMessage("§cНе удалось создать папку отчёта.");
            return false;
        }
        File output = new File(folder, "chunkdiag-report.txt");
        try {
            Files.write(output.toPath(), report, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            getLogger().error("ChunkDiag: failed to write report", e);
            sender.sendMessage("§cНе удалось сохранить отчёт. Подробности в консоли.");
            return false;
        }
    }

    private String describe(int x, int z, IChunk chunk) {
        return "chunk=" + x + "," + z +
                " loaded=" + chunk.isLoaded() +
                " generated=" + chunk.isGenerated() +
                " populated=" + chunk.isPopulated() +
                " lightPopulated=" + chunk.isLightPopulated() +
                " legacyStates=" + chunk.hasLegacyStates() +
                " chunkState=" + chunk.getChunkState();
    }

    private static final class ScanSession {
        private final CommandSender sender;
        private final Level level;
        private final LevelProvider provider;
        private final List<String> report = new ArrayList<>();
        private int index;
        private int loaded;
        private int generatedTrue;
        private int generatedFalse;
        private int lightTrue;
        private int lightFalse;
        private int legacyStates;
        private int notPresent;
        private int loadFailed;
        private int errors;
        private int alreadyLoaded;
        private int loadedByDiagnostic;

        private ScanSession(CommandSender sender, Level level, LevelProvider provider) {
            this.sender = sender;
            this.level = level;
            this.provider = provider;
            report.add("ChunkDiag 1.1.0 - FULL BOUNDED SCAN");
            report.add("Area: chunk X [" + MIN_CHUNK_X + ".." + MAX_CHUNK_X + "], Z [" +
                    MIN_CHUNK_Z + ".." + MAX_CHUNK_Z + "]");
            report.add("Loads at most one existing chunk every 2 ticks using LevelProvider.loadChunk(x,z,false).");
            report.add("No new chunk generation, block edits, light flag edits, or LevelDB key edits.");
        }
    }
}
