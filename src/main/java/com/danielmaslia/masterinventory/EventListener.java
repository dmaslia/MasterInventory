package com.danielmaslia.masterinventory;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.entity.VillagerReplenishTradeEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class EventListener implements Listener {
    private final JavaPlugin plugin;
    private final InventoryManager inventoryManager;
    private final ChatManager chatManager;

    public EventListener(JavaPlugin plugin, InventoryManager inventoryManager, ChatManager chatManager) {
        this.plugin = plugin;
        this.inventoryManager = inventoryManager;
        this.chatManager = chatManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        inventoryManager.savePlayerInventory(p);
        p.sendMessage("§b[Chat] §7Hello, §a" + p.getName()
                + "§7, I am here to help. Type §a\"/chat\" §7to start a session.");
        Reminder.playerJoin(p);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        inventoryManager.savePlayerInventory(event.getPlayer());
        chatManager.removeFromConversation(event.getPlayer().getUniqueId());
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
        event.setCancelled(true);
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
