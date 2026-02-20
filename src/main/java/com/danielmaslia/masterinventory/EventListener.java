package com.danielmaslia.masterinventory;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerReplenishTradeEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Location;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.inventory.meta.BookMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class EventListener implements Listener {
    private final JavaPlugin plugin;
    private final InventoryManager inventoryManager;
    private final ChatManager chatManager;
    private static boolean resetting = false;

    private record PendingLink(String worldName, String gameMode, int[] tpCoords) {}
    private final Map<UUID, PendingLink> pendingPortalLinks = new HashMap<>();
    private final List<PortalLink> portalLinks = new ArrayList<>();
    private final Set<String> linkedWorlds = new HashSet<>();

    private record PortalLink(String configKey, String world, int x, int y, int z, String targetWorld, String gameMode,
                               String tpWorld, int tpX, int tpY, int tpZ) {
        boolean isNear(Location loc, int radius) {
            return loc.getWorld() != null
                    && loc.getWorld().getName().equals(world)
                    && Math.abs(loc.getBlockX() - x) <= radius
                    && Math.abs((loc.getBlockY() + 1) - y) <= radius
                    && Math.abs(loc.getBlockZ() - z) <= radius;
        }
    }

    public EventListener(JavaPlugin plugin, InventoryManager inventoryManager, ChatManager chatManager) {
        this.plugin = plugin;
        this.inventoryManager = inventoryManager;
        this.chatManager = chatManager;
        loadPortals();
    }

    private void loadPortals() {
        portalLinks.clear();
        linkedWorlds.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("portals");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection p = section.getConfigurationSection(key);
            if (p == null) continue;
            String targetWorld = p.getString("target_world");
            portalLinks.add(new PortalLink(
                    key,
                    p.getString("world"),
                    p.getInt("x"),
                    p.getInt("y"),
                    p.getInt("z"),
                    targetWorld,
                    p.getString("gamemode"),
                    p.getString("tp_world"),
                    p.getInt("tp_x"),
                    p.getInt("tp_y"),
                    p.getInt("tp_z")
            ));
            linkedWorlds.add(targetWorld);
        }
    }

    public void addPendingPortal(UUID player, String worldName, String gameMode, int[] tpCoords) {
        pendingPortalLinks.put(player, new PendingLink(worldName, gameMode, tpCoords));
    }

    public void addPendingRemoval(UUID player) {
        pendingPortalLinks.put(player, new PendingLink("__remove__", null, null));
    }

    public Location getTpLocation(String targetWorld) {
        for (PortalLink link : portalLinks) {
            if (link.targetWorld().equalsIgnoreCase(targetWorld) && link.tpWorld() != null) {
                World world = Bukkit.getWorld(link.tpWorld());
                if (world != null) {
                    return new Location(world, link.tpX() + 0.5, link.tpY(), link.tpZ() + 0.5);
                }
            }
        }
        return null;
    }

    public Location getPortalLocation(String targetWorld) {
        for (PortalLink link : portalLinks) {
            if (link.targetWorld().equalsIgnoreCase(targetWorld)) {
                World world = Bukkit.getWorld(link.world());
                if (world != null) {
                    return new Location(world, link.x() + 0.5, link.y() - 1, link.z() + 0.5);
                }
            }
        }
        return null;
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location headLevel = from.clone().add(0, 1, 0);

        if (pendingPortalLinks.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            PendingLink pending = pendingPortalLinks.remove(player.getUniqueId());
            String targetWorld = pending.worldName();
            String gameMode = pending.gameMode();

            // Handle removal
            if ("__remove__".equals(targetWorld)) {
                PortalLink toRemove = null;
                for (PortalLink link : portalLinks) {
                    if (link.isNear(headLevel, 2)) {
                        toRemove = link;
                        break;
                    }
                }
                if (toRemove != null) {
                    plugin.reloadConfig();
                    plugin.getConfig().set("portals." + toRemove.configKey(), null);
                    plugin.saveConfig();
                    portalLinks.remove(toRemove);
                    linkedWorlds.remove(toRemove.targetWorld());
                    player.sendMessage("§aPortal unlinked from world: §f" + toRemove.targetWorld());
                } else {
                    player.sendMessage("§cNo linked portal found here.");
                }
                return;
            }

            int x = headLevel.getBlockX();
            int y = headLevel.getBlockY();
            int z = headLevel.getBlockZ();
            String worldName = from.getWorld().getName();

            // Remove existing link at this portal location if any
            PortalLink existing = null;
            for (PortalLink link : portalLinks) {
                if (link.isNear(headLevel, 2)) {
                    existing = link;
                    break;
                }
            }
            if (existing != null) {
                plugin.getConfig().set("portals." + existing.configKey(), null);
                portalLinks.remove(existing);
                linkedWorlds.remove(existing.targetWorld());
            }

            String key = targetWorld.replace(" ", "_");
            plugin.getConfig().set("portals." + key + ".world", worldName);
            plugin.getConfig().set("portals." + key + ".x", x);
            plugin.getConfig().set("portals." + key + ".y", y);
            plugin.getConfig().set("portals." + key + ".z", z);
            plugin.getConfig().set("portals." + key + ".target_world", targetWorld);
            if (gameMode != null) {
                plugin.getConfig().set("portals." + key + ".gamemode", gameMode);
            }
            plugin.saveConfig();

            linkedWorlds.add(targetWorld);
            player.sendMessage("§aPortal linked to world: §f" + targetWorld);
            int[] tpCoords = pending.tpCoords();
            if (tpCoords != null) {
                // Manual tp coords provided, save them and skip portal building
                saveTpLocation(key, targetWorld, tpCoords[0], tpCoords[1], tpCoords[2]);
                World new_world = Bukkit.getWorld(targetWorld);
                new_world.setSpawnLocation(new Location(new_world, tpCoords[0], tpCoords[1], tpCoords[2]));
            } else {
                Location loc = Bukkit.getWorld(worldName).getSpawnLocation();
                tpCoords = new int []{(int) loc.getX(), (int) loc.getY(), (int) loc.getZ()};
            }
            portalLinks.add(new PortalLink(key, worldName, x, y, z, targetWorld, gameMode,
                    targetWorld, tpCoords[0], tpCoords[1], tpCoords[2]));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "wm teleport " + targetWorld + " " + player.getName());
            return;
        }

        
        

        // Check for a lectern near the portal with book params
        try {
            Location portalBase = from.clone();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        Block block = portalBase.getWorld().getBlockAt(
                                portalBase.getBlockX() + dx, portalBase.getBlockY() + dy, portalBase.getBlockZ() + dz);
                        if (block.getType() == Material.LECTERN && block.getState() instanceof Lectern lectern) {
                            ItemStack bookItem = lectern.getSnapshotInventory().getItem(0);
                            if (bookItem == null || !(bookItem.getItemMeta() instanceof BookMeta bookMeta)) continue;
                            int page = lectern.getPage();
                            if (page >= bookMeta.getPageCount()) continue;

                            String pageText = org.bukkit.ChatColor.stripColor(bookMeta.getPage(page + 1)).trim();
                            String[] parts = pageText.split("\\s+");
                            if (parts.length < 1 || parts[0].isEmpty()) continue;

                            String lecternWorld = parts[0];
                            World targetWorldObj = Bukkit.getWorld(lecternWorld);
                            if (targetWorldObj == null) continue;

                            int[] tpCoords = null;
                            String lecternGameMode = null;
                            int idx = 1;
                            if (parts.length >= idx + 3) {
                                try {
                                    tpCoords = new int[]{
                                        Integer.parseInt(parts[idx]),
                                        Integer.parseInt(parts[idx + 1]),
                                        Integer.parseInt(parts[idx + 2])
                                    };
                                    idx += 3;
                                } catch (NumberFormatException ignored) {}
                            }
                            if (parts.length > idx) {
                                try {
                                    GameMode.valueOf(parts[idx].toUpperCase());
                                    lecternGameMode = parts[idx].toUpperCase();
                                } catch (IllegalArgumentException ignored) {}
                            }

                            if (tpCoords == null) {
                                Location spawn = targetWorldObj.getSpawnLocation();
                                tpCoords = new int[]{spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ()};
                            } else {
                                targetWorldObj.setSpawnLocation(tpCoords[0], tpCoords[1], tpCoords[2]);
                            }

                            event.setCancelled(true);
                            int lx = headLevel.getBlockX();
                            int ly = headLevel.getBlockY();
                            int lz = headLevel.getBlockZ();
                            String srcWorld = from.getWorld().getName();
                            String key = lecternWorld.replace(" ", "_");

                            plugin.getConfig().set("portals." + key + ".world", srcWorld);
                            plugin.getConfig().set("portals." + key + ".x", lx);
                            plugin.getConfig().set("portals." + key + ".y", ly);
                            plugin.getConfig().set("portals." + key + ".z", lz);
                            plugin.getConfig().set("portals." + key + ".target_world", lecternWorld);
                            if (lecternGameMode != null) {
                                plugin.getConfig().set("portals." + key + ".gamemode", lecternGameMode);
                            }
                            saveTpLocation(key, lecternWorld, tpCoords[0], tpCoords[1], tpCoords[2]);
                            plugin.saveConfig();

                            portalLinks.add(new PortalLink(key, srcWorld, lx, ly, lz, lecternWorld, lecternGameMode,
                                    lecternWorld, tpCoords[0], tpCoords[1], tpCoords[2]));
                            linkedWorlds.add(lecternWorld);

                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                    "wm teleport " + lecternWorld + " " + player.getName());
                            return;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Any failure falls through to normal portal behavior
        }

        for (PortalLink link : portalLinks) {
            if (link.isNear(headLevel, 2)) {
                event.setCancelled(true);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "wm teleport " + link.targetWorld() + " " + player.getName());
                return;
            }
        }

        // Reverse teleport: if player is in a linked world, send them back to main world portal
        String currentWorld = player.getWorld().getName();
        if (linkedWorlds.contains(currentWorld)) {
            event.setCancelled(true);
            Location portalLoc = getPortalLocation(currentWorld);
            if (portalLoc != null) {
                player.teleport(portalLoc);
            } else {
                player.sendMessage("§cNo linked portal found to return to.");
            }
        }
    }
    
    // --- Portal building ---

    private void saveTpLocation(String configKey, String tpWorld, int tpX, int tpY, int tpZ) {
        plugin.getConfig().set("portals." + configKey + ".tp_world", tpWorld);
        plugin.getConfig().set("portals." + configKey + ".tp_x", tpX);
        plugin.getConfig().set("portals." + configKey + ".tp_y", tpY);
        plugin.getConfig().set("portals." + configKey + ".tp_z", tpZ);
        plugin.saveConfig();
    }

    // --- World-specific inventory management ---

    private static final Set<String> MAIN_WORLDS = Set.of("world", "world_nether", "world_the_end");

    public boolean isMainWorld(String worldName) {
        return MAIN_WORLDS.contains(worldName);
    }

    private String getWorldGameMode(String worldName) {
        for (PortalLink link : portalLinks) {
            if (link.targetWorld().equalsIgnoreCase(worldName)) {
                return link.gameMode();
            }
        }
        return null;
    }

    private String getInventoryKey(String worldName) {
        return isMainWorld(worldName) ? "main" : worldName;
    }

    private File getInventoryFile(UUID playerId) {
        File dir = new File(plugin.getDataFolder(), "inventories");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, playerId + ".yml");
    }

    private void saveWorldInventory(Player player, String key) {
        File file = getInventoryFile(player.getUniqueId());
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            config.set(key + ".inventory." + i, inv.getItem(i));
        }
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            config.set(key + ".armor." + i, armor[i]);
        }
        config.set(key + ".offhand", inv.getItemInOffHand());
        config.set(key + ".xp_level", player.getLevel());
        config.set(key + ".xp_exp", (double) player.getExp());
        config.set(key + ".health", player.getHealth());
        config.set(key + ".hunger", player.getFoodLevel());
        config.set(key + ".saturation", (double) player.getSaturation());
        config.set(key + ".gamemode", player.getGameMode().name());

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save inventory for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void loadWorldInventory(Player player, String key) {
        File file = getInventoryFile(player.getUniqueId());
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        PlayerInventory inv = player.getInventory();
        inv.clear();
        player.setLevel(0);
        player.setExp(0);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(5.0f);

        if (!config.contains(key)) {
            return;
        }

        for (int i = 0; i < 36; i++) {
            ItemStack item = config.getItemStack(key + ".inventory." + i);
            if (item != null) inv.setItem(i, item);
        }
        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            armor[i] = config.getItemStack(key + ".armor." + i);
        }
        inv.setArmorContents(armor);
        ItemStack offhand = config.getItemStack(key + ".offhand");
        if (offhand != null) inv.setItemInOffHand(offhand);
        player.setLevel(config.getInt(key + ".xp_level", 0));
        player.setExp((float) config.getDouble(key + ".xp_exp", 0.0));
        player.setHealth(config.getDouble(key + ".health", 20.0));
        player.setFoodLevel(config.getInt(key + ".hunger", 20));
        player.setSaturation((float) config.getDouble(key + ".saturation", 5.0));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String fromWorld = event.getFrom().getName();
        String toWorld = player.getWorld().getName();

        String fromKey = getInventoryKey(fromWorld);
        String toKey = getInventoryKey(toWorld);

        if (fromKey.equals(toKey)) return;

        boolean fromManaged = isMainWorld(fromWorld) || linkedWorlds.contains(fromWorld);
        boolean toManaged = isMainWorld(toWorld) || linkedWorlds.contains(toWorld);
        if (!fromManaged && !toManaged) return;

        saveWorldInventory(player, fromKey);
        loadWorldInventory(player, toKey);

        // Apply gamemode: use configured world gamemode, or restore saved gamemode
        String configuredGameMode = getWorldGameMode(toWorld);
        if (configuredGameMode != null) {
            try {
                player.setGameMode(GameMode.valueOf(configuredGameMode.toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        } else {
            // Restore the gamemode the player had when they were last in this world
            File file = getInventoryFile(player.getUniqueId());
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String savedMode = config.getString(toKey + ".gamemode");
            if (savedMode != null) {
                try {
                    player.setGameMode(GameMode.valueOf(savedMode));
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        String worldName = player.getWorld().getName();
        File file = getInventoryFile(player.getUniqueId());
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.set("death_world", worldName);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save death world for " + player.getName());
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        File file = getInventoryFile(player.getUniqueId());
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String deathWorld = config.getString("death_world");
        config.set("death_world", null);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to clear death world for " + player.getName());
        }

        if (deathWorld == null || isMainWorld(deathWorld)) return;
        if (!linkedWorlds.contains(deathWorld)) return;

        World world = Bukkit.getWorld(deathWorld);
        if (world == null) return;

        Location respawn = player.getRespawnLocation();
        if (respawn != null && respawn.getWorld().getName().equals(deathWorld)) {
            event.setRespawnLocation(respawn);
        } else {
            event.setRespawnLocation(world.getSpawnLocation());
        }
    }

    public void nameEntity(Entity entity, String name) {
        entity.setCustomName(org.bukkit.ChatColor.AQUA + "" + org.bukkit.ChatColor.BOLD + name);
        entity.setCustomNameVisible(true);
        entity.setMetadata("name", new FixedMetadataValue(plugin, name));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        inventoryManager.savePlayerInventory(p);

        String worldName = p.getWorld().getName();
        if (linkedWorlds.contains(worldName)) {
            loadWorldInventory(p, worldName);
        }

        p.sendMessage("§b[Chat] §7Hello, §a" + p.getName()
                + "§7, I am here to help. Type §a\"/chat\" §7to start a session.");
        Reminder.playerJoin(p);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        inventoryManager.savePlayerInventory(player);

        String worldName = player.getWorld().getName();
        String key = getInventoryKey(worldName);
        if (linkedWorlds.contains(worldName) || isMainWorld(worldName)) {
            saveWorldInventory(player, key);
        }

        chatManager.removeFromConversation(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerNameTag(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        
        ItemStack item = (event.getHand() == EquipmentSlot.HAND)
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        
        if (item.getType() == Material.NAME_TAG && event.getRightClicked() instanceof LivingEntity entity) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                nameEntity(entity, meta.getDisplayName());
            }
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) {
            return;
        }
        
        java.util.List<MerchantRecipe> recipes = new java.util.ArrayList<>(villager.getRecipes());
        boolean modified = false;
        for (MerchantRecipe recipe : recipes) {
            if (recipe.hasExperienceReward()) {
                recipe.setExperienceReward(false);
                modified = true;
            }
        }
        
        if (modified) {
            villager.setRecipes(recipes);
        }
    }

    @EventHandler
    public void onVillagerReplenish(VillagerReplenishTradeEvent event) {
        if (!resetting) {
            event.setCancelled(true);
        }
    }

    public static void resetVillagers() {
        resetting = true;
        try {
            for (World world : Bukkit.getWorlds()) {
                long time = world.getTime();
                if (time < 0 || time > 12000) {
                    continue;
                }

                for (Villager v : world.getEntitiesByClass(Villager.class)) {
                    if (v.getProfession() != Profession.NONE && v.getProfession() != Profession.NITWIT && !v.isSleeping()) {
                        java.util.List<MerchantRecipe> recipes = new java.util.ArrayList<>(v.getRecipes());
                        for (MerchantRecipe recipe : recipes) {
                            recipe.setUses(0);
                            recipe.setExperienceReward(false);
                            recipe.setDemand(0);
                        }
                        v.setRecipes(recipes);

                        Sound workSound = getWorkSound(v.getProfession());
                        if (workSound != null) {
                            world.playSound(v.getLocation(), workSound, 1.0f, 1.0f);
                        }
                    }
                }
            }
        } finally {
            resetting = false;
        }
    }

    private static Sound getWorkSound(Profession profession) {
        if (profession == Profession.ARMORER) return Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE;
        if (profession == Profession.BUTCHER) return Sound.BLOCK_SMOKER_SMOKE;
        if (profession == Profession.CARTOGRAPHER) return Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT;
        if (profession == Profession.CLERIC) return Sound.BLOCK_BREWING_STAND_BREW;
        if (profession == Profession.FARMER) return Sound.ITEM_HOE_TILL;
        if (profession == Profession.FISHERMAN) return Sound.ENTITY_FISHING_BOBBER_SPLASH;
        if (profession == Profession.FLETCHER) return Sound.ITEM_CROSSBOW_LOADING_MIDDLE;
        if (profession == Profession.LEATHERWORKER) return Sound.BLOCK_FIRE_EXTINGUISH;
        if (profession == Profession.LIBRARIAN) return Sound.ITEM_BOOK_PAGE_TURN;
        if (profession == Profession.MASON) return Sound.UI_STONECUTTER_TAKE_RESULT;
        if (profession == Profession.SHEPHERD) return Sound.ENTITY_SHEEP_SHEAR;
        if (profession == Profession.TOOLSMITH) return Sound.BLOCK_ANVIL_USE;
        if (profession == Profession.WEAPONSMITH) return Sound.BLOCK_ANVIL_USE;
        return null;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (chatManager.isInConversation(uuid)) {
            event.setCancelled(true);
            String message = event.getMessage();
            Bukkit.broadcastMessage("§a[" + player.getName() + "] §f " + message);

            if (message.equalsIgnoreCase("exit")) {
                chatManager.endConversation();
                Bukkit.broadcastMessage("§b[Chat] §7Session ended. Memory cleared.");
                return;
            }

            chatManager.pauseTimer();
            chatManager.addToHistory(player.getName() + ": " + message);

            Bukkit.getScheduler().runTask(plugin, () -> {
                inventoryManager.countInventory();
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    String fullHistory = chatManager.getFullHistory();
                    Bukkit.broadcastMessage("§b[Chat] §7One sec...");
                    chatManager.runPythonAI(player, fullHistory);
                });
            });
        }
    }
}
