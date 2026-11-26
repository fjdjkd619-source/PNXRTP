package ru.leshaxarisha.lightdiagnostics;

import org.powernukkitx.block.Block;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.entity.Entity;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.EventPriority;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.entity.CreatureSpawnEvent;
import org.powernukkitx.event.entity.EntitySpawnEvent;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.format.IChunk;
import org.powernukkitx.plugin.PluginBase;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Locale;

public final class LightDiagnostics extends PluginBase implements Listener {
    private static final int MAX_EVENTS = 2000;
    private final ArrayDeque<String> events = new ArrayDeque<>();
    private boolean tracking;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("LightDiagnostics 0.1.0 enabled (PowerNukkitX API 3.0.6)");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!label.equalsIgnoreCase("lightdiag")) return false;
        if (!sender.hasPermission("lightdiagnostics.admin")) {
            sender.sendMessage("§cНедостаточно прав. Требуется lightdiagnostics.admin.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("§e/lightdiag probe <world> <x> <y> <z>");
            sender.sendMessage("§e/lightdiag track <on|off|status>");
            sender.sendMessage("§e/lightdiag export");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "probe" -> probe(sender, args);
            case "track" -> track(sender, args);
            case "export" -> export(sender);
            default -> sender.sendMessage("§eИспользование: /lightdiag <probe|track|export|help>");
        }
        return true;
    }

    private void probe(CommandSender sender, String[] args) {
        if (args.length != 5) {
            sender.sendMessage("§eФормат: /lightdiag probe <world> <x> <y> <z>");
            return;
        }
        Level level = getServer().getLevelByName(args[1]);
        if (level == null) {
            for (Level candidate : getServer().getLevels().values()) {
                if (candidate.getFolderName().equalsIgnoreCase(args[1])) {
                    level = candidate;
                    break;
                }
            }
        }
        if (level == null) {
            sender.sendMessage("§cМир не найден или не загружен: " + args[1]);
            return;
        }
        try {
            int x = Integer.parseInt(args[2]);
            int y = Integer.parseInt(args[3]);
            int z = Integer.parseInt(args[4]);
            if (y < level.getMinHeight() || y >= level.getMaxHeight()) {
                sender.sendMessage("§cY вне диапазона мира: " + level.getMinHeight() + ".." + (level.getMaxHeight() - 1));
                return;
            }
            int cx = Math.floorDiv(x, 16);
            int cz = Math.floorDiv(z, 16);
            if (!level.isChunkLoaded(cx, cz)) {
                sender.sendMessage("§cЧанк не загружен. Команда не будет загружать или генерировать его.");
                return;
            }
            IChunk chunk = level.getChunk(cx, cz, false);
            if (chunk == null) {
                sender.sendMessage("§cНе удалось получить уже загруженный чанк.");
                return;
            }
            int lx = Math.floorMod(x, 16);
            int lz = Math.floorMod(z, 16);
            int blockLight = chunk.getBlockLight(lx, y, lz);
            int skyLight = chunk.getBlockSkyLight(lx, y, lz);
            Block block = level.getBlock(x, y, z);
            sender.sendMessage("§6--- LightDiagnostics probe ---");
            sender.sendMessage("§7Мир: §f" + level.getFolderName() + " §7Координаты: §f" + x + " " + y + " " + z);
            sender.sendMessage("§7Блок: §f" + block.getId());
            sender.sendMessage("§7Block light (сохранённый уровень): §f" + blockLight + "/15");
            sender.sendMessage("§7Sky light: §f" + skyLight + "/15");
            sender.sendMessage("§7Излучение самого блока: §f" + block.getLightLevel() + "/15");
            sender.sendMessage("§7Chunk light populated: §f" + chunk.isLightPopulated());
            sender.sendMessage("§7Chunk finalization: §f" + chunk.getFinalizationState());
            sender.sendMessage("§7Важно: это чтение данных сервера, а не яркости картинки клиента.");
            getLogger().info("PROBE | world=" + level.getFolderName() + " | xyz=" + x + "," + y + "," + z
                    + " | block=" + block.getId() + " | blockLight=" + blockLight
                    + " | skyLight=" + skyLight + " | emission=" + block.getLightLevel()
                    + " | lightPopulated=" + chunk.isLightPopulated()
                    + " | finalization=" + chunk.getFinalizationState());
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cКоординаты должны быть целыми числами.");
        } catch (Throwable ex) {
            sender.sendMessage("§cОшибка проверки: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            getLogger().error("LightDiagnostics probe failed", ex);
        }
    }

    private void track(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§eФормат: /lightdiag track <on|off|status>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on" -> {
                events.clear();
                tracking = true;
                sender.sendMessage("§aОтслеживание спавна включено. События будут записываться в лог.");
                getLogger().info("Spawn tracking enabled.");
            }
            case "off" -> {
                tracking = false;
                sender.sendMessage("§eОтслеживание спавна выключено. Событий в буфере: " + events.size());
                getLogger().info("Spawn tracking disabled. bufferedEvents=" + events.size());
            }
            case "status" -> sender.sendMessage("§eSpawn tracking: " + (tracking ? "ON" : "OFF")
                    + "§e, events: " + events.size() + "/" + MAX_EVENTS);
            default -> sender.sendMessage("§eФормат: /lightdiag track <on|off|status>");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!tracking) return;
        try {
            var pos = event.getPosition();
            Level level = pos.level;
            int x = pos.getFloorX();
            int y = pos.getFloorY();
            int z = pos.getFloorZ();
            String line = sampleSpawn("CREATURE_SPAWN", level, x, y, z,
                    "reason=" + event.getReason() + " | networkId=" + event.getEntityNetworkId()
                            + " | cancelled=" + event.isCancelled());
            record(line);
        } catch (Throwable ex) {
            record("CREATURE_SPAWN | sampleError=" + ex.getClass().getSimpleName() + ":" + ex.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (!tracking || !event.isCreature()) return;
        try {
            Entity entity = event.getEntity();
            var pos = entity.getPosition();
            record(sampleSpawn("ENTITY_SPAWN", pos.level, pos.getFloorX(), pos.getFloorY(), pos.getFloorZ(),
                    "type=" + entity.getClass().getSimpleName() + " | networkId=" + entity.getNetworkId()
                           ));
        } catch (Throwable ex) {
            record("ENTITY_SPAWN | sampleError=" + ex.getClass().getSimpleName() + ":" + ex.getMessage());
        }
    }

    private String sampleSpawn(String type, Level level, int x, int y, int z, String extra) {
        int blockLight = -1;
        int skyLight = -1;
        String blockId = "unknown";
        String chunkState = "unavailable";
        if (level != null && y >= level.getMinHeight() && y < level.getMaxHeight()) {
            int cx = Math.floorDiv(x, 16);
            int cz = Math.floorDiv(z, 16);
            if (level.isChunkLoaded(cx, cz)) {
                IChunk chunk = level.getChunk(cx, cz, false);
                if (chunk != null) {
                    blockLight = chunk.getBlockLight(Math.floorMod(x, 16), y, Math.floorMod(z, 16));
                    skyLight = chunk.getBlockSkyLight(Math.floorMod(x, 16), y, Math.floorMod(z, 16));
                    chunkState = String.valueOf(chunk.getFinalizationState());
                    blockId = level.getBlock(x, y, z).getId();
                }
            }
        }
        return type + " | world=" + (level == null ? "null" : level.getFolderName())
                + " | xyz=" + x + "," + y + "," + z + " | block=" + blockId
                + " | blockLight=" + blockLight + " | skyLight=" + skyLight
                + " | chunkState=" + chunkState + " | " + extra;
    }

    private void record(String line) {
        if (events.size() >= MAX_EVENTS) events.removeFirst();
        events.addLast(line);
        getLogger().info("SPAWN DIAG | " + line);
    }

    private void export(CommandSender sender) {
        try {
            Path folder = getDataFolder().toPath();
            Files.createDirectories(folder);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path file = folder.resolve("light-diagnostics-" + stamp + ".log");
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write("LightDiagnostics 0.1.0");
                writer.newLine();
                writer.write("Exported: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                writer.newLine();
                writer.write("Tracking currently: " + tracking);
                writer.newLine();
                writer.write("Buffered events: " + events.size());
                writer.newLine();
                for (String event : events) {
                    writer.write(event);
                    writer.newLine();
                }
            }
            sender.sendMessage("§aОтчёт сохранён: §f" + file.toAbsolutePath());
            getLogger().info("Exported diagnostic report: " + file.toAbsolutePath());
        } catch (IOException ex) {
            sender.sendMessage("§cНе удалось сохранить отчёт: " + ex.getMessage());
            getLogger().error("Could not export diagnostic report", ex);
        }
    }
}
