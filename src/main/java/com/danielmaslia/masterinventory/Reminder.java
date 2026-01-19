package com.danielmaslia.masterinventory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.md_5.bungee.api.ChatColor;


public class Reminder {
    public static Map<UUID, Map<String, Reminder>> reminders = new HashMap<>();
    public static PriorityQueue<Reminder> reminderQueue = new PriorityQueue<>(
        Comparator.comparingLong(r -> r.dispatchTime)
    );

    private UUID playerUUID;
    private String name;
    private String description;
    private long dispatchTime;

    public Reminder(UUID uuid, String name, String description, long dispatchTime) {
        this.playerUUID = uuid;
        this.name = name;
        this.description = description;
        this.dispatchTime = dispatchTime;
        
        if (!reminders.containsKey(playerUUID)) {
                reminders.put(playerUUID, new HashMap<>());
        } else if (reminders.get(playerUUID).containsKey(name)) {
            throw new IllegalArgumentException(
                    "reminder with that name exists for player " + uuid);
        }

        reminders.get(playerUUID).put(name, this);
        if (dispatchTime != -1) {
            reminderQueue.add(this);
        }
    }
    
    
    public boolean sendMessage() {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.GRAY + "-----------------------------------");
            player.sendMessage(ChatColor.AQUA + "Reminder: " + ChatColor.GREEN + this.name);
            player.sendMessage(ChatColor.GRAY + this.description);
            player.sendMessage(ChatColor.GRAY + "-----------------------------------");
            return true;
        }
        return false;
    }
    
    public static void playerJoin(Player p) {
        if (reminders.containsKey(p.getUniqueId())) {
            reminders.get(p.getUniqueId()).values().removeIf(r -> {
                if (r.dispatchTime == -1) {
                    r.sendMessage();
                    return true;
                }
                return false;
            });

            if (reminders.get(p.getUniqueId()).isEmpty()) {
                reminders.remove(p.getUniqueId());
            }
        }
    }

    public static void checkReminders() {
        long now = System.currentTimeMillis();
        while (!reminderQueue.isEmpty() && reminderQueue.peek().dispatchTime <= now) {
            Reminder r = reminderQueue.poll();
            Player player = Bukkit.getPlayer(r.playerUUID);
            if (player != null && player.isOnline()) {
                r.sendMessage();
                reminders.get(r.playerUUID).remove(r.name);
                if (reminders.get(r.playerUUID).isEmpty()) {
                    reminders.remove(r.playerUUID);
                }
            } else {
                r.dispatchTime = -1;
            }
        }
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
                reminderQueue.clear();
                for (Map<String, Reminder> playerReminders : reminders.values()) {
                    for (Reminder r : playerReminders.values()) {
                        if (r.dispatchTime != -1) {
                            reminderQueue.add(r);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
