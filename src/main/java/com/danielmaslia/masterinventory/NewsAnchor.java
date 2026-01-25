package com.danielmaslia.masterinventory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class NewsAnchor {
    private final JavaPlugin plugin;
    private final ActivityTracker activityTracker;
    private BukkitTask newsTask;
    private long newsIntervalTicks;

    public NewsAnchor(JavaPlugin plugin, ActivityTracker activityTracker, long newsIntervalTicks) {
        this.plugin = plugin;
        this.activityTracker = activityTracker;
        this.newsIntervalTicks = newsIntervalTicks;
    }

    public void start() {
        if (newsTask != null) {
            newsTask.cancel();
        }

        newsTask = Bukkit.getScheduler().runTaskTimer(plugin, this::broadcastNews, newsIntervalTicks, newsIntervalTicks);
        plugin.getLogger().info("News Anchor started - broadcasting every " + (newsIntervalTicks / 20 / 60) + " minutes");
    }

    public void stop() {
        if (newsTask != null) {
            newsTask.cancel();
            newsTask = null;
            plugin.getLogger().info("News Anchor stopped");
        }
    }

    public void setInterval(long ticks) {
        this.newsIntervalTicks = ticks;
        if (newsTask != null) {
            stop();
            start();
        }
    }

    private void broadcastNews() {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }

        if (activityTracker.getActivityCount() == 0) {
            return;
        }

        activityTracker.exportToCSV();

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

                ProcessBuilder pb = new ProcessBuilder(pythonBinary, scriptPath, "--news");
                pb.redirectErrorStream(true);
                Process process = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                String line;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Bukkit.broadcastMessage("§6§l[NEWS] §r§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                });

                while ((line = reader.readLine()) != null) {
                    final String currentLine = line;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Bukkit.broadcastMessage(currentLine);
                    });
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Bukkit.broadcastMessage("§6§l[NEWS] §r§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                });

                process.waitFor();

            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Bukkit.broadcastMessage("§c[NEWS] Technical difficulties! Stand by...");
                });
                plugin.getLogger().severe("News Anchor error: " + e.getMessage());
            }
        });
    }

    public void triggerNewsNow() {
        broadcastNews();
    }
}
