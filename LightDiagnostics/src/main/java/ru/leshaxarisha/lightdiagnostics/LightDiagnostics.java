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
import org.powernukkitx.nbt.tag.CompoundTag;
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
    private static final int MAX_EVENTS = 5000;
    private static final int REASON_CHECK_DELAY_TICKS = 1;

    private final ArrayDeque<String> events = new ArrayDeque<>();
    private boolean tracking;
    private long creatureEventCount;
    private long entityEventCount;
    private long cancelledCreatureEventCount;
    private long cancelledEntityEventCount;
    private long naturalReasonCount;
    private long otherReasonCount;
    private long missingReasonCount;
    private long reasonCheckErrorCount;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("LightDiagnostics 0.3.0 enabled (PowerNukkitX 3.0.6)");
        getLogger().info("Read-only diagnostics. Natural spawn reason is checked one tick after EntitySpawnEvent.");
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
            sender.sendMessage("§e/lightdiag stats");
            sender.sendMessage("§e/lightdiag export");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "probe" -> probe(sender, args);
            case "track" -> track(sender, args);
            case "stats" -> stats(sender);
            case "export" -> export(sender);
            default -> sender.sendMessage("§eИспользование: /lightdiag <probe|track|stats|export|help>");
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
            if (y < level.getDimensionData().getMinHeight() || y >= level.getDimensionData().getMaxHeight()) {
                sender.sendMessage("§cY вне диапазона мира: " + level.getDimensionData().getMinHeight()
                        + ".." + (level.getDimensionData().getMaxHeight() - 1));
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

            Block block = level.getBlock(x, y, z);
            int blockLight = chunk.getBlockLight(Math.floorMod(x, 16), y, Math.floorMod(z, 16));
            int skyLight = chunk.getBlockSkyLight(Math.floorMod(x, 16), y, Math.floorMod(z, 16));
            sender.sendMessage("§6--- LightDiagnostics 0.3.0 probe ---");
            sender.sendMessage("§7Мир: §f" + level.getFolderName() + " §7Координаты: §f" + x + " " + y + " " + z);
            sender.sendMessage("§7Блок: §f" + block.getId());
            sender.sendMessage("§7Block light: §f" + blockLight + "/15");
            sender.sendMessage("§7Sky light: §f" + skyLight + "/15");
            sender.sendMessage("§7Излучение блока: §f" + block.getLightLevel() + "/15");
            sender.sendMessage("§7Light populated: §f" + chunk.isLightPopulated());
            sender.sendMessage("§7Chunk state: §f" + chunk.getChunkState());
            sender.sendMessage("§7Чанк не загружается и не генерируется.");
            getLogger().info("PROBE | world=" + level.getFolderName() + " | xyz=" + x + "," + y + "," + z
                    + " | block=" + block.getId() + " | blockLight=" + blockLight
                    + " | skyLight=" + skyLight + " | emission=" + block.getLightLevel()
                    + " | lightPopulated=" + chunk.isLightPopulated() + " | chunkState=" + chunk.getChunkState());
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cКоординаты должны быть целыми числами.");
        } catch (Throwable ex) {
            sender.sendMessage("§cОшибка проверки: " + ex.getClass().getSimpleName() + ": " + safeMessage(ex));
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
                creatureEventCount = 0;
                entityEventCount = 0;
                cancelledCreatureEventCount = 0;
                cancelledEntityEventCount = 0;
                naturalReasonCount = 0;
                otherReasonCount = 0;
                missingReasonCount = 0;
                reasonCheckErrorCount = 0;
                tracking = true;
                sender.sendMessage("§aОтслеживание включено. Буфер и счётчики очищены.");
                getLogger().info("Spawn tracking enabled; counters and event buffer reset.");
            }
            case "off" -> {
                tracking = false;
                sender.sendMessage("§eОтслеживание выключено. Записей: " + events.size());
                getLogger().info("Spawn tracking disabled. bufferedEvents=" + events.size());
            }
            case "status" -> stats(sender);
            default -> sender.sendMessage("§eФормат: /lightdiag track <on|off|status>");
        }
    }

    private void stats(CommandSender sender) {
        sender.sendMessage("§6--- LightDiagnostics 0.3.0 statistics ---");
        sender.sendMessage("§7Tracking: §f" + (tracking ? "ON" : "OFF"));
        sender.sendMessage("§7CreatureSpawnEvent: §f" + creatureEventCount
                + " §7(cancelled: §f" + cancelledCreatureEventCount + "§7)");
        sender.sendMessage("§7EntitySpawnEvent creatures: §f" + entityEventCount
                + " §7(cancelled: §f" + cancelledEntityEventCount + "§7)");
        sender.sendMessage("§7NBT SpawnReason=NATURAL: §f" + naturalReasonCount);
        sender.sendMessage("§7NBT other SpawnReason: §f" + otherReasonCount);
        sender.sendMessage("§7NBT SpawnReason missing after 1 tick: §f" + missingReasonCount);
        sender.sendMessage("§7Reason check errors: §f" + reasonCheckErrorCount);
        sender.sendMessage("§7Buffered records: §f" + events.size() + "/" + MAX_EVENTS);
        sender.sendMessage("§7Классификация NBT выполняется через 1 тик после EntitySpawnEvent.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!tracking) return;
        creatureEventCount++;
        if (event.isCancelled()) cancelledCreatureEventCount++;
        try {
            var pos = event.getPosition();
            String line = sampleSpawn("CREATURE_SPAWN", pos.level, pos.getFloorX(), pos.getFloorY(), pos.getFloorZ(),
                    "reason=" + event.getReason()
                            + " | networkId=" + event.getEntityNetworkId()
                            + " | cancelled=" + event.isCancelled());
            record(line);
        } catch (Throwable ex) {
            record("CREATURE_SPAWN | sampleError=" + ex.getClass().getSimpleName() + ":" + safeMessage(ex));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (!tracking || !event.isCreature()) return;
        entityEventCount++;
        if (event.isCancelled()) cancelledEntityEventCount++;

        try {
            Entity entity = event.getEntity();
            var pos = entity.getPosition();
            Level level = pos.level;
            int x = pos.getFloorX();
            int y = pos.getFloorY();
            int z = pos.getFloorZ();
            String entityInfo = "entityClass=" + entity.getClass().getSimpleName()
                    + " | networkId=" + entity.getNetworkId()
                    + " | cancelled=" + event.isCancelled()
                    + " | source=AWAITING_NBT_REASON";
            record(sampleSpawn("ENTITY_SPAWN", level, x, y, z, entityInfo));

            // PowerNukkitX 3.0.6 assigns SpawnReason=NATURAL immediately after
            // entity construction, while EntitySpawnEvent is fired inside construction.
            // One main-thread tick later the caller has completed that assignment.
            getServer().getScheduler().scheduleDelayedTask(this, () -> classifySpawnReason(entity, level, x, y, z), REASON_CHECK_DELAY_TICKS);
        } catch (Throwable ex) {
            record("ENTITY_SPAWN | schedulingError=" + ex.getClass().getSimpleName() + ":" + safeMessage(ex));
        }
    }

    private void classifySpawnReason(Entity entity, Level level, int x, int y, int z) {
        if (!tracking) return;
        try {
            CompoundTag nbt = entity.getNbt();
            String reason = nbt != null && nbt.contains("SpawnReason")
                    ? nbt.getString("SpawnReason")
                    : "";
            if (reason.equalsIgnoreCase("NATURAL")) {
                naturalReasonCount++;
            } else if (!reason.isEmpty()) {
                otherReasonCount++;
            } else {
                missingReasonCount++;
                reason = "MISSING";
            }

            record("SPAWN_CLASSIFICATION | time=" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    + " | world=" + (level == null ? "null" : level.getFolderName())
                    + " | xyz=" + x + "," + y + "," + z
                    + " | entityClass=" + entity.getClass().getSimpleName()
                    + " | networkId=" + entity.getNetworkId()
                    + " | nbtSpawnReason=" + reason
                    + " | entityClosed=" + entity.closed);
        } catch (Throwable ex) {
            reasonCheckErrorCount++;
            record("SPAWN_CLASSIFICATION | xyz=" + x + "," + y + "," + z
                    + " | result=ERROR | error=" + ex.getClass().getSimpleName() + ":" + safeMessage(ex));
        }
    }

    private String sampleSpawn(String eventName, Level level, int x, int y, int z, String extra) {
        StringBuilder line = new StringBuilder(eventName)
                .append(" | time=").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .append(" | world=").append(level == null ? "null" : level.getFolderName())
                .append(" | xyz=").append(x).append(',').append(y).append(',').append(z)
                .append(" | ").append(extra);

        appendBlockSample(line, "feet", level, x, y, z);
        appendBlockSample(line, "below", level, x, y - 1, z);
        appendBlockSample(line, "head", level, x, y + 1, z);
        return line.toString();
    }

    private void appendBlockSample(StringBuilder line, String label, Level level, int x, int y, int z) {
        line.append(" | ").append(label).append("=");
        if (level == null || y < level.getDimensionData().getMinHeight()
                || y >= level.getDimensionData().getMaxHeight()) {
            line.append("out_of_range");
            return;
        }

        int chunkX = Math.floorDiv(x, 16);
        int chunkZ = Math.floorDiv(z, 16);
        if (!level.isChunkLoaded(chunkX, chunkZ)) {
            line.append("unloaded");
            return;
        }

        IChunk chunk = level.getChunk(chunkX, chunkZ, false);
        if (chunk == null) {
            line.append("unavailable");
            return;
        }

        try {
            Block block = level.getBlock(x, y, z);
            int localX = Math.floorMod(x, 16);
            int localZ = Math.floorMod(z, 16);
            line.append(block.getId())
                    .append(",blockLight=").append(chunk.getBlockLight(localX, y, localZ))
                    .append(",skyLight=").append(chunk.getBlockSkyLight(localX, y, localZ))
                    .append(",emission=").append(block.getLightLevel())
                    .append(",lightPopulated=").append(chunk.isLightPopulated())
                    .append(",chunkState=").append(chunk.getChunkState());
        } catch (Throwable ex) {
            line.append("sample_error:").append(ex.getClass().getSimpleName());
        }
    }

    private void record(String line) {
        if (events.size() >= MAX_EVENTS) events.removeFirst();
        events.addLast(line);
        getLogger().info("SPAWN DIAG | " + line);
    }

    private String safeMessage(Throwable ex) {
        return ex.getMessage() == null ? "no_message" : ex.getMessage().replace('|', '/');
    }

    private void export(CommandSender sender) {
        try {
            Path folder = getDataFolder().toPath();
            Files.createDirectories(folder);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path file = folder.resolve("light-diagnostics-0.3.0-" + stamp + ".log");
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write("LightDiagnostics 0.3.0");
                writer.newLine();
                writer.write("Exported: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                writer.newLine();
                writer.write("Tracking currently: " + tracking);
                writer.newLine();
                writer.write("CreatureSpawnEvent count: " + creatureEventCount);
                writer.newLine();
                writer.write("CreatureSpawnEvent cancelled: " + cancelledCreatureEventCount);
                writer.newLine();
                writer.write("EntitySpawnEvent creature count: " + entityEventCount);
                writer.newLine();
                writer.write("EntitySpawnEvent cancelled: " + cancelledEntityEventCount);
                writer.newLine();
                writer.write("NBT SpawnReason NATURAL: " + naturalReasonCount);
                writer.newLine();
                writer.write("NBT other SpawnReason: " + otherReasonCount);
                writer.newLine();
                writer.write("NBT SpawnReason missing after 1 tick: " + missingReasonCount);
                writer.newLine();
                writer.write("SpawnReason check errors: " + reasonCheckErrorCount);
                writer.newLine();
                writer.write("Buffered events: " + events.size() + "/" + MAX_EVENTS);
                writer.newLine();
                writer.write("NOTE: PowerNukkitX 3.0.6 natural chunk spawning sets NBT SpawnReason=NATURAL after EntitySpawnEvent.");
                writer.newLine();
                writer.write("NOTE: This plugin is read-only and samples already-loaded chunks only.");
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
            getLogger().error("Could not export report", ex);
        }
    }
}
