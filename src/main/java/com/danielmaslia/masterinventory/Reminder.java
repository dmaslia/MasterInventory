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


public class Reminder {
    public static Map<UUID, Map<String, Reminder>> reminders = new HashMap<>();
    private UUID playerUUID;
    // double representation of number of hours
    // -1 if no timer/on next join
    private double dur;
    private int taskID;
    private long startedTime;
    private boolean active;
    private String name;
    private String description;

    public Reminder(UUID uuid, double dur, String name, String description) {
        this.playerUUID = uuid;
        this.dur = dur;
        this.name = name;
        this.description = description;
        active = false;
    }

    public double getDur() {
        return dur;
    }
    
    public boolean removeTimer() {
        if (reminders.containsKey(playerUUID) && reminders.get(playerUUID).get(name) != null) {
            reminders.get(playerUUID).remove(name);
            if (reminders.get(playerUUID).isEmpty()) {
                reminders.remove(playerUUID);
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
            dur = dur - elapsedHours;
            active = false;
        }
    }
    
    public void runTimer() {
        taskID = Bukkit.getScheduler().runTaskLater(MasterInventory.getPlugin(), () -> {
            sendMessage();
            removeTimer();
        }, (long) (this.dur * 72000)).getTaskId();
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
        if (!Reminder.reminders.containsKey(playerUUID)) {
                Reminder.reminders.put(playerUUID, new HashMap<>());
                Reminder.reminders.get(playerUUID).put(name, this);
        } else {
            if (Reminder.reminders.get(playerUUID).containsKey(name)) {
                return false;
            }
            Reminder.reminders.get(playerUUID).put(name, this);
        }
        
        if (this.dur == -1) {
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
            gson.toJson(reminders, writer);
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
            Type type = new TypeToken<Map<UUID, Map<String, Reminder>>>() {
            }.getType();
            Map<UUID, Map<String, Reminder>> loadedReminders = gson.fromJson(reader, type);
            if (loadedReminders != null) {
                reminders = loadedReminders;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static void pauseTimers() {
        for (Map.Entry<UUID, Map<String, Reminder>> outerEntry : Reminder.reminders.entrySet()) {
            for (Map.Entry<String, Reminder> innerEntry : outerEntry.getValue().entrySet()) {
                Reminder timer = innerEntry.getValue();

                if (timer.getDur() != -1.0) {
                    timer.pauseTimer();
                }
            }
        }
    }

    public static void runTimers() {
        for (Map.Entry<UUID, Map<String, Reminder>> outerEntry : Reminder.reminders.entrySet()) {
            for (Map.Entry<String, Reminder> innerEntry : outerEntry.getValue().entrySet()) {
                Reminder timer = innerEntry.getValue();

                if (timer.getDur() != -1.0) {
                    Player player = Bukkit.getPlayer(timer.playerUUID);
                    if (player != null && player.isOnline()) {
                        timer.runTimer();
                    }
                }
            }
        }
    }

}
