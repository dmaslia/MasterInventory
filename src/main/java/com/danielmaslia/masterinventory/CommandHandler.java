package com.danielmaslia.masterinventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatColor;

public class CommandHandler implements CommandExecutor, TabCompleter {
    private final InventoryManager inventoryManager;
    private final ChatManager chatManager;
    private final CSVExporter csvExporter;
    private final EventListener eventListener;

    public CommandHandler(InventoryManager inventoryManager, ChatManager chatManager, CSVExporter csvExporter, EventListener eventListener) {
        this.inventoryManager = inventoryManager;
        this.chatManager = chatManager;
        this.csvExporter = csvExporter;
        this.eventListener = eventListener;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<List<String>> remind_suggestions = new ArrayList<>(Arrays.asList(
            Arrays.asList("<reminder_name>"),
            Arrays.asList("[timer_duration]", "<reminder_message>"),
            Arrays.asList("<reminder_message>")
        ));

        List<List<String>> save_suggestions = new ArrayList<>(Arrays.asList(
            Arrays.asList("<x>"),
            Arrays.asList("<y>"),
            Arrays.asList("<z>"),
            Arrays.asList("<description>")
        ));

        if (command.getName().equals("remind") && args.length > 0) {
            int index = Math.min(args.length - 1, remind_suggestions.size() - 1);
            return remind_suggestions.get(index);
        } else if (command.getName().equals("save") && args.length > 0) {
            int index = Math.min(args.length - 1, save_suggestions.size() - 1);
            return save_suggestions.get(index);
        }

        return new ArrayList<>(); 
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (sender instanceof Player && cmd.getName().equals("getinv")) {
            inventoryManager.countInventory();
            sender.sendMessage(ChatColor.GOLD + "-----------------------------------");
            sender.sendMessage(ChatColor.GOLD + "              Inventory Saved           ");
            sender.sendMessage(ChatColor.GOLD + "-----------------------------------");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("chat") && sender instanceof Player) {
            Player player = (Player) sender;
            
            if (chatManager.isInConversation(player.getUniqueId())) {
                sender.sendMessage(ChatColor.RED + "Already having a chat");
                return true;
            }
            if (args.length == 0) {
                inventoryManager.countInventory();
            	Bukkit.broadcastMessage("§bSession started. Type §a\"exit\" §bto end.");
            	Bukkit.broadcastMessage("§b[Chat] §7What's up?");
                chatManager.startConversation();
            } else {
                sender.sendMessage(ChatColor.RED + "Usage: /chat");
                return true;
            }

            return true;
        }
        
        if (cmd.getName().equalsIgnoreCase("save")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                return true;
            }

            Player player = (Player) sender;

            if (args.length < 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /save <description> OR /save <x> <y> <z> <description>");
                return true;
            }

            int x, y, z;
            String description;

            try {
                Integer.parseInt(args[0]);
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /save <x> <y> <z> <description>");
                    return true;
                }
                x = Integer.parseInt(args[0]);
                y = Integer.parseInt(args[1]);
                z = Integer.parseInt(args[2]);
                description = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            } catch (NumberFormatException e) {
                // First arg is not an int, use player's current location
                x = player.getLocation().getBlockX();
                y = player.getLocation().getBlockY();
                z = player.getLocation().getBlockZ();
                description = String.join(" ", args);
            }

            csvExporter.saveCoordToCSV(x, y, z, description, "saved_coords.csv", player);

