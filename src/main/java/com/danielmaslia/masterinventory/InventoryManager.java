package com.danielmaslia.masterinventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

public class InventoryManager {
    private final CSVExporter csvExporter;
    private List<ChunkCoord> chunkCoords;

    public InventoryManager(CSVExporter csvExporter, int x, int y, int z) {
        this.csvExporter = csvExporter;
        this.chunkCoords = new ArrayList<>();

        // Build initial list from radius
        Chunk centerChunk = new Location(Bukkit.getWorld("world"), x, y, z).getChunk();
        int radius = 10;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                chunkCoords.add(new ChunkCoord(centerChunk.getX() + dx, centerChunk.getZ() + dz));
            }
        }

        // Load additional chunks from file
        List<int[]> loadedChunks = csvExporter.loadChunksFromFile();
        for (int[] chunk : loadedChunks) {
            chunkCoords.add(new ChunkCoord(chunk[0], chunk[1]));
        }
    }

    public static class ChunkCoord {
        public final int x;
        public final int z;

        public ChunkCoord(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }

    public void savePlayerInventory(Player player) {
        Map<Material, Integer> counts = new HashMap<>();
        ItemStack[] playerInv = player.getInventory().getContents();
        ItemStack[] enderInv = player.getEnderChest().getContents();
        ItemStack[] combined = Stream.concat(Arrays.stream(enderInv), Arrays.stream(playerInv))
                                     .toArray(ItemStack[]::new);

        for (ItemStack stack : combined) {
            if (stack != null && !stack.getType().isAir()) {
                Material type = stack.getType();
                if (type.equals(Material.SHULKER_BOX) && stack.getItemMeta() instanceof BlockStateMeta) {
                    BlockStateMeta meta = (BlockStateMeta) stack.getItemMeta();
                    ShulkerBox sb = (ShulkerBox) meta.getBlockState();
                    for (ItemStack s : sb.getInventory()) {
                        if (s != null && !s.getType().isAir()) {
                            counts.merge(s.getType(), s.getAmount(), Integer::sum);
                        }
                    }
                }
                counts.merge(stack.getType(), stack.getAmount(), Integer::sum);
            }
        }
        csvExporter.saveInvToCSV(counts, player.getName() + ".csv");
    }

    public void saveChunkContainers() {
        Map<Material, Integer> counts = new HashMap<>();
        Map<Material, Integer> countsLarge = new HashMap<>();
        Map<Material, Integer> currCounts;

        for (ChunkCoord coord : chunkCoords) {
            Chunk currChunk = Bukkit.getWorld("world").getChunkAt(coord.x, coord.z);
            BlockState[] tileEntities = currChunk.getTileEntities();

            for (BlockState state : tileEntities) {
                if (state instanceof Container) {
                    Container container = (Container) state;
                    Inventory inv = container.getInventory();
                    ItemStack[] items = inv.getContents();
                    currCounts = inv.getSize() == 54 ? countsLarge : counts;

                    for (ItemStack stack : items) {
                        if (stack != null && !stack.getType().isAir()) {
                            currCounts.merge(stack.getType(), stack.getAmount(), Integer::sum);
                            if (stack.getType().equals(Material.SHULKER_BOX) && stack.getItemMeta() instanceof BlockStateMeta) {
                                BlockStateMeta meta = (BlockStateMeta) stack.getItemMeta();
                                ShulkerBox sb = (ShulkerBox) meta.getBlockState();
                                for (ItemStack s : sb.getInventory()) {
                                    if (s != null && !s.getType().isAir()) {
                                        currCounts.merge(s.getType(), s.getAmount(), Integer::sum);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        countsLarge.forEach((material, amount) -> {
            counts.merge(material, amount / 2, Integer::sum);
        });

        csvExporter.saveInvToCSV(counts, "scan_results.csv");
    }

    public void countInventory() {
        saveChunkContainers();

        for (Player p : Bukkit.getOnlinePlayers()) {
            savePlayerInventory(p);
        }
    }

    public boolean addChunk(int chunkX, int chunkZ) {
        for (ChunkCoord coord : chunkCoords) {
            if (coord.x == chunkX && coord.z == chunkZ) {
                return false;
            }
        }
        chunkCoords.add(new ChunkCoord(chunkX, chunkZ));
        return true;
    }

    public boolean removeChunk(int chunkX, int chunkZ) {
        return chunkCoords.removeIf(coord -> coord.x == chunkX && coord.z == chunkZ);
    }
}
