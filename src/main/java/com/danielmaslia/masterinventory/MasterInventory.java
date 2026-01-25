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
    private static final long NEWS_INTERVAL_TICKS = 12000L; // 10 minutes (20 ticks * 60 seconds * 10 minutes)

    private InventoryManager inventoryManager;
    private ChatManager chatManager;
    private EventListener eventListener;
    private CommandHandler commandHandler;
    private ActivityTracker activityTracker;
    private NewsAnchor newsAnchor;

    public static MasterInventory getPlugin() {
        return plugin;
    }

    public ActivityTracker getActivityTracker() {
        return activityTracker;
    }

    public NewsAnchor getNewsAnchor() {
        return newsAnchor;
    }

    @Override
    public void onEnable() {
        plugin = this;

        getLogger().info("-----------------------------------");
        getLogger().info("MasterInventory is Starting Auto Save");
        getLogger().info("-----------------------------------");

        Reminder.loadReminders(getDataFolder());

        CSVExporter csvExporter = new CSVExporter(getDataFolder(), getLogger());
        inventoryManager = new InventoryManager(csvExporter, worldArea, netherArea, endArea);
        chatManager = new ChatManager(this);
        activityTracker = new ActivityTracker(getDataFolder());
        eventListener = new EventListener(this, inventoryManager, chatManager, activityTracker);
        commandHandler = new CommandHandler(inventoryManager, chatManager, csvExporter);

        newsAnchor = new NewsAnchor(this, activityTracker, NEWS_INTERVAL_TICKS);
        newsAnchor.start();

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
        if (newsAnchor != null) {
            newsAnchor.stop();
        }
    }
}
