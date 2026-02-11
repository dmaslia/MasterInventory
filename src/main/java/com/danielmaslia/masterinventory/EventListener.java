package com.danielmaslia.masterinventory;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerReplenishTradeEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;

public class EventListener implements Listener {
    private final JavaPlugin plugin;
    private final InventoryManager inventoryManager;
    private final ChatManager chatManager;
    private static boolean resetting = false;

    public EventListener(JavaPlugin plugin, InventoryManager inventoryManager, ChatManager chatManager) {
        this.plugin = plugin;
        this.inventoryManager = inventoryManager;
        this.chatManager = chatManager;
    }
    
    public void nameEntity(Entity entity, String name) {
        entity.setCustomName(org.bukkit.ChatColor.AQUA + "" + org.bukkit.ChatColor.BOLD + name);
        entity.setCustomNameVisible(true);
        entity.setMetadata("name", new FixedMetadataValue(plugin, name));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        inventoryManager.savePlayerInventory(p);
        p.sendMessage("§b[Chat] §7Hello, §a" + p.getName()
                + "§7, I am here to help. Type §a\"/chat\" §7to start a session.");
        Reminder.playerJoin(p);

    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        inventoryManager.savePlayerInventory(event.getPlayer());
        chatManager.removeFromConversation(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerNameTag(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        
        ItemStack item = (event.getHand() == EquipmentSlot.HAND)
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        
        if (item.getType() == Material.NAME_TAG && event.getRightClicked() instanceof LivingEntity entity) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                nameEntity(entity, meta.getDisplayName());
            }
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) {
            return;
        }
        
        java.util.List<MerchantRecipe> recipes = new java.util.ArrayList<>(villager.getRecipes());
        boolean modified = false;
        for (MerchantRecipe recipe : recipes) {
            if (recipe.hasExperienceReward()) {
                recipe.setExperienceReward(false);
                modified = true;
            }
        }
        
        if (modified) {
            villager.setRecipes(recipes);
        }
    }

    @EventHandler
    public void onVillagerReplenish(VillagerReplenishTradeEvent event) {
        if (!resetting) {
            event.setCancelled(true);
        }
    }

    public static void resetVillagers() {
        resetting = true;
        try {
            for (World world : Bukkit.getWorlds()) {
                long time = world.getTime();
                if (time < 0 || time > 12000) {
                    continue;
                }

                for (Villager v : world.getEntitiesByClass(Villager.class)) {
                    if (v.getProfession() != Profession.NONE && v.getProfession() != Profession.NITWIT && !v.isSleeping()) {
                        java.util.List<MerchantRecipe> recipes = new java.util.ArrayList<>(v.getRecipes());
                        for (MerchantRecipe recipe : recipes) {
                            recipe.setUses(0);
                            recipe.setExperienceReward(false);
                            recipe.setDemand(0);
                        }
                        v.setRecipes(recipes);

                        Sound workSound = getWorkSound(v.getProfession());
                        if (workSound != null) {
                            world.playSound(v.getLocation(), workSound, 1.0f, 1.0f);
                        }
                    }
                }
            }
        } finally {
            resetting = false;
        }
    }

    private static Sound getWorkSound(Profession profession) {
        if (profession == Profession.ARMORER) return Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE;
        if (profession == Profession.BUTCHER) return Sound.BLOCK_SMOKER_SMOKE;
        if (profession == Profession.CARTOGRAPHER) return Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT;
        if (profession == Profession.CLERIC) return Sound.BLOCK_BREWING_STAND_BREW;
        if (profession == Profession.FARMER) return Sound.ITEM_HOE_TILL;
        if (profession == Profession.FISHERMAN) return Sound.ENTITY_FISHING_BOBBER_SPLASH;
        if (profession == Profession.FLETCHER) return Sound.ITEM_CROSSBOW_LOADING_MIDDLE;
        if (profession == Profession.LEATHERWORKER) return Sound.BLOCK_FIRE_EXTINGUISH;
        if (profession == Profession.LIBRARIAN) return Sound.ITEM_BOOK_PAGE_TURN;
        if (profession == Profession.MASON) return Sound.UI_STONECUTTER_TAKE_RESULT;
        if (profession == Profession.SHEPHERD) return Sound.ENTITY_SHEEP_SHEAR;
        if (profession == Profession.TOOLSMITH) return Sound.BLOCK_ANVIL_USE;
        if (profession == Profession.WEAPONSMITH) return Sound.BLOCK_ANVIL_USE;
        return null;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (chatManager.isInConversation(uuid)) {
            event.setCancelled(true);
            String message = event.getMessage();
            Bukkit.broadcastMessage("§a[" + player.getName() + "] §f " + message);

            if (message.equalsIgnoreCase("exit")) {
                chatManager.endConversation();
                Bukkit.broadcastMessage("§b[Chat] §7Session ended. Memory cleared.");
                return;
            }

            chatManager.pauseTimer();
            chatManager.addToHistory(player.getName() + ": " + message);

            Bukkit.getScheduler().runTask(plugin, () -> {
                inventoryManager.countInventory();
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    String fullHistory = chatManager.getFullHistory();
                    Bukkit.broadcastMessage("§b[Chat] §7One sec...");
                    chatManager.runPythonAI(player, fullHistory);
                });
            });
        }
    }
}
