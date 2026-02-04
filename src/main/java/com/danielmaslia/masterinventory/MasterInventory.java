package com.danielmaslia.masterinventory;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.inventory.MerchantRecipe;
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

    public static void resetVillagers() {
        for(World world : Bukkit.getWorlds()) {
            long time = world.getTime();
            if(time < 0 || time > 12000) {
                continue; 
            }
            

            for(Villager v : world.getEntitiesByClass(Villager.class)) {
                if(v.getProfession() != Villager.Profession.NONE && v.getProfession() != Villager.Profession.NITWIT && !v.isSleeping()) {
                    java.util.List<MerchantRecipe> recipes = new java.util.ArrayList<>(v.getRecipes());
                    for(MerchantRecipe recipe : recipes){
                        recipe.setUses(0);
                        recipe.setExperienceReward(false);
                    }
                    v.setRecipes(recipes);

                    Sound workSound = getWorkSound(v.getProfession());
                    if(workSound != null) {
                        world.playSound(v.getLocation(), workSound, 1.0f, 1.0f);
                    }
                }
            }
        }
    }

    private static org.bukkit.Sound getWorkSound(Villager.Profession profession) {
    return switch (profession) {
        case Villager.Profession.ARMORER:
            return org.bukkit.Sound.BLOCK_ANVIL_USE;
        case BUTCHER -> org.bukkit.Sound.ENTITY_VILLAGER_WORK_BUTCHER;
        case CARTOGRAPHER -> org.bukkit.Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER;
        case CLERIC -> org.bukkit.Sound.ENTITY_VILLAGER_WORK_CLERIC;
        case FARMER -> org.bukkit.Sound.ENTITY_VILLAGER_WORK_FARMER;
        case FISHERMAN -> org.bukkit.Sound.ENTITY_VILLAGER_WORK_FISHERMAN;
        case FLETCHER -> org.bukkit.Sound.ENTITY_VILLAGER_WORK_FLETCHER;
        case LEATHERWORKER -> org.bukkit.Sound.ENTITY_VILLAGER_WORK_LEATHERWORKER;
        case LIBRARIAN -> org.bukkit.Sound.ENTITY_VILLAGER_WORK_LIBRARIAN;
        case MASON -> org.bukkit.Sound.ENTITY_VILLAGER_WORK_MASON;
        case SHEPHERD -> org.bukkit.Sound.ENTITY_VILLAGER_WORK_SHEPHERD;
        case TOOLSMITH, WEAPONSMITH -> org.bukkit.Sound.BLOCK_GRINDSTONE_USE;
        default -> org.bukkit.Sound.ENTITY_VILLAGER_YES; // Generic "happy" sound
    };
}

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
            resetVillagers();
        }, 0L, 2400L);



    }

    @Override
    public void onDisable() {
        Reminder.saveReminders(getDataFolder());
    }
}
