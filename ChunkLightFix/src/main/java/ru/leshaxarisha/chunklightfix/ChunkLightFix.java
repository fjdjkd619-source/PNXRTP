package ru.leshaxarisha.chunklightfix;

import org.powernukkitx.Player;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.format.ChunkState;
import org.powernukkitx.level.format.IChunk;
import org.powernukkitx.level.format.LevelProvider;
import org.powernukkitx.plugin.PluginBase;
import org.powernukkitx.scheduler.TaskHandler;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Resumes only the final light-population and finished stages for chunks whose
 * state proves that block population already completed. It never runs terrain,
 * populators, or feature generation.
 *
 * The first command is deliberately limited to the known spawn chunk (-2, 2).
 * The bounded bulk pass skips legacy-state chunks and every state except POPULATED.
 */
public final class ChunkLightFix extends PluginBase {
    private static final int MIN_CHUNK_X = -12;
    private static final int MAX_CHUNK_X = 8;
    private static final int MIN_CHUNK_Z = -8;
    private static final int MAX_CHUNK_Z = 12;
    private static final int TEST_CHUNK_X = -2;
    private static final int TEST_CHUNK_Z = 2;

    private TaskHandler task;
    private Session session;
    private String lastStatus = "Проверка ещё не запускалась.";

    @Override
    public void onEnable() {
        getLogger().info("ChunkLightFix 0.1.0 enabled. No automatic world edits.");
    }

    @Override
    public void onDisable() {
        stopSession(false);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!label.equalsIgnoreCase("chunklightfix")) return false;
        if (!sender.hasPermission("chunklightfix.use")) {
            sender.sendMessage("§cНедостаточно прав: chunklightfix.use");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманду нужно выполнить в игре от имени игрока.");
            return true;
        }
        Level level = player.getLevel();
        if (level == null) {
            sender.sendMessage("§cМир игрока не найден.");
            return true;
        }
        if (!"world".equalsIgnoreCase(level.getName())) {
            sender.sendMessage("§cChunkLightFix разрешён только в основном мире world.");
            return true;
        }

