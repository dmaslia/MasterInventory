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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
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
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.EntityType;

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

    public Location getFirstPortalLocation() {
        for (PortalLink link : portalLinks) {
            World world = Bukkit.getWorld(link.world());
            if (world != null) {
                return new Location(world, link.x() + 0.5, link.y() - 1, link.z() + 0.5);
            }
        }
        return null;
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location headLevel = from.clone().add(0, 1, 0);
        
        if (!isMainWorld(event.getPlayer().getWorld().getName())) {
            return;
        }

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

        
        

        // Check for a lectern near the portal with book params (inside-out, 20 block radius)
        try {
            Location portalBase = from.clone();
            int radius = 20;
            List<int[]> offsets = new ArrayList<>();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                            offsets.add(new int[]{dx, dy, dz});
                        }
                    }
                }
            }
            offsets.sort((a, b) -> Integer.compare(
                    a[0] * a[0] + a[1] * a[1] + a[2] * a[2],
                    b[0] * b[0] + b[1] * b[1] + b[2] * b[2]));
            for (int[] offset : offsets) {
                int dx = offset[0], dy = offset[1], dz = offset[2];
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

                            // "nether" keyword removes the link and restores vanilla behavior
                            if (lecternWorld.equalsIgnoreCase("nether")) {
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
                                }
                                break;
                            }

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

                            // Remove existing link at this portal location if any
                            PortalLink existingLectern = null;
                            for (PortalLink link : portalLinks) {
                                if (link.isNear(headLevel, 2)) {
                                    existingLectern = link;
                                    break;
                                }
                            }
                            if (existingLectern != null) {
                                plugin.getConfig().set("portals." + existingLectern.configKey(), null);
                                portalLinks.remove(existingLectern);
                                linkedWorlds.remove(existingLectern.targetWorld());
                            }

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
        if (fromKey.equals("skyblock_nether")) fromKey = "skyblock";
        if (toKey.equals("skyblock_nether")) toKey = "skyblock";

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
    public void onSpawnChange(org.bukkit.event.player.PlayerSpawnChangeEvent event) {
        Player player = event.getPlayer();
        Location newSpawn = event.getNewSpawn();
        if (newSpawn == null) return;

        String worldName = newSpawn.getWorld().getName();
        String key = getInventoryKey(worldName);

        File file = getInventoryFile(player.getUniqueId());
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.set(key + ".respawn.x", newSpawn.getBlockX());
        config.set(key + ".respawn.y", newSpawn.getBlockY());
        config.set(key + ".respawn.z", newSpawn.getBlockZ());
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save respawn location for " + player.getName());
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

        String key = getInventoryKey(deathWorld);
        if (config.contains(key + ".respawn.x")) {
            int rx = config.getInt(key + ".respawn.x");
            int ry = config.getInt(key + ".respawn.y");
            int rz = config.getInt(key + ".respawn.z");
            event.setRespawnLocation(new Location(world, rx + 0.5, ry, rz + 0.5));
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
        inventoryManager.flushDirty();

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
        inventoryManager.flushDirty();

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
                            recipe.setExperienceReward(true);
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
    public void onInventoryClose(InventoryCloseEvent event) {
        Location loc = event.getInventory().getLocation();
        if (loc != null) {
            inventoryManager.markDirty(loc);
            inventoryManager.flushDirty();
        }
        if (event.getPlayer() instanceof Player player) {
            inventoryManager.savePlayerInventory(player);
        }
    }

    @EventHandler
    public void onInventoryMove(InventoryMoveItemEvent event) {

        Location srcLoc = event.getSource().getLocation();
        Location destLoc = event.getDestination().getLocation();
        if (srcLoc != null) inventoryManager.markDirty(srcLoc);
        if (destLoc != null) inventoryManager.markDirty(destLoc);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getState() instanceof org.bukkit.block.Container container) {
            Location loc = container.getInventory().getLocation();
            inventoryManager.removeContainer(loc);
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        for (org.bukkit.block.Block block : event.blockList()) {
            if (block.getState() instanceof org.bukkit.block.Container container) {
                inventoryManager.removeContainer(container.getInventory().getLocation());
            }
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        for (org.bukkit.block.Block block : event.blockList()) {
            if (block.getState() instanceof org.bukkit.block.Container container) {
                inventoryManager.removeContainer(container.getInventory().getLocation());
            }
        }
    }

    private static final Random random = new Random();

    private static final Map<EntityType, String[]> MOB_MESSAGES = Map.ofEntries(
        Map.entry(EntityType.CREEPER, new String[]{
            "Your build is ugly. I'd be doing you a favor.",
            "I've seen dirt huts with more effort than your base.",
            "Stand still, I want to redecorate your face.",
            "You flinched. Pathetic.",
            "Even TNT has more personality than you."
        }),
        Map.entry(EntityType.ZOMBIE, new String[]{
            "I'd eat your brain but there's clearly nothing there.",
            "You fight like someone playing with their feet.",
            "I'm literally rotting and I still look better than you.",
            "Imagine losing to a mob that can't even open a door.",
            "Your armor is embarrassing. Just take it off."
        }),
        Map.entry(EntityType.SKELETON, new String[]{
            "I've missed you on purpose. You're not worth the arrow.",
            "You couldn't dodge a snowball, let alone my shots.",
            "Even without a brain I'm smarter than you.",
            "Nice sword. Shame you don't know how to use it.",
            "I have better aim with no eyes than you do with two."
        }),
        Map.entry(EntityType.SPIDER, new String[]{
            "You scream like a baby when you see me. I heard it.",
            "I've been on your ceiling for three nights. You never noticed.",
            "Eight legs and I'm still faster than you.",
            "Your reflexes are honestly insulting.",
            "I've wrapped better prey than you. Way better."
        }),
        Map.entry(EntityType.ENDERMAN, new String[]{
            "I moved a block from your build. You still haven't noticed.",
            "You're not worth teleporting away from.",
            "I've watched you get lost in your own base. Twice.",
            "Don't look at me. You'd lose that fight.",
            "I've seen the End. It's more interesting than you."
        }),
        Map.entry(EntityType.WITCH, new String[]{
            "I'd brew you a potion of intelligence but I don't have enough ingredients.",
            "You couldn't even beat me without milk. Sad.",
            "My splash potions have more aim than your life.",
            "I've hexed smarter people than you. Actually, everyone's smarter.",
            "Your inventory management is a war crime."
        }),
        Map.entry(EntityType.VILLAGER, new String[]{
            "Hrrm. That means I'm judging you. Heavily.",
            "I raised my prices because I saw you coming.",
            "You overpaid last time and I'm still laughing.",
            "The iron golem doesn't protect you. I told him not to.",
            "Even a nitwit contributes more than you."
        }),
        Map.entry(EntityType.COW, new String[]{
            "You need me more than I need you. Remember that.",
            "Moo. That's cow for 'you're annoying.'",
            "You really came all this way for leather? Loser.",
            "I've seen your farm. It's pathetic.",
            "Stop staring at me like I owe you something."
        }),
        Map.entry(EntityType.PIG, new String[]{
            "Put a saddle on me and see what happens. I dare you.",
            "You smell worse than I do and that's saying something.",
            "I'd rather be a porkchop than spend another second near you.",
            "Your carrot farm? I've seen better in a desert.",
            "Oink. That means you're trash, by the way."
        }),
        Map.entry(EntityType.SHEEP, new String[]{
            "Shear me one more time. See what happens.",
            "My wool is worth more than everything you own.",
            "You dyed me pink. I will never forgive you.",
            "I follow you because I pity you, not because I like you.",
            "Baa. Translation: you're basic."
        }),
        Map.entry(EntityType.CHICKEN, new String[]{
            "I lay eggs worth more than your entire build.",
            "You couldn't catch me if I was standing still. Oh wait.",
            "My feathers have more value than your opinion.",
            "Even I can fly better than you can play.",
            "Bawk bawk. That means 'get out of my face.'"
        }),
        Map.entry(EntityType.WOLF, new String[]{
            "I only follow you because you have bones. Don't get it twisted.",
            "I've seen you fight. I'm embarrassed to be tamed by you.",
            "You really think we're friends? I tolerate you. Barely.",
            "I died for you last time and honestly I regret it.",
            "My previous owner was way better. Just saying."
        }),
        Map.entry(EntityType.CAT, new String[]{
            "I sit on your chest to inconvenience you. On purpose.",
            "You think I scare creepers for you? I do it for me.",
            "I've knocked better things off tables than you've ever built.",
            "Feed me or I leave. Actually, I might leave anyway.",
            "I've seen your base. My litter box has better interior design."
        }),
        Map.entry(EntityType.IRON_GOLEM, new String[]{
            "I protect the village. Not you. Know the difference.",
            "One hit. That's all it would take. Remember that.",
            "These roses are for the villagers. You get nothing.",
            "I've thrown zombies lighter than your ego.",
            "Touch a villager and I'll send you to respawn so fast."
        }),
        Map.entry(EntityType.PHANTOM, new String[]{
            "You look awful. When's the last time you slept?",
            "I exist because you're too stubborn to use a bed. Idiot.",
            "Your sleep schedule is worse than your combat skills.",
            "I'm the consequence of your bad decisions. All of them.",
            "Even insomniacs think you need help."
        }),
        Map.entry(EntityType.FOX, new String[]{
            "I stole your stuff and I'd do it again.",
            "You're slow, predictable, and honestly boring.",
            "I could outrun you carrying a chicken. And I have.",
            "Your berry farm is a joke. I've seen better in the wild.",
            "I'm cuter, faster, and smarter. Deal with it."
        })
    );

    private static final String[] GENERIC_MESSAGES = {
        "You're really not as good at this game as you think.",
        "I've seen noobs with better gear than you.",
        "Why are you even here? Go back to peaceful mode.",
        "Your base looks like a first-day build. And not in a good way.",
        "Even the bats are laughing at you.",
        "I've been watching you. It's not impressive.",
        "You mine like you've never held a pickaxe before.",
        "Honestly? The Ender Dragon would be disappointed.",
        "I'd run from you but it's funnier to watch you panic.",
        "You're the reason mobs don't respawn. They don't want to deal with you either."
    };

    public static void mobChat() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (random.nextDouble() >= 0.10) continue;

            List<LivingEntity> nearbyMobs = new ArrayList<>();
            for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
                if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
                    nearbyMobs.add(living);
                }
            }
            if (nearbyMobs.isEmpty()) continue;

            LivingEntity mob = nearbyMobs.get(random.nextInt(nearbyMobs.size()));
            String name = mob.getCustomName() != null
                    ? mob.getCustomName()
                    : StringUtils.formatEnumString(mob.getType().name());
            String[] messages = MOB_MESSAGES.getOrDefault(mob.getType(), GENERIC_MESSAGES);
            String message = messages[random.nextInt(messages.length)];

            player.sendMessage("§d[" + name + " whispers] §f" + message);
        }
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

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String fullHistory = chatManager.getFullHistory();
                Bukkit.broadcastMessage("§b[Chat] §7One sec...");
                chatManager.runPythonAI(player, fullHistory);
            });
        }
    }
}
