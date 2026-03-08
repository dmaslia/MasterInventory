package com.danielmaslia.masterinventory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final Map<String, Map<ItemKey, Integer>> lastScan = new LinkedHashMap<>();
    private final Map<Location, Map<ItemKey, Integer>> containerCounts = new HashMap<>();
    private final Set<Location> dirtyLocations = new HashSet<>();
    private final Set<Location> copperLocations = new HashSet<>();
    private boolean scanDirty = true;

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

    private Location toBlockLocation(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public boolean isTrackedLocation(Location loc) {
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;
        String world = loc.getWorld().getName();
        for (Chunk chunk : chunks) {
            if (chunkX == chunk.getX() && chunkZ == chunk.getZ() && world.equals(chunk.getWorld().getName())) {
                return true;
            }
        }
        return false;
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

    /**
     * One-time full scan on startup. Populates containerCounts from all tracked chunks.
     */
    public void initScan() {
        containerCounts.clear();
        dirtyLocations.clear();
        Set<Integer> seenInventories = new HashSet<>();

        for (Chunk chunk : chunks) {
            if (!chunk.isLoaded()) continue;
            for (BlockState state : chunk.getTileEntities()) {
                if (state instanceof Container container) {
                    if (state.getLocation().getWorld().getName().equals("world_nether")
                        && state.getLocation().getY() < 120) {
                        continue;
                    }
                    Inventory inv = container.getInventory();
                    int identity = System.identityHashCode(inv);
                    if (!seenInventories.add(identity)) continue;

                    Location loc = toBlockLocation(inv.getLocation());
                    if(state.getBlock().getType().equals(Material.COPPER_CHEST)) {
                        copperLocations.add(loc);
                    }
                    Map<ItemKey, Integer> counts = new HashMap<>();
                    processInventory(inv.getContents(), counts, 0, loc);
                    containerCounts.put(loc, counts);
                }
            }
        }
        saveScanResultsCSV();
        Bukkit.getLogger().info("[MasterInventory] Initial scan: " + containerCounts.size() + " containers tracked");
    }

    /**
     * Resolve dirty containers, merge all counts, and write scan_results.csv.
     */
    private void saveScanResultsCSV() {
        for (Location loc : dirtyLocations) {
            if (loc.getBlock().getState() instanceof Container container) {
                Inventory inv = container.getInventory();
                Location invLoc = toBlockLocation(inv.getLocation());
                Map<ItemKey, Integer> counts = new HashMap<>();
                processInventory(inv.getContents(), counts, 0, invLoc);
                containerCounts.put(invLoc, counts);
            } else {
                containerCounts.remove(loc);
            }
        }
        dirtyLocations.retainAll(copperLocations);

        Map<ItemKey, Integer> merged = new HashMap<>();
        for (Map<ItemKey, Integer> counts : containerCounts.values()) {
            counts.forEach((key, amount) -> merged.merge(key, amount, Integer::sum));
        }
        lastScan.put("scan_results", merged);
        csvExporter.saveInvToCSV(merged, "scan_results.csv");
        scanDirty = false;
    }

    /**
     * Re-count a single container and update its entry. Called from InventoryCloseEvent.
     */
    public void updateContainer(Location loc, Inventory inv) {
        Location blockLoc = toBlockLocation(loc);
        if (!isTrackedLocation(blockLoc)) return;
        if (blockLoc.getWorld().getName().equals("world_nether") && blockLoc.getY() < 120) return;

        Map<ItemKey, Integer> counts = new HashMap<>();
        processInventory(inv.getContents(), counts, 0, blockLoc);
        containerCounts.put(blockLoc, counts);
        saveScanResultsCSV();
    }

    /**
     * Remove a container's counts. Called from BlockBreakEvent / explosion events.
     */
    public void removeContainer(Location loc) {
        Location blockLoc = toBlockLocation(loc);
        if (containerCounts.remove(blockLoc) != null) {
            saveScanResultsCSV();
        }
    }

    /**
     * Mark a container as dirty for lazy re-count. Called from InventoryMoveItemEvent
     * to avoid re-counting on every hopper tick.
     */
    public void markDirty(Location loc) {
        Location blockLoc = toBlockLocation(loc);
        if (isTrackedLocation(blockLoc)) {
            dirtyLocations.add(blockLoc);
            scanDirty = true;
        }
    }

    /**
     * Scan a single newly-added chunk.
     */
    public void scanChunk(Chunk chunk) {
        Set<Integer> seenInventories = new HashSet<>();
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Container container) {
                if (state.getLocation().getWorld().getName().equals("world_nether")
                    && state.getLocation().getY() < 120) continue;

                Inventory inv = container.getInventory();
                int identity = System.identityHashCode(inv);
                if (!seenInventories.add(identity)) continue;

                Location loc = toBlockLocation(inv.getLocation());
                Map<ItemKey, Integer> counts = new HashMap<>();
                processInventory(inv.getContents(), counts, 0, loc);
                containerCounts.put(loc, counts);
            }
        }
        saveScanResultsCSV();
    }

    /**
     * Remove all tracked containers in a chunk.
     */
    public void unscanChunk(Chunk chunk) {
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        String world = chunk.getWorld().getName();
        containerCounts.entrySet().removeIf(entry -> {
            Location loc = entry.getKey();
            return (loc.getBlockX() >> 4) == chunkX
                && (loc.getBlockZ() >> 4) == chunkZ
                && loc.getWorld().getName().equals(world);
        });
        saveScanResultsCSV();
    }

    /**
     * Resolve dirty containers and write CSV. Called from join/quit events.
     */
    public void flushDirty() {
        Bukkit.getLogger().info("flushed dirty");
        if (!dirtyLocations.isEmpty() || scanDirty) {
            saveScanResultsCSV();
        }
    }

    public void savePlayerInventory(Player player) {
        Map<ItemKey, Integer> counts = new HashMap<>();

        ItemStack[] combined = Stream.of(
            player.getEnderChest().getContents(),
            player.getInventory().getContents()
        ).flatMap(Arrays::stream).toArray(ItemStack[]::new);

        processInventory(combined, counts, 0, player.getLocation());

        lastScan.put(player.getName(), new HashMap<>(counts));
        csvExporter.saveInvToCSV(counts, player.getName() + ".csv");
    }

    public Map<String, Map<ItemKey, Integer>> getLastScan() {
        flushDirty();
        return lastScan;
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
