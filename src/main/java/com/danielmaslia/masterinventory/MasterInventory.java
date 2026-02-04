package com.danielmaslia.masterinventory;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

public final class MasterInventory extends JavaPlugin {
    private static MasterInventory plugin;


    Location worldCenter = new Location(null, -1977, 73, 329);
    InventoryManager.ScanArea worldArea = new InventoryManager.ScanArea(worldCenter, "world", 10);
    Location netherCenter = new Location(null, -257, 128, 42);
    InventoryManager.ScanArea netherArea = new InventoryManager.ScanArea(netherCenter, "world_nether", 20);
    Location endCenter = new Location(null, 2, 61, -2);
    InventoryManager.ScanArea endArea = new InventoryManager.ScanArea(endCenter, "world_the_end", 10);
    private static final long AUTOSAVE_INTERVAL = 12000L;

    private InventoryManager inventoryManager;
    private ChatManager chatManager;
    private EventListener eventListener;
    private CommandHandler commandHandler;

    public static MasterInventory getPlugin() {
        return plugin;
    }

    @Override
    public void onEnable() {
        plugin = this;

        getLogger().info("-----------------------------------");
        getLogger().info("MasterInventory is Starting Auto Save");
        getLogger().info("-----------------------------------");

        Reminder.loadReminders(getDataFolder());
        saveResource("trades.csv", false);

        CSVExporter csvExporter = new CSVExporter(getDataFolder(), getLogger());
        inventoryManager = new InventoryManager(csvExporter, worldArea, netherArea, endArea);
        chatManager = new ChatManager(this);
        eventListener = new EventListener(this, inventoryManager, chatManager);
        commandHandler = new CommandHandler(inventoryManager, chatManager, csvExporter);

        getServer().getPluginManager().registerEvents(eventListener, this);
        getCommand("getinv").setExecutor(commandHandler);
        getCommand("chat").setExecutor(commandHandler);
        getCommand("save").setExecutor(commandHandler);
        getCommand("remind").setExecutor(commandHandler);
        getCommand("add").setExecutor(commandHandler);
        getCommand("remove").setExecutor(commandHandler);
        getCommand("remind").setTabCompleter(commandHandler);
        getCommand("p").setExecutor(commandHandler);
        getCommand("fixvillagers").setExecutor(commandHandler);

        // automatic inventory counting
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            inventoryManager.countInventory();
        }, 0L, AUTOSAVE_INTERVAL);

        // automatic reminder checking 
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            Reminder.checkReminders();
        }, 0L, 20L);

        // automatic villager reset
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            EventListener.resetVillagers();
        }, 0L, 2400L);



    }

    @Override
    public void onDisable() {
        Reminder.saveReminders(getDataFolder());
    }
}
