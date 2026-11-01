package ru.frizmin;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.enchantments.Enchantment;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.inventory.InventoryClickEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.potion.Effect;
import cn.nukkit.scoreboard.Scoreboard;
import cn.nukkit.scoreboard.ScoreboardObjective;
import cn.nukkit.scoreboard.display.DisplaySlot;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FrizMineBedrock extends PluginBase implements Listener {

    private LuckPerms luckPerms;
    private Location lastBeacon;
    private Config economyConfig;
    private final Map<UUID, Scoreboard> scoreboards = new HashMap<>();
    private final Map<UUID, String> cachedRanks = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        this.luckPerms = LuckPermsProvider.get();
        saveResource("economy.yml", false);
        this.economyConfig = new Config(new File(getDataFolder(), "economy.yml"), Config.YAML);
        
        getServer().getPluginManager().registerEvents(this, this);
        startUpdateTask();
        getLogger().info("FrizMineBedrock (Yaroslavka Cool) включён!");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        try {
            User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
            cachedRanks.put(player.getUniqueId(), user.getPrimaryGroup());
        } catch (Exception e) {
            cachedRanks.put(player.getUniqueId(), "player");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        switch (command.getName().toLowerCase()) {
            case "kits": openKitsMenu(player); return true;
            case "grant": handleGrant(player, args); return true;
            case "talisman": handleTalisman(player, args); return true;
            case "tp": handleTp(player, args); return true;
            case "vanish": handleVanish(player); return true;
            case "killerbeacon": createBeaconStructure(player); return true;
            case "beacontp":
                if (lastBeacon != null) player.teleport(lastBeacon);
                else player.sendMessage(TextFormat.RED + "Маяк еще не создан!");
                return true;
            case "meteor": createMeteorStructure(player); return true;
            case "shop": openShop(player); return true;
            case "sell": openBuyer(player); return true;
            case "map": openMap(player); return true;
            case "help":
            case "commands":
            case "cmd":
                openCommandsMenu(player);
                return true;
            case "staffhelp":
                showStaffHelp(player);
                return true;
            case "bal":
            case "balance":
                player.sendMessage(TextFormat.GREEN + "Ваш баланс: " + getCoins(player) + " монет.");
                return true;
            case "blacktire": giveTire(player, "Black"); return true;
            case "watertire": giveTire(player, "Water"); return true;
            case "lavatire": giveTire(player, "Lava"); return true;
            default: return false;
        }
    }

    private void startUpdateTask() {
        getServer().getScheduler().scheduleRepeatingTask(this, () -> {
            for (Player player : getServer().getOnlinePlayers().values()) {
                updateScoreboard(player);
                updateNametag(player);
            }
        }, 60); // 60 тиков = 3 секунды (меньше лагов)
    }

    private void updateNametag(Player player) {
        String rank = getPlayerGroup(player);
        String prefix = getPrefix(rank);
        int hp = (int) Math.ceil(player.getHealth());
        player.setNameTag(prefix + " §f" + player.getName() + " §c❤" + hp);
    }

    private String getPrefix(String rank) {
        if (rank == null) return "§7[Игрок]";
        switch (rank.toLowerCase()) {
            case "owner": return "§4§l[Владелец]";
            case "administrator": return "§c[Админ]";
            case "seniormoderator": return "§3[Ст.Мод]";
            case "moderator": return "§9[Мод]";
            case "prince": return "§d[Князь]";
            default: return "§7[Игрок]";
        }
    }

    private void updateScoreboard(Player player) {
        Scoreboard board = scoreboards.computeIfAbsent(player.getUniqueId(), k -> {
            Scoreboard b = new Scoreboard();
            b.addObjective("sidebar", "dummy").setDisplaySlot(DisplaySlot.SIDEBAR);
            return b;
        });
        ScoreboardObjective obj = board.getObjective("sidebar");
        if (obj == null) return;
        
        obj.setDisplayName("§b§lYaroslavka §f§lCool");
        obj.getLines().forEach(obj::removeLine);
        
        obj.setLine(10, "§7Ник: §f" + player.getName());
        obj.setLine(9, "§7Ранг: " + getPrefix(getPlayerGroup(player)));
        obj.setLine(8, "§7Баланс: §e" + getCoins(player));
        obj.setLine(7, " ");
        obj.setLine(6, "§7Онлайн: §f" + getServer().getOnlinePlayers().size());
        obj.setLine(5, "§7Время: §f" + new SimpleDateFormat("HH:mm").format(new Date()));
        obj.setLine(4, "§7Коорд: §f" + player.getFloorX() + " " + player.getFloorY() + " " + player.getFloorZ());
        player.setScoreboard(board);
    }

    private void openKitsMenu(Player player) {
        Inventory inv = getServer().createInventory(new KitHolder(), 27, "§8Выбор кита");
        String rank = getPlayerGroup(player);
        boolean isOwner = rank.equalsIgnoreCase("owner");

        inv.setItem(10, createKitItem("minecraft:stone_sword", "§7Кит Игрока", !isOwner));
        inv.setItem(11, createKitItem("minecraft:iron_sword", "§9Кит Модератора", !isOwner && !rank.equals("moderator")));
        inv.setItem(12, createKitItem("minecraft:diamond_sword", "§3Кит Ст. Модератора", !isOwner && !rank.equals("seniormoderator")));
        inv.setItem(13, createKitItem("minecraft:netherite_sword", "§cКит Админа", !isOwner && !rank.equals("administrator")));
        inv.setItem(14, createKitItem("minecraft:golden_apple", "§dКит Князя", !isOwner && !rank.equals("prince")));
        inv.setItem(15, createKitItem("minecraft:elytra", "§4Кит Создателя", !isOwner));
        player.addWindow(inv);
    }

    private Item createKitItem(String material, String name, boolean locked) {
        Item item = Item.fromString(material);
        item.setCustomName(name);
        item.setLore(locked ? "§cЗаблокировано" : "§aНажми, чтобы взять");
        return item;
    }

    private void openMap(Player player) {
        Inventory inv = getServer().createInventory(new MapHolder(), 54, "§8Карта сервера");
        int slot = 0;
        for (Player online : getServer().getOnlinePlayers().values()) {
            if (slot >= 54) break;
            Item head = Item.fromString("minecraft:player_head");
            head.setCustomName("§e" + online.getName());
            head.setLore("§7Мир: §f" + online.getLevel().getName(), "§7X: " + online.getFloorX() + " Y: " + online.getFloorY() + " Z: " + online.getFloorZ(), "§aНажми для ТП");
            inv.setItem(slot++, head);
        }
        player.addWindow(inv);
    }

    private void openBuyer(Player player) {
        Inventory inv = getServer().createInventory(new BuyerHolder(), 27, "§8Скупщик");
        inv.setItem(10, createSellItem("minecraft:gold_ingot", "§6Золото", 5));
        inv.setItem(11, createSellItem("minecraft:iron_ingot", "§fЖелезо", 10));
        inv.setItem(12, createSellItem("minecraft:diamond", "§bАлмаз", 50));
        inv.setItem(13, createSellItem("minecraft:emerald", "§aИзумруд", 100));
        inv.setItem(14, createSellItem("minecraft:netherite_ingot", "§8Незерит", 500));
        inv.setItem(15, createSellItem("minecraft:ancient_debris", "§4Обломки", 200));
        player.addWindow(inv);
    }

    private Item createSellItem(String material, String name, int price) {
        Item item = Item.fromString(material);
        item.setCustomName(name);
        item.setLore("§7Цена: §e" + price + " монет", "§aНажми, чтобы продать всё");
        return item;
    }

    private void openShop(Player player) {
        Inventory inv = getServer().createInventory(new ShopHolder(), 27, "§8Донат-Магазин");
        
        Item talisman = Item.fromString("minecraft:totem_of_undying");
        talisman.setCustomName("§c§lТалисман Крушителя");
        talisman.setLore("§7Цена: §e5000 монет");
        inv.setItem(10, talisman);

        Item apple = Item.fromString("minecraft:enchanted_golden_apple", 0, 8);
        apple.setCustomName("§d§lЯблоко Бога x8");
        apple.setLore("§7Цена: §e2000 монет");
        inv.setItem(12, apple);

        Item sword = Item.fromString("minecraft:netherite_sword");
        sword.setCustomName("§6§lКлинок Владыки");
        sword.addEnchant(Enchantment.getEnchantment(Enchantment.ID_DAMAGE_ALL), 7);
        sword.setLore("§7Цена: §e10000 монет", "§7Острота VII");
        inv.setItem(14, sword);

        Item pickaxe = Item.fromString("minecraft:netherite_pickaxe");
        pickaxe.setCustomName("§b§lБур Ярославки");
        pickaxe.addEnchant(Enchantment.getEnchantment(Enchantment.ID_DIGGING), 10);
        pickaxe.setLore("§7Цена: §e8000 монет", "§7Эфф. X");
        inv.setItem(16, pickaxe);
        player.addWindow(inv);
    }

    private void openCommandsMenu(Player player) {
        Inventory inv = getServer().createInventory(new CommandHolder(), 27, "§8Список команд");
        String rank = getPlayerGroup(player).toLowerCase();

        inv.setItem(10, createCommandBook("minecraft:book", "§aКоманды Игрока", "§7Базовые команды сервера"));
        if (rank.equals("moderator") || rank.equals("seniormoderator") || rank.equals("administrator") || rank.equals("owner")) {
            inv.setItem(12, createCommandBook("minecraft:iron_sword", "§9Команды Модератора", "§7Инструменты модерации"));
        }
        if (rank.equals("administrator") || rank.equals("owner")) {
            inv.setItem(14, createCommandBook("minecraft:diamond_sword", "§cКоманды Админа", "§7Взрывчатка и управление"));
        }
        if (rank.equals("owner")) {
            inv.setItem(16, createCommandBook("minecraft:netherite_sword", "§4Команды Создателя", "§7Абсолютно все команды"));
        }
        player.addWindow(inv);
    }

    private Item createCommandBook(String material, String name, String lore) {
        Item item = Item.fromString(material);
        item.setCustomName(name);
        item.setLore(lore + "\n§aНажми, чтобы посмотреть");
        return item;
    }

    private void showStaffHelp(Player player) {
        String rank = getPlayerGroup(player).toLowerCase();
        player.sendMessage("§8§m--------------------------------");
        player.sendMessage("§b§lВаши команды (" + rank + "):");
        player.sendMessage("§e/kits §7- Меню китов | §e/shop §7- Магазин | §e/sell §7- Скупщик | §e/map §7- Карта | §e/bal §7- Баланс");
        if (rank.equals("moderator") || rank.equals("seniormoderator") || rank.equals("administrator") || rank.equals("owner")) {
            player.sendMessage("§9Стафф: §e/vanish, /tp, /grant, /killerbeacon, /meteor");
        }
        if (rank.equals("administrator") || rank.equals("owner")) {
            player.sendMessage("§cАдмин: §e/talisman, /blacktire, /watertire, /lavatire");
        }
        player.sendMessage("§8§m--------------------------------");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() == null) return;
        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);
        int slot = event.getSlot();

        if (event.getInventory().getHolder() instanceof KitHolder) {
            String rank = getPlayerGroup(player);
            boolean isOwner = rank.equalsIgnoreCase("owner");
            if (slot == 10) giveKit(player, "player");
            else if (slot == 11 && (isOwner || rank.equals("moderator"))) giveKit(player, "moderator");
            else if (slot == 12 && (isOwner || rank.equals("seniormoderator"))) giveKit(player, "seniormoderator");
            else if (slot == 13 && (isOwner || rank.equals("administrator"))) giveKit(player, "administrator");
            else if (slot == 14 && (isOwner || rank.equals("prince"))) giveKit(player, "prince");
            else if (slot == 15 && isOwner) giveKit(player, "owner");
            else player.sendMessage(TextFormat.RED + "У вас нет доступа!");
            player.closeInventory();
        }
        else if (event.getInventory().getHolder() instanceof BuyerHolder) {
            Item sellItem = null; int price = 0;
            if (slot == 10) { sellItem = Item.fromString("minecraft:gold_ingot"); price = 5; }
            else if (slot == 11) { sellItem = Item.fromString("minecraft:iron_ingot"); price = 10; }
            else if (slot == 12) { sellItem = Item.fromString("minecraft:diamond"); price = 50; }
            else if (slot == 13) { sellItem = Item.fromString("minecraft:emerald"); price = 100; }
            else if (slot == 14) { sellItem = Item.fromString("minecraft:netherite_ingot"); price = 500; }
            else if (slot == 15) { sellItem = Item.fromString("minecraft:ancient_debris"); price = 200; }

            if (sellItem != null) {
                int count = 0;
                for (Map.Entry<Integer, Item> entry : player.getInventory().getContents().entrySet()) {
                    if (entry.getValue().equals(sellItem, true, false)) {
                        count += entry.getValue().getCount();
                        player.getInventory().clear(entry.getKey());
                    }
                }
                if (count > 0) {
                    addCoins(player, count * price);
                    player.sendMessage(TextFormat.GREEN + "Продано " + count + " шт. за " + (count * price) + " монет!");
                } else player.sendMessage(TextFormat.RED + "Нет предметов!");
                player.closeInventory();
            }
        }
        else if (event.getInventory().getHolder() instanceof MapHolder) {
            Item clicked = event.getCurrentItem();
            if (clicked != null && clicked.hasCustomName()) {
                Player target = getServer().getPlayer(clicked.getCustomName().replace("§e", ""));
                if (target != null) { player.teleport(target.getLocation()); player.closeInventory(); }
            }
        }
        else if (event.getInventory().getHolder() instanceof ShopHolder) {
            if (slot == 10 && removeCoins(player, 5000)) { player.getInventory().addItem(createTalisman()); player.sendMessage("§aКуплено!"); }
            else if (slot == 12 && removeCoins(player, 2000)) { player.getInventory().addItem(Item.fromString("minecraft:enchanted_golden_apple", 0, 8)); player.sendMessage("§aКуплено!"); }
            else if (slot == 14 && removeCoins(player, 10000)) { player.getInventory().addItem(event.getCurrentItem()); player.sendMessage("§aКуплено!"); }
            else if (slot == 16 && removeCoins(player, 8000)) { player.getInventory().addItem(event.getCurrentItem()); player.sendMessage("§aКуплено!"); }
            else player.sendMessage(TextFormat.RED + "Недостаточно монет!");
        }
        else if (event.getInventory().getHolder() instanceof CommandHolder) {
            if (slot == 10) player.sendMessage("§a§lКОМАНДЫ ИГРОКА: §e/kits, /shop, /sell, /map, /bal");
            else if (slot == 12) player.sendMessage("§9§lКОМАНДЫ МОДЕРАТОРА: §e/vanish, /tp, /grant, /killerbeacon, /meteor");
            else if (slot == 14) player.sendMessage("§c§lКОМАНДЫ АДМИНА: §e/talisman, /blacktire, /watertire, /lavatire, /gm 1");
            else if (slot == 16) player.sendMessage("§4§lКОМАНДЫ СОЗДАТЕЛЯ: §eУ вас есть доступ ко всем командам.");
            player.closeInventory();
        }
    }

    private int getCoins(Player player) { return economyConfig.getInt(player.getUniqueId().toString(), 500); }
    private void addCoins(Player player, int amount) {
        economyConfig.set(player.getUniqueId().toString(), getCoins(player) + amount);
        economyConfig.save();
    }
    private boolean removeCoins(Player player, int amount) {
        if (getCoins(player) >= amount) {
            economyConfig.set(player.getUniqueId().toString(), getCoins(player) - amount);
            economyConfig.save();
            return true;
        }
        return false;
    }

    private String getPlayerGroup(Player player) {
        return cachedRanks.getOrDefault(player.getUniqueId(), "player");
    }

    private void setPlayerGroup(Player player, String groupName) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            user.data().add(InheritanceNode.builder(groupName).build());
            luckPerms.getUserManager().saveUser(user);
            cachedRanks.put(player.getUniqueId(), groupName);
        }
    }

    private void giveKit(Player player, String kitName) {
        switch (kitName) {
            case "player":
                player.getInventory().addItem(Item.fromString("minecraft:stone_sword"));
                player.getInventory().addItem(Item.fromString("minecraft:stone_pickaxe"));
                break;
            case "moderator":
                player.getInventory().addItem(Item.fromString("minecraft:iron_sword"));
                break;
            case "seniormoderator":
                player.getInventory().addItem(Item.fromString("minecraft:diamond_sword"));
                break;
            case "administrator":
                player.getInventory().addItem(Item.fromString("minecraft:netherite_sword"));
                break;
            case "prince":
                player.getInventory().addItem(Item.fromString("minecraft:diamond_sword"));
                player.getInventory().addItem(createTalisman());
                break;
            case "owner":
                player.getInventory().addItem(Item.fromString("minecraft:netherite_sword"));
                player.getInventory().addItem(Item.fromString("minecraft:elytra"));
                break;
        }
        player.sendMessage(TextFormat.GREEN + "Кит выдан!");
    }

    private Item createTalisman() {
        Item item = Item.fromString("minecraft:totem_of_undying");
        item.setCustomName(TextFormat.RED + "Талисман Крушителя");
        return item;
    }

    private void giveTire(Player player, String type) {
        Item item = Item.fromString("minecraft:tnt");
        item.setCustomName(TextFormat.DARK_GRAY + type + " Tire");
        player.getInventory().addItem(item);
    }

    private void handleGrant(Player player, String[] args) {
        if (args.length < 2) return;
        Player target = getServer().getPlayer(args[0]);
        if (target != null) {
            setPlayerGroup(target, args[1].toLowerCase());
            player.sendMessage(TextFormat.GREEN + "Привилегия выдана: " + target.getName());
        }
    }
    
    private void handleTalisman(Player player, String[] args) { player.getInventory().addItem(createTalisman()); }
    private void handleTp(Player player, String[] args) {
        if (args.length > 0) { Player target = getServer().getPlayer(args[0]); if (target != null) player.teleport(target.getLocation()); }
    }
    private void handleVanish(Player player) {
        if (player.hasEffect(Effect.INVISIBILITY)) player.removeEffect(Effect.INVISIBILITY);
        else player.addEffect(Effect.getEffect(Effect.INVISIBILITY).setDuration(Integer.MAX_VALUE).setVisible(false));
    }

    private void createBeaconStructure(Player player) {
        Level level = player.getLevel();
        int x = new java.util.Random().nextInt(2000) - 1000;
        int z = new java.util.Random().nextInt(2000) - 1000;
        int y = level.getHighestBlockAt(x, z).getFloorY() + 1;
        lastBeacon = new Location(x, y, z, level);

        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) level.setBlock(new Vector3(x + dx, y - 1, z + dz), Item.fromString("minecraft:obsidian").getBlock());
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.setBlock(new Vector3(x + dx, y, z + dz), Item.fromString("minecraft:netherite_block").getBlock());
        for (int i = 1; i <= 3; i++) level.setBlock(new Vector3(x, y + i, z), Item.fromString("minecraft:beacon").getBlock());
        level.setBlock(new Vector3(x, y + 4, z), Item.fromString("minecraft:end_crystal").getBlock());
        level.setBlock(new Vector3(x, y + 5, z), Item.fromString("minecraft:glowstone").getBlock());

        Vector3 chestPos = new Vector3(x + 3, y, z);
        level.setBlock(chestPos, Item.fromString("minecraft:chest").getBlock());
        if (level.getBlockEntity(chestPos) instanceof cn.nukkit.blockentity.BlockEntityChest chest) {
            chest.getInventory().addItem(Item.fromString("minecraft:diamond", 0, 16));
        }
        getServer().broadcastMessage(TextFormat.RED + "[Маяк Убийцы] " + TextFormat.WHITE + "Появился на: " + x + " " + y + " " + z);
    }

    private void createMeteorStructure(Player player) {
        Level level = player.getLevel();
        int x = new java.util.Random().nextInt(2000) - 1000;
        int z = new java.util.Random().nextInt(2000) - 1000;
        int y = level.getHighestBlockAt(x, z).getFloorY() + 1;

        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (dx * dx + dz * dz <= 9) {
                if (dx == 0 && dz == 0) level.setBlock(new Vector3(x + dx, y - 1, z + dz), Item.fromString("minecraft:magma").getBlock());
                else if (dx * dx + dz * dz >= 5) level.setBlock(new Vector3(x + dx, y - 1, z + dz), Item.fromString("minecraft:obsidian").getBlock());
            }
        }
        level.setBlock(new Vector3(x, y, z), Item.fromString("minecraft:netherite_block").getBlock());
        level.setBlock(new Vector3(x, y + 1, z), Item.fromString("minecraft:magma").getBlock());

        Vector3 chestPos = new Vector3(x, y + 2, z);
        level.setBlock(chestPos, Item.fromString("minecraft:chest").getBlock());
        getServer().broadcastMessage(TextFormat.GOLD + "[Метеорит] " + TextFormat.WHITE + "Упал на: " + x + " " + z);
    }

    private static class KitHolder implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    private static class BuyerHolder implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    private static class MapHolder implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    private static class ShopHolder implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    private static class CommandHolder implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
}