        String mode = args.length == 0 ? "status" : args[0].toLowerCase();
        switch (mode) {
            case "test" -> runSingleTest(sender, level);
            case "start" -> startBulk(sender, level);
            case "status" -> sender.sendMessage("§eChunkLightFix: " + lastStatus +
                    (session == null ? "" : " Прогресс " + session.index + "/441."));
            case "stop" -> {
                stopSession(true);
                sender.sendMessage("§eChunkLightFix: остановлено.");
            }
            default -> sender.sendMessage("§eИспользование: /chunklightfix <test|start|status|stop>");
        }
        return true;
    }

    private void runSingleTest(CommandSender sender, Level level) {
        if (session != null) {
            sender.sendMessage("§cСначала останови текущую операцию: /chunklightfix stop");
            return;
        }
        sender.sendMessage("§eПроверяю только тестовый чанк (-2, 2). Другие чанки не затрагиваются.");
        ProcessResult result;
        try {
            result = processChunk(level, TEST_CHUNK_X, TEST_CHUNK_Z);
        } catch (Throwable error) {
            getLogger().error("ChunkLightFix test failed", error);
            result = ProcessResult.of(Kind.ERROR, false,
                    "ошибка " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
        lastStatus = "Тестовый чанк (-2,2): " + result.message;
        sender.sendMessage((result.repaired ? "§a" : "§e") + "ChunkLightFix: " + result.message);
        if (result.repaired) {
            sender.sendMessage("§aСохранено. Проверь /chunkdiag here, стоя в чанке (-2,2).");
            sender.sendMessage("§eМассовое исправление пока не запускалось.");
        }
    }

    private void startBulk(CommandSender sender, Level level) {
        if (session != null) {
            sender.sendMessage("§eОперация уже выполняется. /chunklightfix status");
            return;
        }
        session = new Session(sender, level, level.getProvider());
        sender.sendMessage("§aChunkLightFix: начат ограниченный проход по 441 координате.");
        sender.sendMessage("§eОбрабатываются только существующие чанки POPULATED без legacy states и без light flag.");
        sender.sendMessage("§eОдин чанк каждые 20 тиков. Новые чанки не создаются; генерация блоков не запускается.");
        task = getServer().getScheduler().scheduleRepeatingTask(this, this::scanStep, 20);
    }

    private void scanStep() {
        Session current = session;
        if (current == null) {
            stopSession(false);
            return;
        }
        if (current.index >= 441) {
            finish(current);
            return;
        }

        int x = MIN_CHUNK_X + (current.index % 21);
        int z = MIN_CHUNK_Z + (current.index / 21);
        current.index++;

        try {
            ProcessResult result = processChunk(current.level, x, z);
            switch (result.kind) {
                case REPAIRED -> current.repaired++;
                case ALREADY_OK -> current.alreadyOk++;
                case LEGACY -> current.legacySkipped++;
                case WRONG_STATE -> current.stateSkipped++;
                case MISSING -> current.missing++;
                case ERROR -> current.errors++;
            }
            current.report.add("chunk=" + x + "," + z + " result=" + result.kind + " detail=" + result.message);
        } catch (Throwable error) {
            current.errors++;
            current.report.add("chunk=" + x + "," + z + " ERROR=" + error.getClass().getSimpleName() + ":" + error.getMessage());
            getLogger().error("ChunkLightFix failed at " + x + "," + z, error);
        }

        if (current.index % 35 == 0) {
            current.sender.sendMessage("§7ChunkLightFix: " + current.index + "/441; исправлено=" +
                    current.repaired + "; legacy пропущено=" + current.legacySkipped +
                    "; ошибок=" + current.errors + ".");
        }
        if (current.index >= 441) finish(current);
    }

    private ProcessResult processChunk(Level level, int x, int z) {
        LevelProvider provider = level.getProvider();
        boolean wasLoaded = provider.getLoadedChunk(x, z) != null;

        if (!wasLoaded && !level.loadChunk(x, z, false)) {
            return ProcessResult.of(Kind.MISSING, false, "нет существующего сохранённого чанка");
        }

        IChunk chunk = provider.getLoadedChunk(x, z);
        if (chunk == null) {
            releaseIfNew(level, x, z, wasLoaded);
            return ProcessResult.of(Kind.MISSING, false, "после загрузки чанк не найден");
        }

        try {
            if (chunk.isLightPopulated()) {
                return ProcessResult.of(Kind.ALREADY_OK, false, "освещение уже помечено готовым");
            }
            if (!chunk.isGenerated() || chunk.getChunkState() != ChunkState.POPULATED) {
                return ProcessResult.of(Kind.WRONG_STATE, false,
                        "состояние " + chunk.getChunkState() + ", требуется POPULATED");
            }
            if (chunk.hasLegacyStates()) {
                return ProcessResult.of(Kind.LEGACY, false, "legacy states — пропущен для отдельной проверки");
            }

            // Exact PNX 3.0.6 LightPopulationStage operations:
            // recalculateHeightMap -> populateSkyLight -> setLightPopulated.
            // Then complete the immediately following FinishedStage state transition.
            chunk.recalculateHeightMap();
            chunk.populateSkyLight();
            chunk.setLightPopulated();
            chunk.setChunkState(ChunkState.FINISHED);

            // FinishedStage may clear the dirty counter when saveGenerated=false.
            // Mark dirty after the state transition and explicitly persist the chunk.
            chunk.setChanged();
            provider.saveChunk(x, z);

            if (!chunk.isLightPopulated() || chunk.getChunkState() != ChunkState.FINISHED) {
                return ProcessResult.of(Kind.ERROR, false, "проверка после записи не пройдена");
            }
            return ProcessResult.of(Kind.REPAIRED, true, "исправлен и сохранён; lightPopulated=true, state=FINISHED");
        } finally {
            releaseIfNew(level, x, z, wasLoaded);
        }
    }

    private void releaseIfNew(Level level, int x, int z, boolean wasLoaded) {
        if (!wasLoaded) {
            // Safe unload only. Spawn/ticking-area/player-used chunks may remain loaded.
            level.unloadChunk(x, z, true, false);
        }
    }

    private void finish(Session completed) {
        if (session != completed) return;
        completed.report.add("");
        completed.report.add("SUMMARY checked=" + completed.index +
                " repaired=" + completed.repaired +
                " alreadyOk=" + completed.alreadyOk +
                " legacySkipped=" + completed.legacySkipped +
                " stateSkipped=" + completed.stateSkipped +
                " missing=" + completed.missing +
                " errors=" + completed.errors);
        boolean saved = writeReport(completed.report);
        lastStatus = "завершено: исправлено=" + completed.repaired +
                ", legacy пропущено=" + completed.legacySkipped +
                ", ошибок=" + completed.errors +
                (saved ? "; отчёт сохранён" : "; отчёт не сохранён");
        completed.sender.sendMessage("§aChunkLightFix завершён: исправлено=" + completed.repaired +
                ", уже готово=" + completed.alreadyOk +
                ", legacy пропущено=" + completed.legacySkipped +
                ", другое состояние пропущено=" + completed.stateSkipped +
                ", отсутствует=" + completed.missing +
                ", ошибок=" + completed.errors + ".");
        if (saved) completed.sender.sendMessage("§aОтчёт: plugins/ChunkLightFix/repair-report.txt");
        stopSession(false);
    }

    private boolean writeReport(List<String> lines) {
        File folder = getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) return false;
        try {
            Files.write(new File(folder, "repair-report.txt").toPath(), lines, StandardCharsets.UTF_8);
            return true;
        } catch (IOException error) {
            getLogger().error("Could not write repair report", error);
            return false;
        }
    }

    private void stopSession(boolean notify) {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (session != null && notify) {
            session.sender.sendMessage("§eОстановлено на " + session.index + "/441.");
        }
        session = null;
    }

    private enum Kind { REPAIRED, ALREADY_OK, LEGACY, WRONG_STATE, MISSING, ERROR }

    private record ProcessResult(Kind kind, boolean repaired, String message) {
        static ProcessResult of(Kind kind, boolean repaired, String message) {
            return new ProcessResult(kind, repaired, message);
        }
    }

    private static final class Session {
        final CommandSender sender;
        final Level level;
        final LevelProvider provider;
        final List<String> report = new ArrayList<>();
        int index;
        int repaired;
        int alreadyOk;
        int legacySkipped;
        int stateSkipped;
        int missing;
        int errors;

        Session(CommandSender sender, Level level, LevelProvider provider) {
            this.sender = sender;
            this.level = level;
            this.provider = provider;
            report.add("ChunkLightFix 0.1.0 - bounded repair");
            report.add("Area: chunk X [-12..8], Z [-8..12]");
            report.add("Only existing POPULATED chunks with legacyStates=false and lightPopulated=false are processed.");
            report.add("No terrain, populators, features, or block generation are rerun.");
        }
    }
}