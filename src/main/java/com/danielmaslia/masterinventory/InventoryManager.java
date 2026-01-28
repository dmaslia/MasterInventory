package com.danielmaslia.masterinventory;

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
        Integer id,
        Location loc
    ) {}

    public static record ScanArea(
        Location center,
        String world,
        int radius
    ) {}

    private final CSVExporter csvExporter;
    private List<Chunk> chunks;

    public InventoryManager(CSVExporter csvExporter, ScanArea worldArea, ScanArea netherArea, ScanArea endArea) {
        this.csvExporter = csvExporter;
        chunks = csvExporter.loadChunksFromFile();

        if (worldArea != null) {
            worldArea.center().setWorld(Bukkit.getWorld(worldArea.world()));
            addChunksFromArea(worldArea);
        }

        if (netherArea != null) {
            netherArea.center().setWorld(Bukkit.getWorld(netherArea.world()));
            addChunksFromArea(netherArea);
        }

        if (endArea != null) {
            endArea.center().setWorld(Bukkit.getWorld(endArea.world()));
            addChunksFromArea(endArea);
        }
    }

    private void addChunksFromArea(ScanArea area) {
        Chunk centerChunk = area.center().getChunk();
        for (int dx = -area.radius(); dx <= area.radius(); dx++) {
            for (int dz = -area.radius(); dz <= area.radius(); dz++) {
                chunks.add(area.center().getWorld().getChunkAt(centerChunk.getX() + dx, centerChunk.getZ() + dz));
            }
        }
    }

    private int processInventory(ItemStack[] items, Map<ItemKey, Integer> targetMap, int lastId, Location loc) {
        for (ItemStack stack : items) {
            if (stack == null || stack.getType().isAir()) continue;

            Integer shulkerId = null;
            ShulkerBox shulkerBox = null;

            if (stack.getType().name().endsWith("SHULKER_BOX") &&
                stack.getItemMeta() instanceof BlockStateMeta meta &&
                meta.getBlockState() instanceof ShulkerBox box &&
                !box.getInventory().isEmpty()) {
                
                shulkerBox = box;
                shulkerId = lastId++;
            }

            if (shulkerBox != null) {
                targetMap.merge(new ItemKey(stack.getType(), shulkerId, loc), 1, Integer::sum);
                for (ItemStack s : shulkerBox.getInventory().getContents()) {
                    if (s != null && !s.getType().isAir()) {
                        targetMap.merge(new ItemKey(s.getType(), shulkerId, loc), s.getAmount(), Integer::sum);
                    }
                }
            } else {
                targetMap.merge(new ItemKey(stack.getType(), null, loc), stack.getAmount(), Integer::sum);
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

        processInventory(combined, counts, 0, player.getLocation());

        csvExporter.saveInvToCSV(counts, player.getName() + ".csv");
    }

    public void saveChunkContainers() {
        int lastId = 0;
        Map<ItemKey, Integer> counts = new HashMap<>();
        Map<ItemKey, Integer> countsLarge = new HashMap<>();

        for (Chunk chunk : chunks) {
            for (BlockState state : chunk.getTileEntities()) {
                if (state instanceof Container container) {
                    if(
                        state.getLocation().getWorld().getName().equals("world_nether") && 
                        state.getLocation().getY() < 120
                    ) {
                        continue;
                    }
                    Inventory inv = container.getInventory();
                    Map<ItemKey, Integer> targetMap = (inv.getSize() == 54) ? countsLarge : counts;
                    
                    lastId = processInventory(inv.getContents(), targetMap, lastId, state.getLocation());
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

    public boolean addChunk(Chunk c) {

        for (Chunk chunk : chunks) {

            if (c.getX() == chunk.getX() && c.getZ() == chunk.getZ() && c.getWorld().getName().equals(chunk.getWorld().getName())) {
                return false;
            }
        }
        chunks.add(c);
        return true;
    }

    public boolean removeChunk(Chunk c) {
        return chunks.removeIf(
            coord -> coord.getX() == c.getX() &&
            coord.getZ() == c.getZ() &&
            coord.getWorld().getName().equals(c.getWorld().getName())
        );
    }
}
