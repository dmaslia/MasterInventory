package com.danielmaslia.masterinventory;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

public final class MasterInventory extends JavaPlugin {
    private static MasterInventory plugin;

    private static final int SCAN_X = -1977;
    private static final int SCAN_Y = 73;
    private static final int SCAN_Z = 329;
    private static final int SCAN_RADIUS = 10;
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

        CSVExporter csvExporter = new CSVExporter(getDataFolder(), getLogger());
        Location worldCenter = new Location(Bukkit.getWorld("world"), SCAN_X, SCAN_Y, SCAN_Z);
        InventoryManager.ScanArea worldArea = new InventoryManager.ScanArea(worldCenter, SCAN_RADIUS);
        inventoryManager = new InventoryManager(csvExporter, worldArea, null, null);
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

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            inventoryManager.countInventory();
        }, 0L, AUTOSAVE_INTERVAL);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            Reminder.checkReminders();
        }, 0L, 20L);
    }

    @Override
    public void onDisable() {
        Reminder.saveReminders(getDataFolder());
    }
}
