package com.danielmaslia.masterinventory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.danielmaslia.masterinventory.InventoryManager.ChunkCoord;

public class CSVExporter {
    private final File dataFolder;
    private final Logger logger;

    public CSVExporter(File dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    // saves Location and description to csv as x, y, z, description
    // if csv exists and coordinates exist, overwrite that line
    // if csv exists and coordinates don't exist, append to bottom
    // if csv doesn't exist, create it with header
    public void saveCoordToCSV(int x, int y, int z, String description, String fileName, Player player) {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File file = new File(dataFolder, fileName);
        boolean fileExists = file.exists();
        List<String> lines = new ArrayList<>();
        boolean coordFound = false;
        String coordKey = x + "," + y + "," + z;

        try {
            if (fileExists) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(coordKey + ",")) {
                        lines.add(coordKey + "," + description);
                        coordFound = true;
                    } else {
                        lines.add(line);
                    }
                }
                reader.close();
            }

            FileWriter fw = new FileWriter(file, false);
            PrintWriter pw = new PrintWriter(fw);

            if (!fileExists) {
                pw.println("x,y,z,description");
                pw.println(coordKey + "," + description);
            } else if (!coordFound) {
                for (String line : lines) {
                    pw.println(line);
                }
                pw.println(coordKey + "," + description);
            } else {
                for (String line : lines) {
                    pw.println(line);
                }
            }

            pw.flush();
            pw.close();

            if (coordFound) {
                player.sendMessage(ChatColor.GOLD + "Location updated: " + ChatColor.YELLOW + "(" + x + ", " + y + ", " + z + ") " + ChatColor.GRAY + "- " + ChatColor.WHITE + description);
            } else {
                player.sendMessage(ChatColor.GREEN + "Location saved: " + ChatColor.AQUA + "(" + x + ", " + y + ", " + z + ") " + ChatColor.GRAY + "- " + ChatColor.WHITE + description);
            }

        } catch (IOException e) {
            player.sendMessage(ChatColor.RED + "Could not save CSV file!");
            e.printStackTrace();
        }
    }
    

    // saver for inventory
    public void saveInvToCSV(Map<InventoryManager.ItemKey, Integer> data, String fileName) {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File file = new File(dataFolder, fileName);

        try {
            FileWriter fw = new FileWriter(file, false);
            PrintWriter pw = new PrintWriter(fw);
            pw.println("Inventory for " + fileName);
            pw.println("Material,Count,ID");

            for (Map.Entry<InventoryManager.ItemKey, Integer> entry : data.entrySet()) {
                pw.println(
                    StringUtils.formatEnumString(entry.getKey().material().toString()) + "," +
                    entry.getValue() + "," +
                    entry.getKey().id()
                );
            }

            pw.flush();
            pw.close();

            logger.info("Successfully saved CSV to: " + file.getAbsolutePath());

        } catch (IOException e) {
            logger.severe("Could not save CSV file!");
            e.printStackTrace();
        }
    }

    public void saveChunkToFile(int chunkX, int chunkZ, String world) {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File file = new File(dataFolder, "chunks.txt");

        try {
            FileWriter fw = new FileWriter(file, true);
            PrintWriter pw = new PrintWriter(fw);
            pw.println(chunkX + ", " + chunkZ + ", " + world);
            pw.flush();
            pw.close();

            logger.info("Chunk saved: " + chunkX + ", " + chunkZ + ", " + world);

        } catch (IOException e) {
            logger.severe("Could not save chunk to file!");
            e.printStackTrace();
        }
    }

    public List<ChunkCoord> loadChunksFromFile() {
        List<ChunkCoord> chunks = new ArrayList<>();
        File file = new File(dataFolder, "chunks.txt");

        if (!file.exists()) {
            return chunks;
        }

        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    int x = Integer.parseInt(parts[0].trim());
                    int z = Integer.parseInt(parts[1].trim());
                    World world = Bukkit.getWorld(parts[2].trim());
                    chunks.add(new ChunkCoord(x, z, world));
                }
            }
            reader.close();
            logger.info("Loaded " + chunks.size() + " chunks from file");
        } catch (IOException e) {
            logger.severe("Could not load chunks from file!");
            e.printStackTrace();
        }

        return chunks;
    }

    public boolean removeChunkFromFile(int chunkX, int chunkZ) {
        if (!dataFolder.exists()) {
            return false;
        }

        File file = new File(dataFolder, "chunks.txt");
        if (!file.exists()) {
            return false;
        }

        List<String> lines = new ArrayList<>();
        boolean removed = false;
        String targetLine = chunkX + ", " + chunkZ;

        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.equals(targetLine)) {
                    lines.add(line);
                } else {
                    removed = true;
                }
            }
            reader.close();

            if (removed) {
                FileWriter fw = new FileWriter(file, false);
                PrintWriter pw = new PrintWriter(fw);
                for (String l : lines) {
                    pw.println(l);
                }
                pw.flush();
                pw.close();
                logger.info("Chunk removed: " + chunkX + ", " + chunkZ);
            }

        } catch (IOException e) {
            logger.severe("Could not remove chunk from file!");
            e.printStackTrace();
            return false;
        }

        return removed;
    }
}
