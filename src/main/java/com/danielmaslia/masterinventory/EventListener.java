package com.danielmaslia.masterinventory;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
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
        inventoryManager.savePlayerInventory(event.getPlayer());
        event.getPlayer().sendMessage("§b[Chat] §7Hello, §a" + event.getPlayer().getName()
                + "§7, I am here to help. Type §a\"/chat\" §7to start a session.");
        if (Reminder.reminders.containsKey(event.getPlayer().getUniqueId())) {
            for (Map.Entry<String, Reminder> entry : Reminder.reminders.get(event.getPlayer().getUniqueId()).entrySet()) {
                Reminder timer = entry.getValue();
                
                if (timer.getDur() == -1.0) {
                    // no error handling because player is online
                    timer.sendMessage();
                    // no error handling because existence in map already checked
                    timer.removeTimer();
                } else {
                    timer.runTimer();
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        inventoryManager.savePlayerInventory(event.getPlayer());
        if (Reminder.reminders.containsKey(event.getPlayer().getUniqueId())) {
            for (Map.Entry<String, Reminder> entry : Reminder.reminders.get(event.getPlayer().getUniqueId()).entrySet()) {
                Reminder timer = entry.getValue();
                
                if (timer.getDur() != -1.0) {
                    timer.pauseTimer();
                }
            }
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
