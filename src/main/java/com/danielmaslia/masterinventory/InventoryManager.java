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
    public static record ItemKey(
        Material material, 
        Integer id
    ) {}

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
    

    private int processInventory(ItemStack[] items, Map<ItemKey, Integer> targetMap, int lastId) {
        for (ItemStack stack : items) {
            if (stack == null || stack.getType().isAir()) continue;

            Integer shulkerId = null;
            boolean isPopulatedShulker = false;
            ShulkerBox sb = null;

            if (stack.getType().name().endsWith("SHULKER_BOX")) {
                if (stack.getItemMeta() instanceof BlockStateMeta meta) {
                    if (meta.getBlockState() instanceof ShulkerBox box) {
                        sb = box;
                        if (!sb.getInventory().isEmpty()) {
                            isPopulatedShulker = true;
                            shulkerId = lastId++;
                        }
                    }
                }
            }

            if (isPopulatedShulker) {
                targetMap.merge(new ItemKey(stack.getType(), shulkerId), 1, Integer::sum);
                for (ItemStack s : sb.getInventory().getContents()) {
                    if (s != null && !s.getType().isAir()) {
                        targetMap.merge(new ItemKey(s.getType(), shulkerId), s.getAmount(), Integer::sum);
                    }
                }
            } else {
                targetMap.merge(new ItemKey(stack.getType(), null), stack.getAmount(), Integer::sum);
            }
        }
        return lastId;
    }

    public void savePlayerInventory(Player player) {
        Map<ItemKey, Integer> counts = new HashMap<>();
        
        ItemStack[] combined = Stream.of(
            player.getEnderChest().getContents(),
            player.getInventory().getContents()
        ).flatMap(Arrays::stream).toArray(ItemStack[]::new);

        processInventory(combined, counts, 0);

        csvExporter.saveInvToCSV(counts, player.getName() + ".csv");
    }

    public void saveChunkContainers() {
        int lastId = 0;
        Map<ItemKey, Integer> counts = new HashMap<>();
        Map<ItemKey, Integer> countsLarge = new HashMap<>();

        for (ChunkCoord coord : chunkCoords) {
            Chunk currChunk = Bukkit.getWorld("world").getChunkAt(coord.x, coord.z);
            for (BlockState state : currChunk.getTileEntities()) {
                if (state instanceof Container container) {
                    Inventory inv = container.getInventory();
                    Map<ItemKey, Integer> targetMap = (inv.getSize() == 54) ? countsLarge : counts;
                    
                    lastId = processInventory(inv.getContents(), targetMap, lastId);
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
