package com.danielmaslia.masterinventory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.md_5.bungee.api.ChatColor;


public class Timer {
    public static Map<UUID, Map<String, Timer>> timers = new HashMap<>();
    private UUID playerUUID;
    // double representation of number of hours
    // -1 if no timer/on next join
    private double timer;
    private int taskID;
    private long startedTime;
    private boolean active;
    private String name;
    private String description;

    public Timer(UUID uuid, double timer, String name, String description) {
        this.playerUUID = uuid;
        this.timer = timer;
        this.name = name;
        this.description = description;
        active = false;
    }

    public double getTimer() {
        return timer;
    }
    
    public boolean removeTimer() {
        if (timers.containsKey(playerUUID) && timers.get(playerUUID).get(name) != null) {
            timers.get(playerUUID).remove(name);
            if (timers.get(playerUUID).isEmpty()) {
                timers.remove(playerUUID);
            }
            return true;
        }
        return false;
    }

    public void pauseTimer() {
        if (active) {
            long currTime = System.currentTimeMillis();
            Bukkit.getScheduler().cancelTask(taskID);
            double elapsedHours = (currTime - startedTime) / 3600000.0;
            timer = timer - elapsedHours;
            active = false;
        }
    }
    
    public void runTimer() {
        taskID = Bukkit.getScheduler().runTaskLater(MasterInventory.getPlugin(), () -> {
            sendMessage();
            removeTimer();
        }, (long) (this.timer * 72000)).getTaskId();
        active = true;
        startedTime = System.currentTimeMillis();
    }

    public boolean sendMessage() {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player.isOnline()) {
            player.sendMessage(ChatColor.GRAY + "-----------------------------------");
            player.sendMessage(ChatColor.AQUA + "Reminder: " + ChatColor.GREEN + this.name);
            player.sendMessage(ChatColor.GRAY + this.description);
            player.sendMessage(ChatColor.GRAY + "-----------------------------------");
            return true;
        }
        return false;
    }
    
    public boolean put() {
        if (!Timer.timers.containsKey(playerUUID)) {
                Timer.timers.put(playerUUID, new HashMap<>());
                Timer.timers.get(playerUUID).put(name, this);
        } else {
            if (Timer.timers.get(playerUUID).containsKey(name)) {
                return false;
            }
            Timer.timers.get(playerUUID).put(name, this);
        }
        
        if (this.timer == -1) {
            return true;
        }
        runTimer();

        return true;
    }

    public static void saveReminders(File dataFolder) {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File dataFile = new File(dataFolder, ".reminders.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        try (FileWriter writer = new FileWriter(dataFile)) {
            gson.toJson(timers, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void loadReminders(File dataFolder) {
        File dataFile = new File(dataFolder, ".reminders.json");
        if (!dataFile.exists()) {
            return;
        }

        Gson gson = new Gson();
        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<UUID, Map<String, Timer>>>() {
            }.getType();
            Map<UUID, Map<String, Timer>> loadedTimers = gson.fromJson(reader, type);
            if (loadedTimers != null) {
                timers = loadedTimers;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static void pauseTimers() {
        for (Map.Entry<UUID, Map<String, Timer>> outerEntry : Timer.timers.entrySet()) {
            for (Map.Entry<String, Timer> innerEntry : outerEntry.getValue().entrySet()) {
                Timer timer = innerEntry.getValue();

                if (timer.getTimer() != -1.0) {
                    timer.pauseTimer();
                }
            }
        }
    }

    public static void runTimers() {
        for (Map.Entry<UUID, Map<String, Timer>> outerEntry : Timer.timers.entrySet()) {
            for (Map.Entry<String, Timer> innerEntry : outerEntry.getValue().entrySet()) {
                Timer timer = innerEntry.getValue();

                if (timer.getTimer() != -1.0) {
                    Player player = Bukkit.getPlayer(timer.playerUUID);
                    if (player != null && player.isOnline()) {
                        timer.runTimer();
                    }
                }
            }
        }
    }

}