            return true;
        }
        
        if (cmd.getName().equals("remind") && sender instanceof Player player) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /remind <reminder_name> [timer] <description>");
                return true;
            }
            long dispatchTime;
            String name = args[0];
            String desc;
            try {
                double hours = Double.parseDouble(args[1]);
                dispatchTime = System.currentTimeMillis() + (long)(hours * 3600000);
                desc = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            } catch (NumberFormatException e) {
                dispatchTime = -1;
                desc = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            }
            try {
                new Reminder(player.getUniqueId(), name, desc, dispatchTime);
                player.sendMessage(ChatColor.GRAY + "-----------------------------------");
                player.sendMessage(ChatColor.GREEN + "Reminder Added");
                player.sendMessage(ChatColor.AQUA + "Name: " + ChatColor.GRAY + name);
                player.sendMessage(ChatColor.AQUA + "Duration: " + ChatColor.GRAY
                        + (dispatchTime == -1 ? "On next join" : ((dispatchTime - System.currentTimeMillis()) / 3600000.0) + " hours"));
                player.sendMessage(ChatColor.AQUA + "Description: " + ChatColor.GRAY + desc);
                player.sendMessage(ChatColor.GRAY + "-----------------------------------");
            } catch (IllegalArgumentException e) {
                player.sendMessage(ChatColor.RED + "You already have a reminder with name: " + ChatColor.AQUA + name);
            }

            return true;
        }
        
        if (cmd.getName().equals("add") && sender instanceof Player player) {
            Chunk chunk = player.getLocation().getChunk();
            boolean added = inventoryManager.addChunk(chunk);

            if (added) {
                csvExporter.saveChunkToFile(chunk);
                player.sendMessage(ChatColor.GREEN + "Chunk added: " + ChatColor.GRAY + chunk.getX() + ", " + chunk.getZ());
                inventoryManager.countInventory();
            } else {
                player.sendMessage(ChatColor.YELLOW + "Chunk already exists: " + ChatColor.GRAY + chunk.getX() + ", " + chunk.getZ());
            }
            return true;
        }

        if (cmd.getName().equals("remove") && sender instanceof Player player) {
            Chunk chunk = player.getLocation().getChunk();

            boolean removedFromList = inventoryManager.removeChunk(chunk);
            boolean removedFromFile = csvExporter.removeChunkFromFile(chunk);

            if (removedFromList || removedFromFile) {
                player.sendMessage(ChatColor.RED + "Chunk removed: " + ChatColor.GRAY + chunk.getX() + ", " + chunk.getZ());
                inventoryManager.countInventory();
            } else {
                player.sendMessage(ChatColor.YELLOW + "Chunk not found: " + ChatColor.GRAY + chunk.getX() + ", " + chunk.getZ());
            }
            return true;
        }

        if (cmd.getName().equals("addmobs") && sender instanceof Player player) {
            int count = 0;
            for (World world : Bukkit.getWorlds()) {
                for (LivingEntity entity : world.getLivingEntities()) {
                    if (entity.getCustomName() != null && !(entity instanceof Player)) {
                        eventListener.nameEntity(entity, ChatColor.stripColor(entity.getCustomName()));
                        count++;
                    }
                }
            }
            player.sendMessage(ChatColor.GREEN + "Added " + count + " named mobs to tracking.");
            return true;
        }

        if (cmd.getName().equals("exit") && sender instanceof Player player) {
            String currentWorld = player.getWorld().getName();
            if (eventListener.isMainWorld(currentWorld)) {
                player.sendMessage(ChatColor.RED + "You are already in the main world.");
                return true;
            }
            org.bukkit.Location portalLoc = eventListener.getPortalLocation(currentWorld);
            if (portalLoc == null) {
                org.bukkit.Location firstPortal = eventListener.getFirstPortalLocation();
                if (firstPortal != null) {
                    player.teleport(firstPortal);
                } else {
                    org.bukkit.World mainWorld = org.bukkit.Bukkit.getWorld("world");
                    if (mainWorld != null) {
                        player.teleport(mainWorld.getSpawnLocation());
                    }
                }
                return true;
            }
            player.teleport(portalLoc);
            return true;
        }

        if (cmd.getName().equals("add_world") && sender instanceof Player player) {
            if (!player.isOp()) {
                player.sendMessage(ChatColor.RED + "You must be an operator to use this command.");
                return true;
            }
            if (args.length < 1) {
                player.sendMessage(ChatColor.RED + "Usage: /add_world <world_name> [x y z] [gamemode]");
                return true;
            }
            // Parse from the end: check gamemode, then check coords
            int end = args.length;
            String gameMode = null;
            int[] tpCoords = null;

            // Check if last argument is a valid gamemode
            try {
                org.bukkit.GameMode.valueOf(args[end - 1].toUpperCase());
                gameMode = args[end - 1].toUpperCase();
                end--;
            } catch (IllegalArgumentException ignored) {}

            // Check if the last 3 remaining args are coordinates
            if (end >= 4) {
                try {
                    int tx = Integer.parseInt(args[end - 3]);
                    int ty = Integer.parseInt(args[end - 2]);
                    int tz = Integer.parseInt(args[end - 1]);
                    tpCoords = new int[]{tx, ty, tz};
                    end -= 3;
                } catch (NumberFormatException ignored) {}
            }

            String worldName = String.join(" ", java.util.Arrays.copyOfRange(args, 0, end));
            if (worldName.isEmpty()) {
                player.sendMessage(ChatColor.RED + "Usage: /add_world <world_name> [x y z] [gamemode]");
                return true;
            }
            eventListener.addPendingPortal(player.getUniqueId(), worldName, gameMode, tpCoords);
            String msg = "§aEnter a portal to link it to: §f" + worldName;
            if (tpCoords != null) {
                msg += " §7(tp: " + tpCoords[0] + ", " + tpCoords[1] + ", " + tpCoords[2] + ")";
            }
            if (gameMode != null) {
                msg += " §7(gamemode: " + gameMode.toLowerCase() + ")";
            }
            player.sendMessage(msg);
            return true;
        }

        if (cmd.getName().equals("remove_world") && sender instanceof Player player) {
            if (!player.isOp()) {
                player.sendMessage(ChatColor.RED + "You must be an operator to use this command.");
                return true;
            }
            eventListener.addPendingRemoval(player.getUniqueId());
            player.sendMessage("§aEnter a portal to unlink it.");
            return true;
        }

        if(cmd.getName().equals("p") && sender instanceof Player player) {
            if(!chatManager.isOn()) {
                player.sendMessage(ChatColor.RED + "No chat session active");
            }
            UUID playerId = player.getUniqueId();
            if(chatManager.isInConversation(playerId)) {
                chatManager.removeFromConversation(playerId);
            } else {
                chatManager.addToConversation(playerId);
            }
            return true;

        }

        return false;
    }
}
