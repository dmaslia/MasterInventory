package com.danielmaslia.masterinventory;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;

public class EventListener implements Listener {
    private final JavaPlugin plugin;
    private final InventoryManager inventoryManager;
    private final ChatManager chatManager;
    private final ActivityTracker activityTracker;

    private static final Set<Material> RARE_ITEMS = Set.of(
        Material.DIAMOND,
        Material.EMERALD,
        Material.NETHERITE_INGOT,
        Material.NETHERITE_SCRAP,
        Material.ANCIENT_DEBRIS,
        Material.TOTEM_OF_UNDYING,
        Material.NETHER_STAR,
        Material.ELYTRA,
        Material.DRAGON_EGG,
        Material.ENCHANTED_GOLDEN_APPLE
    );

    public EventListener(JavaPlugin plugin, InventoryManager inventoryManager, ChatManager chatManager, ActivityTracker activityTracker) {
        this.plugin = plugin;
        this.inventoryManager = inventoryManager;
        this.chatManager = chatManager;
        this.activityTracker = activityTracker;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        inventoryManager.savePlayerInventory(p);
        p.sendMessage("§b[Chat] §7Hello, §a" + p.getName()
                + "§7, I am here to help. Type §a\"/chat\" §7to start a session.");
        Reminder.playerJoin(p);

        activityTracker.logActivity(p.getName(), "JOIN", "joined the game");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        inventoryManager.savePlayerInventory(p);

        activityTracker.logActivity(p.getName(), "LEAVE", "left the game");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material material = event.getBlock().getType();
        String materialName = StringUtils.formatEnumString(material.toString());

        activityTracker.logActivity(player.getName(), "BLOCK_BREAK", "broke " + materialName);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Material material = event.getBlock().getType();
        String materialName = StringUtils.formatEnumString(material.toString());

        activityTracker.logActivity(player.getName(), "BLOCK_PLACE", "placed " + materialName);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();

        if (killer != null && !(entity instanceof Player)) {
            String entityName = StringUtils.formatEnumString(entity.getType().toString());
            activityTracker.logActivity(killer.getName(), "MOB_KILL", "killed a " + entityName);
        }
    }

    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getEntity();
        String deathMessage = event.getDeathMessage();

        if (deathMessage != null) {
            deathMessage = deathMessage.replace(player.getName(), "").trim();
        } else {
            deathMessage = "died";
        }

        activityTracker.logActivity(player.getName(), "DEATH", deathMessage);
    }

    @EventHandler
    public void onPlayerAdvancement(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        String advancementKey = event.getAdvancement().getKey().getKey();

        if (advancementKey.startsWith("recipes/")) {
            return;
        }

        String advancementName = advancementKey.replace("story/", "")
                                               .replace("nether/", "")
                                               .replace("end/", "")
                                               .replace("adventure/", "")
                                               .replace("husbandry/", "")
                                               .replace("_", " ");

        activityTracker.logActivity(player.getName(), "ADVANCEMENT", "earned advancement: " + advancementName);
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        Item itemEntity = event.getItem();
        ItemStack item = itemEntity.getItemStack();
        Material material = item.getType();

        if (RARE_ITEMS.contains(material)) {
            String itemName = StringUtils.formatEnumString(material.toString());
            int amount = item.getAmount();
            String details = "picked up " + amount + "x " + itemName;
            activityTracker.logActivity(player.getName(), "RARE_ITEM", details);
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
                chatManager.endConversation(player.getUniqueId());
                Bukkit.broadcastMessage("§b[Chat] §7Session ended. Memory cleared.");
                return;
            }

            chatManager.pauseTimer();
            chatManager.addToHistory(player.getUniqueId(), player.getName() + ": " + message);

            Bukkit.getScheduler().runTask(plugin, () -> {
                inventoryManager.countInventory();
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    String fullHistory = chatManager.getFullHistory(uuid);
                    Bukkit.broadcastMessage("§b[Chat] §7One sec...");
                    chatManager.runPythonAI(player, fullHistory);
                });
            });
        }
    }
}
