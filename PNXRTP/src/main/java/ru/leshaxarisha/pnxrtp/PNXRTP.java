package ru.leshaxarisha.pnxrtp;

import org.iq80.leveldb.DBIterator;
import org.powernukkitx.Player;
import org.powernukkitx.block.Block;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.event.player.PlayerTeleportEvent;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.Location;
import org.powernukkitx.level.Position;
import org.powernukkitx.level.format.leveldb.LevelDBProvider;
import org.powernukkitx.level.format.leveldb.LevelDBStorage;
import org.powernukkitx.level.util.LevelDBKeyUtil;
import org.powernukkitx.plugin.PluginBase;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/** PNXRTP for PowerNukkitX 3.0.6 / API 3.0.6. */
public class PNXRTP extends PluginBase {
    private static final int HISTORY_LIMIT = 5;
    private static final int MIN_RTP_DISTANCE = 512;
    private static final int RTP_ATTEMPTS = 40;
    private static final int DEFAULT_AVOID_PLAYERS = 50;
    private static final int DEFAULT_COOLDOWN_SECONDS = 60;
    private static final int DEFAULT_GENERATION_RADIUS = 1000;
    private static final int MAX_GENERATION_RADIUS = 10000;

    private final Map<String, WorldIndex> indexes = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Deque<SavedPoint>>> history = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final AtomicBoolean generatorTaskRunning = new AtomicBoolean(false);

    private File dataFolder;
    private File configFile;
    private File progressFile;
    private File historyFile;
    private File eventFile;
    private final Properties config = new Properties();

