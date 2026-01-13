package com.danielmaslia.masterinventory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class ChatManager {
    private final JavaPlugin plugin;
    private final Map<Integer, List<String>> conversationHistory = new HashMap<>();
    private final Map<Integer, Integer> chatTimers = new HashMap<>();
    private final long chatDur = 6000;
    private BukkitTask actionBarTask;

    public ChatManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isInConversation(UUID playerId) {
        return conversationHistory.containsKey(0);
    }

    public void startConversation(UUID playerId) {
        conversationHistory.putIfAbsent(0, new ArrayList<>());
        showChatIndicator();
        startTimer(playerId);
    }

    public void endConversation(UUID playerId) {
        conversationHistory.remove(0);
        hideChatIndicator();

        if (chatTimers.containsKey(0)) {
            Bukkit.getScheduler().cancelTask(chatTimers.get(0));
            chatTimers.remove(0);
        }
    }

    private void startTimer(UUID playerId) {
        if (chatTimers.containsKey(0)) {
            Bukkit.getScheduler().cancelTask(chatTimers.get(0));
        }

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            endConversation(playerId);
            Bukkit.broadcastMessage("§b[Chat] §7Session ended due to inactivity.");
        }, chatDur).getTaskId();

        chatTimers.put(0, taskId);
    }

    public void pauseTimer() {
        if (chatTimers.containsKey(0)) {
            Bukkit.getScheduler().cancelTask(chatTimers.get(0));
            chatTimers.remove(0);
        }
    }

    public void resumeTimer(UUID playerId) {
        startTimer(playerId);
    }

    private void showChatIndicator() {
        // Send action bar message repeatedly (every second) to keep it visible
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            TextComponent message = new TextComponent("§8[§aChat§8]");
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, message);
            }
        }, 0L, 20L); // Run immediately, then every 20 ticks (1 second)
    }

    private void hideChatIndicator() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;

            // Clear the action bar
            TextComponent empty = new TextComponent("");
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, empty);
            }
        }
    }

    public void addToHistory(UUID playerId, String message) {
        if (conversationHistory.containsKey(0)) {
            conversationHistory.get(0).add(message);
        }
    }

    public String getFullHistory(UUID playerId) {
        if (conversationHistory.containsKey(0)) {
            return String.join("###", conversationHistory.get(0));
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
                        if (conversationHistory.containsKey(0)) {
                            conversationHistory.get(0).add("system: " + result);
                        }
                    }
                    resumeTimer(player.getUniqueId());
                });

            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Bukkit.broadcastMessage("§b[Chat] §7System Error: " + e.getMessage());
                    resumeTimer(player.getUniqueId());
                });
            }
        });
    }
}
