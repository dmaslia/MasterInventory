package com.danielmaslia.masterinventory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class ChatManager {
    private final JavaPlugin plugin;
    private final Set<UUID> players = new HashSet<UUID>();
    private final List<String> conversationHistory = new ArrayList<String>();
    private Integer chatTimer = -1;
    private final long chatDur = 6000;
    private Map<UUID, Integer> actionBarTasks = new HashMap<>();

    public ChatManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isOn() {
        return (chatTimer != -1);
    }

    public boolean isInConversation(UUID playerId) {
        return players.contains(playerId);
    }

    public void addToConversation(UUID playerId) {
        players.add(playerId);
        showChatIndicator(playerId);
    }

    public void removeFromConversation(UUID playerId) {
        players.remove(playerId);
        hideChatIndicator(playerId);
    }


    public void startConversation() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if(p != null) {
                players.add(p.getUniqueId());
                showChatIndicator(p.getUniqueId());
            }
        }
        startTimer();
    }

    public void endConversation() {
        conversationHistory.clear();
        for(UUID uuid : players) {
            hideChatIndicator(uuid);
        }
        players.clear();
        Bukkit.getScheduler().cancelTask(chatTimer);
        chatTimer = -1;
    }

    private void startTimer() {
        if (chatTimer != -1) {
            Bukkit.getScheduler().cancelTask(chatTimer);
        }

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            endConversation();
            Bukkit.broadcastMessage("§b[Chat] §7Session ended due to inactivity.");
        }, chatDur).getTaskId();

        chatTimer = taskId;
    }

    public void pauseTimer() {
        if (chatTimer != -1) {
            Bukkit.getScheduler().cancelTask(chatTimer);
            chatTimer = -1;
        }
    }

    public void resumeTimer() {
        startTimer();
    }

    private void showChatIndicator(UUID uuid) {
        // Send action bar message repeatedly (every second) to keep it visible
        if(Bukkit.getPlayer(uuid) == null || !Bukkit.getPlayer(uuid).isOnline()) {
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (player != null && player.isOnline()) {
                TextComponent message = new TextComponent("§8[§aChat§8]");
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, message);
            }
        }, 0L, 20L).getTaskId(); // Run immediately, then every 20 ticks (1 second)
        actionBarTasks.put(uuid, taskId);
    }

    private void hideChatIndicator(UUID uuid) {
        if (actionBarTasks.containsKey(uuid)) {
            Bukkit.getScheduler().cancelTask(actionBarTasks.get(uuid));
            actionBarTasks.remove(uuid);

            // Clear the action bar
            if(Bukkit.getPlayer(uuid) != null && Bukkit.getPlayer(uuid).isOnline()) {
                TextComponent empty = new TextComponent("");
                Player player = Bukkit.getPlayer(uuid);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, empty);
            }

        }
    }

    public void addToHistory(String message) {
        conversationHistory.add(message);
    }

    public String getFullHistory() {
        if (!conversationHistory.isEmpty()) {
            return String.join("###", conversationHistory);
        }
        return "";
    }

    public void runPythonAI(Player player, String query) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String pluginDir = plugin.getDataFolder().getAbsolutePath();
                String os = System.getProperty("os.name").toLowerCase();
                String pythonBinary;

                if (os.contains("win")) {
                    pythonBinary = pluginDir + File.separator + "venv" + File.separator + "Scripts" + File.separator + "python.exe";
                } else {
                    pythonBinary = pluginDir + File.separator + "venv" + File.separator + "bin" + File.separator + "python";
                }

                String scriptPath = pluginDir + File.separator + "llm_runner.py";

                ProcessBuilder pb = new ProcessBuilder(pythonBinary, scriptPath, query);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder fullResponse = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    final String currentLine = line;
                    fullResponse.append(currentLine).append("\n");

                    Bukkit.getScheduler().runTask(plugin, () -> {
                    	Bukkit.broadcastMessage(currentLine);
                    });
                }

                String result = fullResponse.toString().trim();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (result.isEmpty()) {
                    	Bukkit.broadcastMessage("§b[Chat] §7Error: AI returned an empty response.");
                    } else {
                        if (!conversationHistory.isEmpty()) {
                            conversationHistory.add("system: " + result);
                        }
                    }
                    resumeTimer();
                });

            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Bukkit.broadcastMessage("§b[Chat] §7System Error: " + e.getMessage());
                    resumeTimer();
                });
            }
        });
    }
}
