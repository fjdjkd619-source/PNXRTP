package ru.leshaxarisha.housetransfer;

import org.powernukkitx.Player;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.Position;
import org.powernukkitx.level.generator.object.BlockManager;
import org.powernukkitx.level.structure.Structure;
import org.powernukkitx.level.structure.StructureAPI;
import org.powernukkitx.plugin.PluginBase;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class HouseTransfer extends PluginBase {
    private static final long MAX_BLOCKS = 300_000L;
    private static final Pattern SAFE_NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    private final Map<String, Selection> selections = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("HouseTransfer 0.1.1 enabled for PowerNukkitX 3.0.6.");
        getLogger().info("Uses PNX Structure NBT; block entities and their NBT are included. Living/player entities are excluded.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!label.equalsIgnoreCase("copy") && !label.equalsIgnoreCase("housecopy")) return false;
        if (!sender.hasPermission("housetransfer.admin")) {
            sender.sendMessage("§cНедостаточно прав. Требуется housetransfer.admin.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманду нужно выполнять в игре от имени игрока.");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }

        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "pos1" -> setPosition(sender, player, true);
                case "pos2" -> setPosition(sender, player, false);
                case "info" -> showInfo(sender, player);
                case "save" -> save(sender, player, args);
                case "paste" -> paste(sender, player, args);
                case "help" -> help(sender);
                default -> help(sender);
            }
        } catch (Throwable error) {
            sender.sendMessage("§cОшибка HouseTransfer: " + error.getClass().getSimpleName()
                    + (error.getMessage() == null ? "" : " — " + safe(error.getMessage())));
            getLogger().error("HouseTransfer command failed", error);
        }
        return true;
    }

    private void setPosition(CommandSender sender, Player player, boolean first) {
        int x = player.getFloorX();
        int y = player.getFloorY() - 1;
        int z = player.getFloorZ();
        Selection selection = selections.computeIfAbsent(player.getName(), key -> new Selection());
        if (first) {
            selection.x1 = x;
            selection.y1 = y;
            selection.z1 = z;
            selection.level1 = player.getLevel();
            selection.hasPos1 = true;
        } else {
            selection.x2 = x;
            selection.y2 = y;
            selection.z2 = z;
            selection.level2 = player.getLevel();
            selection.hasPos2 = true;
        }
        sender.sendMessage("§aТочка " + (first ? "1" : "2") + " (блок под ногами) установлена: §f"
                + x + " " + y + " " + z + " §7(мир " + player.getLevel().getFolderName() + ")");
    }

    private void showInfo(CommandSender sender, Player player) {
        Selection s = selections.get(player.getName());
        if (s == null || !s.hasPos1 || !s.hasPos2) {
            sender.sendMessage("§eНужно установить обе точки: /copy pos1 и /copy pos2.");
            return;
        }
        if (s.level1 != s.level2) {
            sender.sendMessage("§cТочки находятся в разных мирах.");
            return;
        }
        int minX = Math.min(s.x1, s.x2);
        int minY = Math.min(s.y1, s.y2);
        int minZ = Math.min(s.z1, s.z2);
        int sizeX = Math.abs(s.x2 - s.x1) + 1;
        int sizeY = Math.abs(s.y2 - s.y1) + 1;
        int sizeZ = Math.abs(s.z2 - s.z1) + 1;
        long volume = (long) sizeX * sizeY * sizeZ;
        sender.sendMessage("§6--- HouseTransfer selection ---");
        sender.sendMessage("§7Мир: §f" + s.level1.getFolderName());
        sender.sendMessage("§7Минимум: §f" + minX + " " + minY + " " + minZ);
        sender.sendMessage("§7Размер: §f" + sizeX + " × " + sizeY + " × " + sizeZ);
        sender.sendMessage("§7Объём: §f" + volume + " блоков");
        sender.sendMessage("§7Максимум: §f" + s.x1 + " " + s.y1 + " " + s.z1
                + " → " + s.x2 + " " + s.y2 + " " + s.z2);
    }

    private void save(CommandSender sender, Player player, String[] args) {
        if (args.length != 2 || !SAFE_NAME.matcher(args[1].toLowerCase(Locale.ROOT)).matches()) {
            sender.sendMessage("§eФормат: /copy save <имя_схемы>");
            return;
        }
        Selection s = selections.get(player.getName());
        if (s == null || !s.hasPos1 || !s.hasPos2) {
            sender.sendMessage("§cСначала установи обе точки: /copy pos1 и /copy pos2.");
            return;
        }
        if (s.level1 != s.level2) {
            sender.sendMessage("§cТочки находятся в разных мирах. Сохранять нельзя.");
            return;
        }

        Level level = s.level1;
        int minX = Math.min(s.x1, s.x2);
        int minY = Math.min(s.y1, s.y2);
        int minZ = Math.min(s.z1, s.z2);
        int sizeX = Math.abs(s.x2 - s.x1) + 1;
        int sizeY = Math.abs(s.y2 - s.y1) + 1;
        int sizeZ = Math.abs(s.z2 - s.z1) + 1;
        long volume = (long) sizeX * sizeY * sizeZ;

        if (minY < level.getDimensionData().getMinHeight()
                || minY + sizeY > level.getDimensionData().getMaxHeight()) {
            sender.sendMessage("§cВыделение выходит за вертикальные границы мира.");
            return;
        }
        if (volume <= 0 || volume > MAX_BLOCKS) {
            sender.sendMessage("§cВыделение слишком большое: " + volume
                    + " блоков. Максимум: " + MAX_BLOCKS + ". Чтобы не перегружать сервер.");
            return;
        }
        if (!allChunksLoaded(level, minX, minZ, sizeX, sizeZ)) {
            sender.sendMessage("§cНе все чанки выделения загружены. Ничего не сохранено.");
            sender.sendMessage("§7Подойди/пролети так, чтобы весь дом и его углы были загружены, затем повтори.");
            return;
        }

        String name = "housecopy:" + args[1].toLowerCase(Locale.ROOT);
        if (StructureAPI.exists(name)) {
            sender.sendMessage("§cСхема с таким именем уже существует. Выбери другое имя, чтобы не затереть сохранённую копию.");
            return;
        }
        sender.sendMessage("§eСохраняю область " + sizeX + "×" + sizeY + "×" + sizeZ
                + " (" + volume + " блоков). Не выходи из игры до завершения.");

        // PNX Structure.create(false) captures both block layers and every BlockEntity NBT,
        // including inventories, while intentionally excluding separate world entities.
        Structure structure = Structure.create(level, minX, minY, minZ, sizeX, sizeY, sizeZ, false);
        StructureAPI.save(structure, name);

        if (!StructureAPI.exists(name)) {
            sender.sendMessage("§cФайл структуры не появился. Исходный дом не изменён.");
            return;
        }
        Structure check = StructureAPI.load(name);
        if (check == null || check.getSizeX() != sizeX || check.getSizeY() != sizeY
                || check.getSizeZ() != sizeZ
                || check.getBlockEntities().size() != structure.getBlockEntities().size()) {
            sender.sendMessage("§cПроверка сохранённой схемы не пройдена. Дом не тронут.");
            getLogger().error("Structure verification failed for " + name);
            return;
        }

        sender.sendMessage("§aДом сохранён и проверен: §f" + name);
        sender.sendMessage("§7Размер: §f" + sizeX + "×" + sizeY + "×" + sizeZ
                + " §7Блок-сущностей: §f" + check.getBlockEntities().size());
        sender.sendMessage("§eОригинал не изменён. Старые чанки не удаляются.");
        getLogger().info("Saved and verified structure " + name + " size="
                + sizeX + "x" + sizeY + "x" + sizeZ
                + " blockEntities=" + check.getBlockEntities().size());
    }

    private void paste(CommandSender sender, Player player, String[] args) {
        if (args.length != 2 && args.length != 5) {
            sender.sendMessage("§eФормат: /copy paste <имя> [x y z]");
            return;
        }
        if (!SAFE_NAME.matcher(args[1].toLowerCase(Locale.ROOT)).matches()) {
            sender.sendMessage("§cНедопустимое имя схемы.");
            return;
        }

        int x;
        int y;
        int z;
        try {
            if (args.length == 5) {
                x = Integer.parseInt(args[2]);
                y = Integer.parseInt(args[3]);
                z = Integer.parseInt(args[4]);
            } else {
                x = player.getFloorX();
                y = player.getFloorY() - 1;
                z = player.getFloorZ();
            }
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cКоординаты должны быть целыми числами.");
            return;
        }

        Level level = player.getLevel();
        String name = "housecopy:" + args[1].toLowerCase(Locale.ROOT);
        Structure structure = StructureAPI.load(name);
        if (structure == null) {
            sender.sendMessage("§cСхема не найдена: " + name);
            return;
        }
        if (y < level.getDimensionData().getMinHeight()
                || y + structure.getSizeY() > level.getDimensionData().getMaxHeight()) {
            sender.sendMessage("§cПостройка выходит за вертикальные границы мира.");
            return;
        }
        if (!allChunksLoaded(level, x, z, structure.getSizeX(), structure.getSizeZ())) {
            sender.sendMessage("§cНе все целевые чанки загружены. Ничего не вставлено.");
            sender.sendMessage("§7Сначала подойди к выбранному месту и дождись загрузки всей области.");
            return;
        }

        sender.sendMessage("§eВставляю схему " + name + " в " + x + " " + y + " " + z + "…");
        BlockManager manager = new BlockManager(level);
        structure.place(new Position(x, y, z, level), false, manager);
        sender.sendMessage("§aВставка завершена. Блок-сущностей восстановлено: "
                + structure.getBlockEntities().size());
        sender.sendMessage("§eСтарый дом и старые чанки не изменены.");
        getLogger().info("Pasted structure " + name + " at " + x + "," + y + "," + z
                + " in " + level.getFolderName());
    }

    private boolean allChunksLoaded(Level level, int x, int z, int sizeX, int sizeZ) {
        int minChunkX = Math.floorDiv(x, 16);
        int maxChunkX = Math.floorDiv(x + sizeX - 1, 16);
        int minChunkZ = Math.floorDiv(z, 16);
        int maxChunkZ = Math.floorDiv(z + sizeZ - 1, 16);
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!level.isChunkLoaded(cx, cz)) return false;
            }
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§6--- HouseTransfer 0.1.1 ---");
        sender.sendMessage("§e/copy pos1 §7— первая точка на блоке под ногами");
        sender.sendMessage("§e/copy pos2 §7— вторая точка на блоке под ногами");
        sender.sendMessage("§e/copy info §7— размер выделения");
        sender.sendMessage("§e/copy save <имя> §7— сохранить дом вместе с BlockEntity NBT");
        sender.sendMessage("§e/copy paste <имя> [x y z] §7— вставить схему");
        sender.sendMessage("§cПлагин не удаляет блоки и чанки исходного дома.");
        sender.sendMessage("§7Отдельные сущности (мобы, рамки, картины, стойки) не копируются.");
    }

    private String safe(String value) {
        return value.replace('\n', ' ').replace('\r', ' ').replace('|', '/');
    }

    private static final class Selection {
        private int x1, y1, z1, x2, y2, z2;
        private Level level1, level2;
        private boolean hasPos1, hasPos2;
    }
}