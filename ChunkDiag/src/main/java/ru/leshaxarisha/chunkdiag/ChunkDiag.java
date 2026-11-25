package ru.leshaxarisha.chunkdiag;

import org.powernukkitx.Player;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.level.Level;
import org.powernukkitx.plugin.PluginBase;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only diagnostics. It only queries chunks that are already loaded;
 * it never requests chunk loading, changes chunk flags, or writes world data.
 */
public final class ChunkDiag extends PluginBase {
    private static final int MIN_CHUNK_X = -12;
    private static final int MAX_CHUNK_X = 8;
    private static final int MIN_CHUNK_Z = -8;
    private static final int MAX_CHUNK_Z = 12;

    @Override
    public void onEnable() {
        getLogger().info("ChunkDiag 1.0.0 enabled (read-only; loaded chunks only).");
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
            int cx = ((int) Math.floor(player.getX())) >> 4;
            int cz = ((int) Math.floor(player.getZ())) >> 4;
            inspectOne(sender, level, cx, cz);
            return true;
        }
        if (!mode.equals("area")) {
            sender.sendMessage("§eИспользование: /chunkdiag <area|here>");
            return true;
        }

        scanProtectedArea(sender, level);
        return true;
    }

    private void inspectOne(CommandSender sender, Level level, int x, int z) {
        Object chunk = getLoadedChunk(level, x, z);
        if (chunk == null) {
            sender.sendMessage("§eЧанк " + x + "," + z + " сейчас не загружен; ничего не загружалось.");
            return;
        }
        String line = describe(x, z, chunk);
        sender.sendMessage("§eChunkDiag: §f" + line);
        getLogger().info("ChunkDiag here: " + line);
    }

    private void scanProtectedArea(CommandSender sender, Level level) {
        int loaded = 0, generated = 0, notGenerated = 0, lightTrue = 0, lightFalse = 0;
        int genUnknown = 0, lightUnknown = 0, errors = 0;
        List<String> report = new ArrayList<>();
        report.add("ChunkDiag 1.0.0 - READ ONLY");
        report.add("Area: chunk X [" + MIN_CHUNK_X + ".." + MAX_CHUNK_X + "], Z [" +
                MIN_CHUNK_Z + ".." + MAX_CHUNK_Z + "]");
        report.add("Only already-loaded chunks are inspected. No chunk is loaded or modified.");
        for (int z = MIN_CHUNK_Z; z <= MAX_CHUNK_Z; z++) {
            for (int x = MIN_CHUNK_X; x <= MAX_CHUNK_X; x++) {
                Object chunk = getLoadedChunk(level, x, z);
                if (chunk == null) continue;
                loaded++;
                String line = describe(x, z, chunk);
                report.add(line);
                String g = invoke(chunk, "isGenerated");
                String l = invoke(chunk, "isLightPopulated");
                if ("true".equals(g)) generated++;
                else if ("false".equals(g)) notGenerated++;
                else genUnknown++;
                if ("true".equals(l)) lightTrue++;
                else if ("false".equals(l)) lightFalse++;
                else lightUnknown++;
                if (line.contains("ERROR")) errors++;
            }
        }

        report.add("");
        report.add("SUMMARY loaded=" + loaded + "/441");
        report.add("generated=true:" + generated + ", false:" + notGenerated + ", unknown:" + genUnknown);
        report.add("lightPopulated=true:" + lightTrue + ", false:" + lightFalse + ", unknown:" + lightUnknown);
        report.add("reflectionErrors=" + errors);

        File folder = getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            sender.sendMessage("§cНе удалось создать папку отчёта.");
            return;
        }
        File output = new File(folder, "chunkdiag-report.txt");
        try {
            Files.write(output.toPath(), report, StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLogger().error("ChunkDiag: failed to write report", e);
            sender.sendMessage("§cНе удалось сохранить отчёт. Подробности в консоли.");
            return;
        }

        sender.sendMessage("§aChunkDiag завершён. Загружено чанков: §f" + loaded + "/441");
        sender.sendMessage("§eGenerated: true=" + generated + ", false=" + notGenerated + ", unknown=" + genUnknown);
        sender.sendMessage("§eLightPopulated: true=" + lightTrue + ", false=" + lightFalse + ", unknown=" + lightUnknown);
        sender.sendMessage("§aОтчёт: plugins/ChunkDiag/chunkdiag-report.txt");
        getLogger().info("ChunkDiag summary: loaded=" + loaded + "/441, generated true/false/unknown=" +
                generated + "/" + notGenerated + "/" + genUnknown + ", light true/false/unknown=" +
                lightTrue + "/" + lightFalse + "/" + lightUnknown + ", report=" + output.getAbsolutePath());
    }

    private Object getLoadedChunk(Level level, int x, int z) {
        try {
            Method method = level.getClass().getMethod("getChunkIfLoaded", int.class, int.class);
            return method.invoke(level, x, z);
        } catch (NoSuchMethodException e) {
            getLogger().error("ChunkDiag: Level#getChunkIfLoaded(int,int) is unavailable; refusing to load chunks.");
            return null;
        } catch (ReflectiveOperationException e) {
            getLogger().error("ChunkDiag: cannot inspect loaded chunk " + x + "," + z, e);
            return null;
        }
    }

    private String describe(int x, int z, Object chunk) {
        return "chunk=" + x + "," + z +
                " loaded=" + invoke(chunk, "isLoaded") +
                " generated=" + invoke(chunk, "isGenerated") +
                " lightPopulated=" + invoke(chunk, "isLightPopulated") +
                " state=" + invoke(chunk, "getState");
    }

    private String invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return String.valueOf(value);
        } catch (NoSuchMethodException e) {
            return "NO_METHOD";
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "ERROR";
        }
    }
}