    private volatile boolean generating;
    private volatile boolean paused;
    private volatile boolean autoPaused;
    private Level generationLevel;
    private int generationRadius = DEFAULT_GENERATION_RADIUS;
    private int speed = 1;
    private int ring;
    private int side;
    private int offset;
    private long skippedExistingChunks;
    private long requestAttempts;
    private long acceptedRequests;
    private long rejectedRequests;
    private long finishedQueueEntries;
    private long tickCounter;
    private final Set<Long> pendingChunks = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        dataFolder = getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            getLogger().error("PNXRTP: cannot create plugin data folder");
            return;
        }
        configFile = new File(dataFolder, "config.properties");
        progressFile = new File(dataFolder, "rtpgen-progress.txt");
        historyFile = new File(dataFolder, "rtp-history.txt");
        eventFile = new File(dataFolder, "events.log");
        loadConfig();
        loadProgress();
        loadHistory();
        getLogger().info("PNXRTP 1.2.0 enabled for PowerNukkitX API 3.0.6");
        journal("INFO", "Plugin enabled");
        Level defaultLevel = getServer().getDefaultLevel();
        if (defaultLevel != null) buildIndex(defaultLevel);
    }

    @Override
    public void onDisable() {
        if (generating) {
            generating = false;
            getServer().getScheduler().cancelTask(this);
        }
        saveProgress();
        saveHistory();
        savePluginConfig();
        journal("INFO", "Plugin disabled; state saved");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("rtpgen")) {
            if (!sender.hasPermission("pnxrtp.admin")) {
                sender.sendMessage("§cНедостаточно прав. Требуется pnxrtp.admin.");
                return true;
            }
            handleGeneratorCommand(sender, args);
            return true;
        }
        if (!label.equalsIgnoreCase("rtp")) return false;

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("pnxrtp.admin")) {
                sender.sendMessage("§cНедостаточно прав. Требуется pnxrtp.admin.");
                return true;
            }
            loadConfig();
            journal("INFO", "Configuration reloaded");
            sender.sendMessage("§aКонфигурация PNXRTP перезагружена.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("status")) {
            WorldIndex index = sender instanceof Player p ? indexes.get(worldKey(p.getLevel())) : null;
            sender.sendMessage("§e----- PNXRTP -----");
            sender.sendMessage("§eИндекс мира: " + (index == null ? "§cне запущен" : index.building ? "§eстроится" : "§aготов"));
            if (index != null) sender.sendMessage("§eСохранённых чанков в индексе: §f" + index.chunks.size());
            sender.sendMessage("§eГенератор: " + (generating ? paused ? "§eпауза" : "§aработает" : "§cостановлен"));
            sender.sendMessage("§eПамять контейнера/JVM: §f" + memoryPercent() + "%");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭту команду может использовать только игрок.");
            return true;
        }
        performRtp(player);
        return true;
    }

    private void performRtp(Player player) {
        Level level = player.getLevel();
        if (level == null) {
            player.sendMessage("§cМир не найден.");
            return;
        }
        String world = worldKey(level);
        if (!worldEnabled(world, "rtp-enabled", true)) {
            player.sendMessage("§cRTP отключён в этом мире.");
            return;
        }
        long now = System.currentTimeMillis();
        long next = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < next) {
            player.sendMessage("§eПодожди ещё " + ((next - now + 999) / 1000) + " сек. перед следующим RTP.");
            return;
        }
        WorldIndex index = indexes.get(world);
        if (index == null || index.building) {
            buildIndex(level);
            player.sendMessage("§eИндекс сохранённых чанков ещё строится. Попробуй /rtp немного позже.");
            return;
        }
        List<Long> chunks = index.chunks;
        if (chunks.isEmpty()) {
            player.sendMessage("§cВ этом мире пока не найдено сохранённых чанков.");
            return;
        }

        Deque<SavedPoint> previous = history
                .computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(world, k -> new ArrayDeque<>());
        int avoidPlayers = intConfig(world, "avoid-player-distance", DEFAULT_AVOID_PLAYERS, 0, 512);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int attempt = 0; attempt < RTP_ATTEMPTS; attempt++) {
            long packed = chunks.get(random.nextInt(chunks.size()));
            int chunkX = (int) (packed >> 32);
            int chunkZ = (int) packed;
            int x = (chunkX << 4) + random.nextInt(16);
            int z = (chunkZ << 4) + random.nextInt(16);
            synchronized (previous) {
                if (!farEnoughFromHistory(x, z, previous)) continue;
            }
            if (avoidPlayers > 0 && tooCloseToPlayer(player, level, x + 0.5, z + 0.5, avoidPlayers)) continue;

            // create=false is essential: RTP must never generate a new chunk.
            if (!level.loadChunk(chunkX, chunkZ, false)) continue;
            int groundY = level.getHighestBlockAt(x, z) - 1;
            int feetY = groundY + 1;
            if (feetY < level.getMinHeight() || feetY + 1 >= level.getMaxHeight()) continue;
            Block ground = level.getBlock(x, groundY, z);
            Block feet = level.getBlock(x, feetY, z);
            Block head = level.getBlock(x, feetY + 1, z);
            if (!ground.isSolid() || ground.isAir() || !feet.isAir() || !head.isAir()) continue;
            if (isUnsafe(ground) || isUnsafe(feet) || isUnsafe(head)) continue;

            Location destination = new Location(x + 0.5, feetY, z + 0.5, level);
            if (player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                synchronized (previous) {
                    previous.addFirst(new SavedPoint(x + 0.5, z + 0.5));
                    while (previous.size() > HISTORY_LIMIT) previous.removeLast();
                }
                cooldowns.put(player.getUniqueId(), now + cooldownSeconds(world) * 1000L);
                saveHistory();
                player.sendMessage("§aТы телепортирован в случайное безопасное место!");
            } else {
                player.sendMessage("§cТелепортация не удалась.");
            }
            return;
        }
        player.sendMessage("§cНе найдено безопасное место на нужном расстоянии. Возможно, территория мира слишком мала.");
    }

    private boolean farEnoughFromHistory(int x, int z, Deque<SavedPoint> points) {
        long min2 = (long) MIN_RTP_DISTANCE * MIN_RTP_DISTANCE;
        for (SavedPoint point : points) {
            double dx = x + 0.5 - point.x;
            double dz = z + 0.5 - point.z;
            if (dx * dx + dz * dz < min2) return false;
        }
        return true;
    }

    private boolean tooCloseToPlayer(Player self, Level level, double x, double z, int distance) {
        long min2 = (long) distance * distance;
        for (Player other : getServer().getOnlinePlayers().values()) {
            if (other == self || other.getLevel() != level) continue;
            double dx = other.getFloorX() - x;
            double dz = other.getFloorZ() - z;
            if (dx * dx + dz * dz < min2) return true;
        }
        return false;
    }

    private void buildIndex(Level level) {
        String key = worldKey(level);
        WorldIndex existing = indexes.get(key);
        if (existing != null && (existing.building || !existing.chunks.isEmpty())) return;
        WorldIndex index = new WorldIndex();
        index.building = true;
        indexes.put(key, index);
        CompletableFuture.runAsync(() -> {
            List<Long> found = new ArrayList<>();
            DBIterator iterator = null;
            try {
                if (!(level.getProvider() instanceof LevelDBProvider provider)) {
                    throw new IllegalStateException("World provider is not LevelDBProvider");
                }
                LevelDBStorage storage = provider.getStorage();
                byte[] template = LevelDBKeyUtil.VERSION.getKey(0, 0, provider.getDimensionData());
                byte typeByte = template[template.length - 1];
                iterator = storage.getDb().iterator();
                iterator.seekToFirst();
                while (iterator.hasNext()) {
                    Map.Entry<byte[], byte[]> entry = iterator.next();
                    byte[] keyBytes = entry.getKey();
                    if (keyBytes.length != template.length || keyBytes[keyBytes.length - 1] != typeByte) continue;
                    boolean dimensionMatches = true;
                    for (int i = 8; i < keyBytes.length - 1; i++) {
                        if (keyBytes[i] != template[i]) { dimensionMatches = false; break; }
                    }
                    if (!dimensionMatches) continue;
                    ByteBuffer buffer = ByteBuffer.wrap(keyBytes).order(ByteOrder.LITTLE_ENDIAN);
                    int x = buffer.getInt();
                    int z = buffer.getInt();
                    found.add(pack(x, z));
                }
            } catch (Throwable error) {
                getLogger().error("PNXRTP: failed to scan saved chunk index for " + key, error);
            } finally {
                if (iterator != null) {
                    iterator.close();
                }
            }
            found.sort(Long::compare);
            index.chunks = List.copyOf(found);
            index.chunkSet = Set.copyOf(found);
            index.building = false;
            getLogger().info("PNXRTP: indexed " + found.size() + " saved chunk records in world " + key);
        });
    }

    private void handleGeneratorCommand(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendGeneratorStatus(sender);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> startGenerator(sender);
            case "stop" -> {
                if (!generating) { sender.sendMessage("§eГенератор уже остановлен."); return; }
                stopGenerator(false);
                sender.sendMessage("§cГенерация остановлена. Прогресс сохранён.");
            }
            case "pause" -> {
                if (!generating) { sender.sendMessage("§eГенератор не запущен."); return; }
                paused = true;
                autoPaused = false;
                journal("INFO", "Generation manually paused");
                saveProgress();
                sender.sendMessage("§eГенерация приостановлена.");
            }
            case "resume" -> {
                if (!generating) { startGenerator(sender); return; }
                paused = false;
                autoPaused = false;
                journal("INFO", "Generation manually resumed");
                sender.sendMessage("§aГенерация продолжена.");
            }
            case "radius" -> setRadius(sender, args);
            case "speed" -> setSpeed(sender, args);
            case "world" -> selectGeneratorWorld(sender, args);
            case "reset" -> {
                if (generating) stopGenerator(false);
                ring = side = offset = 0;
                requestAttempts = acceptedRequests = rejectedRequests = finishedQueueEntries = skippedExistingChunks = 0;
                pendingChunks.clear();
                saveProgress();
                sender.sendMessage("§aПрогресс генератора сброшен.");
            }
            default -> sender.sendMessage("§eКоманды: /rtpgen start|stop|pause|resume|status|radius <число>|speed <1-4>|world|reset");
        }
    }

    private void startGenerator(CommandSender sender) {
        if (generating) {
            if (paused) { paused = false; autoPaused = false; sender.sendMessage("§aГенерация продолжена."); }
            else sender.sendMessage("§eГенератор уже работает.");
            return;
        }
        generationLevel = resolveGeneratorWorld();
        if (generationLevel == null) { sender.sendMessage("§cВыбранный мир не загружен или не найден. Используй /rtpgen world <имя>."); return; }
        buildIndex(generationLevel);
        generationRadius = intConfig(worldKey(generationLevel), "generator-radius", generationRadius, 100, MAX_GENERATION_RADIUS);
        speed = intConfig(worldKey(generationLevel), "generator-speed", speed, 1, 4);
        if (!worldEnabled(worldKey(generationLevel), "generator-enabled", true)) {
            sender.sendMessage("§cГенератор отключён для этого мира в конфигурации.");
            return;
        }
        int memory = memoryPercent();
        if (memory >= 85) {
            sender.sendMessage("§cПамять уже выше 85%. Сначала снизь нагрузку или перезапусти сервер.");
            return;
        }
        generating = true;
        paused = false;
        autoPaused = false;
        tickCounter = 0;
        journal("INFO", "Generation started: world=" + worldKey(generationLevel) + ", radius=" + generationRadius + ", speed=" + speed);
        if (generatorTaskRunning.compareAndSet(false, true)) {
            getServer().getScheduler().scheduleRepeatingTask(this, this::generatorTick, 20);
        }
        saveProgress();
        sender.sendMessage("§aГенерация запущена. Безопасный режим: " + speed + " запрос(а) на интервал.");
    }

    private void stopGenerator(boolean automatic) {
        generating = false;
        paused = false;
        autoPaused = automatic;
        journal(automatic ? "WARN" : "INFO", automatic ? "Generation stopped automatically" : "Generation stopped manually");
        generatorTaskRunning.set(false);
        getServer().getScheduler().cancelTask(this);
        saveProgress();
    }

    private void generatorTick() {
        if (!generating || generationLevel == null) return;
        int memory = memoryPercent();
        int pauseAt = parseInt(config.getProperty("generator.memory.pause", "75"), 75, 50, 95);
        int stopAt = parseInt(config.getProperty("generator.memory.stop", "85"), 85, pauseAt + 1, 99);
        int resumeAt = parseInt(config.getProperty("generator.memory.resume", "60"), 60, 30, pauseAt - 1);
        if (memory >= stopAt) {
            journal("WARN", "Generation stopped automatically: memory=" + memory + "%");
            getLogger().warning("RTPGen stopped: memory reached " + memory + "%");
            stopGenerator(true);
            return;
        }
        if (memory >= pauseAt && !paused) {
            paused = true;
            autoPaused = true;
            saveProgress();
            journal("WARN", "Generation auto-paused: memory=" + memory + "%");
        } else if (paused && autoPaused && memory <= resumeAt) {
            paused = false;
            autoPaused = false;
            journal("INFO", "Generation auto-resumed: memory=" + memory + "%");
        }
        updateFinishedChunks();
        if (paused) return;
        tickCounter++;
        int interval = memory >= 65 ? Math.max(2, 4 / speed) : Math.max(1, 2 / speed);
        if (tickCounter % interval != 0) return;

        WorldIndex index = indexes.get(worldKey(generationLevel));
        if (index == null || index.building) return;
        int maxRing = (generationRadius + 15) / 16;
        Position spawn = generationLevel.getSpawnLocation();
        int centerX = spawn.getFloorX() >> 4;
        int centerZ = spawn.getFloorZ() >> 4;
        for (int checked = 0; checked < 64; checked++) {
        int oldRing = ring;
        int oldSide = side;
        int oldOffset = offset;
        int dx;
        int dz;
        if (ring == 0) {
            dx = 0; dz = 0; ring = 1;
        } else {
            switch (side) {
                case 0 -> { dx = ring; dz = -ring + offset; }
                case 1 -> { dx = ring - offset; dz = ring; }
                case 2 -> { dx = -ring; dz = ring - offset; }
                default -> { dx = -ring + offset; dz = -ring; }
            }
            offset++;
            if (offset >= ring * 2) {
                offset = 0;
                side++;
                if (side >= 4) { side = 0; ring++; }
            }
        }
        if (ring > maxRing + 1) {
            getLogger().info("RTPGen reached configured radius.");
            stopGenerator(false);
            return;
        }
        int blockX = dx * 16 + 8;
        int blockZ = dz * 16 + 8;
        if ((long) blockX * blockX + (long) blockZ * blockZ > (long) generationRadius * generationRadius) {
            saveProgress();
            continue;
        }
        long packed = pack(centerX + dx, centerZ + dz);
        if (index.chunkSet.contains(packed)) {
            skippedExistingChunks++;
            if (requestAttempts % 25 == 0) saveProgress();
            continue;
        }
        if (generationLevel.isChunkGenerating(centerX + dx, centerZ + dz)) {
            saveProgress();
            continue;
        }
        requestAttempts++;
        generationLevel.generateChunk(centerX + dx, centerZ + dz);
        if (generationLevel.isChunkGenerating(centerX + dx, centerZ + dz)) {
            acceptedRequests++;
            pendingChunks.add(packed);
        } else {
            rejectedRequests++;
            // Restore the exact cursor so a queue-full request is retried, not skipped.
            ring = oldRing;
            side = oldSide;
            offset = oldOffset;
        }
        if (requestAttempts % 25 == 0) saveProgress();
        return;
    }

    }
    private void updateFinishedChunks() {
        if (pendingChunks.isEmpty() || generationLevel == null) return;
        WorldIndex index = indexes.get(worldKey(generationLevel));
        for (Long packed : new ArrayList<>(pendingChunks)) {
            int x = (int) (packed >> 32);
            int z = (int) (long) packed;
            if (!generationLevel.isChunkGenerating(x, z)) {
                pendingChunks.remove(packed);
                finishedQueueEntries++;
                if (index != null) {
                    List<Long> current = index.chunks;
                    if (!current.contains(packed)) {
                        List<Long> updated = new ArrayList<>(current);
                        updated.add(packed);
                        index.chunks = List.copyOf(updated);
                        Set<Long> updatedSet = new java.util.HashSet<>(index.chunkSet);
                        updatedSet.add(packed);
                        index.chunkSet = Set.copyOf(updatedSet);
                    }
                }
            }
        }
    }

    private void selectGeneratorWorld(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Level selected = generationLevel != null ? generationLevel : resolveGeneratorWorld();
            sender.sendMessage("§eМир генератора: §f" + (selected == null ? "не найден" : worldKey(selected)));
            sender.sendMessage("§7Выбор: /rtpgen world <имя мира>");
            return;
        }
        String requested = args[1];
        Level found = null;
        for (Level level : getServer().getLevels().values()) {
            if (level.getFolderName().equalsIgnoreCase(requested)
                    || level.getProvider().getName().equalsIgnoreCase(requested)) {
                found = level;
                break;
            }
        }
        if (found == null) found = getServer().getLevelByName(requested);
        if (found == null) {
            sender.sendMessage("§cМир не найден среди загруженных. Проверь имя мира.");
            return;
        }
        if (generating) stopGenerator(false);
        generationLevel = found;
        generationRadius = intConfig(worldKey(found), "generator-radius", generationRadius, 100, MAX_GENERATION_RADIUS);
        speed = intConfig(worldKey(found), "generator-speed", speed, 1, 4);
        config.setProperty("generator.world", found.getFolderName());
        savePluginConfig();
        saveProgress();
        sender.sendMessage("§aМир генератора выбран: §f" + worldKey(found));
    }

    private Level resolveGeneratorWorld() {
        String configured = config.getProperty("generator.world", "default");
        if (configured.equalsIgnoreCase("default")) return getServer().getDefaultLevel();
        for (Level level : getServer().getLevels().values()) {
            if (level.getFolderName().equalsIgnoreCase(configured)
                    || level.getProvider().getName().equalsIgnoreCase(configured)) return level;
        }
        return getServer().getLevelByName(configured);
    }

    private void setRadius(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("§eИспользование: /rtpgen radius <100-10000>"); return; }
        try {
            int value = Integer.parseInt(args[1]);
            if (value < 100 || value > MAX_GENERATION_RADIUS) throw new NumberFormatException();
            generationRadius = value;
            config.setProperty("generator.radius", String.valueOf(value));
            if (generationLevel != null) config.setProperty("world." + worldKey(generationLevel) + ".generator-radius", String.valueOf(value));
            savePluginConfig();
            saveProgress();
            sender.sendMessage("§aРадиус генерации: " + value + " блоков.");
        } catch (NumberFormatException e) { sender.sendMessage("§cУкажи число от 100 до 10000."); }
    }

    private void setSpeed(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("§eИспользование: /rtpgen speed <1-4>"); return; }
        try {
            int value = Integer.parseInt(args[1]);
            if (value < 1 || value > 4) throw new NumberFormatException();
            speed = value;
            config.setProperty("generator.speed", String.valueOf(value));
            if (generationLevel != null) config.setProperty("world." + worldKey(generationLevel) + ".generator-speed", String.valueOf(value));
            savePluginConfig();
            sender.sendMessage("§aСкорость генератора установлена: " + value + ". Чем выше число, тем чаще запросы.");
        } catch (NumberFormatException e) { sender.sendMessage("§cУкажи число от 1 до 4."); }
    }

    private void sendGeneratorStatus(CommandSender sender) {
        sender.sendMessage("§e----- RTPGen -----");
        sender.sendMessage("§eСтатус: " + (generating ? paused ? autoPaused ? "§6Автопауза" : "§eПауза" : "§aРаботает" : "§cОстановлен"));
        sender.sendMessage("§eМир: §f" + (generationLevel == null ? getServer().getDefaultLevel() == null ? "нет" : worldKey(getServer().getDefaultLevel()) : worldKey(generationLevel)));
        sender.sendMessage("§eРадиус: §f" + generationRadius + " блоков");
        sender.sendMessage("§eСкорость: §f" + speed + " (1-4)");
        sender.sendMessage("§eПропущено существующих чанков: §f" + skippedExistingChunks);
        sender.sendMessage("§eПозиция спирали: §fкольцо " + ring + ", сторона " + side + ", смещение " + offset);
        sender.sendMessage("§eПопыток запросов: §f" + requestAttempts);
        sender.sendMessage("§eПринято очередью: §f" + acceptedRequests);
        sender.sendMessage("§eОтклонено/не подтверждено: §f" + rejectedRequests);
        sender.sendMessage("§eВ обработке по наблюдению: §f" + pendingChunks.size());
        sender.sendMessage("§eВышли из очереди (завершение не гарантирует сохранение): §f" + finishedQueueEntries);
        sender.sendMessage("§eПамять контейнера/JVM: §f" + memoryPercent() + "%");
        sender.sendMessage("§7Порог автопаузы: " + config.getProperty("generator.memory.pause", "75") + "%, автопродолжения: " + config.getProperty("generator.memory.resume", "60") + "%, остановки: " + config.getProperty("generator.memory.stop", "85") + "%");
    }

    private int memoryPercent() {
        String[][] paths = {
                {"/sys/fs/cgroup/memory.current", "/sys/fs/cgroup/memory.max"},
                {"/sys/fs/cgroup/memory/memory.usage_in_bytes", "/sys/fs/cgroup/memory/memory.limit_in_bytes"}
        };
        for (String[] pair : paths) {
            try {
                String usedText = Files.readString(new File(pair[0]).toPath()).trim();
                String maxText = Files.readString(new File(pair[1]).toPath()).trim();
                if (maxText.equals("max")) continue;
                long used = Long.parseLong(usedText);
                long max = Long.parseLong(maxText);
                if (max > 0 && max < (1L << 60)) return (int) Math.min(100, used * 100L / max);
            } catch (Exception ignored) { }
        }
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        return (int) Math.min(100, used * 100L / runtime.maxMemory());
    }

    private void loadConfig() {
        config.clear();
        config.setProperty("generator.radius", String.valueOf(DEFAULT_GENERATION_RADIUS));
        config.setProperty("generator.speed", "1");
        config.setProperty("rtp.cooldown-seconds", String.valueOf(DEFAULT_COOLDOWN_SECONDS));
        config.setProperty("rtp.avoid-player-distance", String.valueOf(DEFAULT_AVOID_PLAYERS));
        config.setProperty("generator.memory.pause", "75");
        config.setProperty("generator.memory.resume", "60");
        config.setProperty("generator.memory.stop", "85");
        if (configFile.exists()) {
            try (var in = Files.newInputStream(configFile.toPath())) { config.load(in); }
            catch (IOException e) { getLogger().warning("Could not load config.properties: " + e.getMessage()); }
        }
        generationRadius = parseInt(config.getProperty("generator.radius"), DEFAULT_GENERATION_RADIUS, 100, MAX_GENERATION_RADIUS);
        speed = parseInt(config.getProperty("generator.speed"), 1, 1, 4);
        savePluginConfig();
    }

    private void savePluginConfig() {
        if (configFile == null) return;
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (var out = new java.io.OutputStreamWriter(bytes, StandardCharsets.UTF_8)) {
                config.store(out, "PNXRTP configuration. Per-world keys: world.<folder>.<setting>");
            }
            atomicWrite(configFile, bytes.toString(StandardCharsets.UTF_8));
        } catch (IOException e) { getLogger().error("Could not save PNXRTP config", e); }
    }

    private boolean worldEnabled(String world, String setting, boolean fallback) {
        return Boolean.parseBoolean(config.getProperty("world." + world + "." + setting, String.valueOf(fallback)));
    }

    private int intConfig(String world, String setting, int fallback, int min, int max) {
        return parseInt(config.getProperty("world." + world + "." + setting,
                config.getProperty("rtp." + setting, String.valueOf(fallback))), fallback, min, max);
    }

    private int cooldownSeconds(String world) {
        return intConfig(world, "cooldown-seconds", DEFAULT_COOLDOWN_SECONDS, 0, 3600);
    }

    private static int parseInt(String value, int fallback, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(value))); }
        catch (Exception ignored) { return fallback; }
    }

    private void loadProgress() {
        if (!progressFile.exists()) return;
        try (BufferedReader in = Files.newBufferedReader(progressFile.toPath(), StandardCharsets.UTF_8)) {
            ring = Integer.parseInt(in.readLine());
            side = Integer.parseInt(in.readLine());
            offset = Integer.parseInt(in.readLine());
            requestAttempts = Long.parseLong(in.readLine());
            acceptedRequests = Long.parseLong(in.readLine());
            rejectedRequests = Long.parseLong(in.readLine());
            String savedWorld = in.readLine();
            if (savedWorld != null && !savedWorld.isBlank()) config.setProperty("generator.world", savedWorld);
            String savedRadius = in.readLine();
            if (savedRadius != null) generationRadius = parseInt(savedRadius, generationRadius, 100, MAX_GENERATION_RADIUS);
            String savedSpeed = in.readLine();
            if (savedSpeed != null) speed = parseInt(savedSpeed, speed, 1, 4);
        } catch (Exception e) {
            ring = side = offset = 0;
            requestAttempts = acceptedRequests = rejectedRequests = 0;
            getLogger().warning("Could not load generator progress; starting from spawn.");
        }
    }

    private void saveProgress() {
        if (progressFile == null) return;
        try {
            String savedWorld = generationLevel == null ? config.getProperty("generator.world", "default") : generationLevel.getFolderName();
            String content = ring + "\n" + side + "\n" + offset + "\n" + requestAttempts + "\n" + acceptedRequests + "\n" + rejectedRequests + "\n" + savedWorld + "\n" + generationRadius + "\n" + speed + "\n";
            atomicWrite(progressFile, content);
        } catch (IOException e) { getLogger().error("Could not save generator progress", e); }
    }

    private void loadHistory() {
        if (!historyFile.exists()) return;
        try (BufferedReader in = Files.newBufferedReader(historyFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = in.readLine()) != null) {
                String[] p = line.split("\\|", 5);
                if (p.length != 5) continue;
                UUID uuid = UUID.fromString(p[0]);
                double x = Double.parseDouble(p[3]);
                double z = Double.parseDouble(p[4]);
                Deque<SavedPoint> points = history.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(p[1], k -> new ArrayDeque<>());
                points.addLast(new SavedPoint(x, z));
                while (points.size() > HISTORY_LIMIT) points.removeFirst();
            }
        } catch (Exception e) { getLogger().warning("Could not load RTP history: " + e.getMessage()); }
    }

    private synchronized void saveHistory() {
        if (historyFile == null) return;
        try {
            StringBuilder content = new StringBuilder();
            for (var playerEntry : history.entrySet()) {
                for (var worldEntry : playerEntry.getValue().entrySet()) {
                    Deque<SavedPoint> points = worldEntry.getValue();
                    synchronized (points) {
                        for (SavedPoint point : points) content.append(playerEntry.getKey()).append('|').append(worldEntry.getKey()).append("|0|").append(point.x).append('|').append(point.z).append('\n');
                    }
                }
            }
            atomicWrite(historyFile, content.toString());
        } catch (IOException e) { getLogger().error("Could not save RTP history", e); }
    }

    private void journal(String level, String message) {
        if (eventFile == null) return;
        String line = java.time.Instant.now() + " [" + level + "] " + message + System.lineSeparator();
        try {
            Files.writeString(eventFile.toPath(), line, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) { getLogger().warning("Could not append PNXRTP event journal: " + e.getMessage()); }
    }

    private static void atomicWrite(File target, String content) throws IOException {
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        Files.writeString(temp.toPath(), content, StandardCharsets.UTF_8);
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String worldKey(Level level) {
        return level == null ? "unknown" : level.getFolderName().replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private boolean isUnsafe(Block block) {
        String id = block.getId().toLowerCase(Locale.ROOT);
        return id.contains("magma_block") || id.contains("cactus") || id.contains("campfire")
                || id.contains("fire") || id.contains("lava") || id.contains("powder_snow")
                || id.contains("sweet_berry_bush") || id.contains("pointed_dripstone")
                || id.contains("wither_rose") || id.contains("berry_bush");
    }

    private static final class WorldIndex {
        volatile boolean building;
        volatile List<Long> chunks = List.of();
        volatile Set<Long> chunkSet = Set.of();
    }

    private static final class SavedPoint {
        final double x;
        final double z;
        SavedPoint(double x, double z) { this.x = x; this.z = z; }
    }
}
