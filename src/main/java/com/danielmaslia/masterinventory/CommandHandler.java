package com.danielmaslia.masterinventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatColor;

public class CommandHandler implements CommandExecutor, TabCompleter {
    private final InventoryManager inventoryManager;
    private final ChatManager chatManager;
    private final CSVExporter csvExporter;

    public CommandHandler(InventoryManager inventoryManager, ChatManager chatManager, CSVExporter csvExporter) {
        this.inventoryManager = inventoryManager;
        this.chatManager = chatManager;
        this.csvExporter = csvExporter;
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
                player.sendMessage(ChatColor.RED + "Usage: /remind <reminder_name> [timer] <description");
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
