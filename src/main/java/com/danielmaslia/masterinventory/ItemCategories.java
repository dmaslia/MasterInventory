package com.danielmaslia.masterinventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemCategories {
    private static final Map<String, List<String>> MATERIAL_TO_CATEGORIES = new HashMap<>();

    static {
        register("Raw Wood and Related",
            "Oak Planks", "Spruce Planks", "Birch Planks", "Jungle Planks", "Acacia Planks",
            "Dark Oak Planks", "Pale Oak Planks", "Cherry Planks", "Stripped Oak Wood",
            "Stripped Cherry Log", "Oak Slab", "Spruce Slab", "Birch Slab", "Jungle Slab",
            "Acacia Slab", "Dark Oak Slab", "Pale Oak Slab", "Cherry Slab", "Oak Log",
            "Spruce Log", "Birch Log", "Jungle Log", "Acacia Log", "Dark Oak Log",
            "Pale Oak Log", "Cherry Log", "Dark Oak Wood", "Cherry Wood", "Bamboo Planks",
            "Oak Stairs", "Spruce Stairs", "Birch Stairs", "Jungle Stairs", "Acacia Stairs",
            "Dark Oak Stairs", "Pale Oak Stairs", "Cherry Stairs"
        );
        register("Raw Stone and Related",
            "Gravel", "Diorite", "Sand", "Stone", "Cobblestone Stairs", "Stone Stairs",
            "Granite Stairs", "Andesite Stairs", "Diorite Stairs", "Cobbled Deepslate Stairs",
            "Tuff", "Tuff Stairs", "Tuff Slab", "Granite Slab", "Andesite Slab", "Diorite Slab",
            "Cobbled Deepslate Slab", "Cobblestone Slab", "Stone Slab", "Cobbled Deepslate",
            "Deepslate", "Andesite", "Granite"
        );
        register("Dirt, Grass, and Related",
            "Dirt", "Grass Block", "Mycelium"
        );
        register("Trash Blocks and Other Raw Materials",
            "Netherrack", "Mossy Cobblestone", "White Terracotta", "Dead Fire Coral Block",
            "Dead Bubble Coral Block", "Calcite", "Sandstone", "Obsidian",
            "Dead Tube Coral Block", "Pointed Dripstone"
        );
        register("Seeds, Saplings, and Related",
            "Cherry Sapling", "Oak Sapling", "Spruce Sapling", "Birch Sapling", "Jungle Sapling",
            "Acacia Sapling", "Dark Oak Sapling", "Pale Oak Sapling", "Wheat Seeds",
            "Pumpkin Seeds", "Melon Seeds", "Beetroot Seeds", "Cocoa Beans", "Bamboo",
            "Block of Bamboo", "Glow Berries", "Sugar Cane", "Kelp", "Seagrass"
        );
        register("Leaves and Flowers",
            "Oxeye Daisy", "Wildflowers", "Allium", "Azure Bluet", "Peony", "Sunflower",
            "Red Tulip", "Lilac", "Open Eyeblossom", "Pink Petals", "Lily Pad",
            "Oak Leaves", "Jungle Leaves", "Cherry Leaves", "Pale Oak Leaves", "Spruce Leaves",
            "Birch Leaves", "Dark Oak Leaves", "Leaf Litter", "Firefly Bush", "Orange Tulip",
            "White Tulip", "Pink Tulip", "Poppy", "Dandelion", "Cornflower",
            "Lily of the Valley", "Rose Bush"
        );
        register("Farm Misc.",
            "Carved Pumpkin", "Carrot", "Poisonous Potato", "Brown Mushroom Block",
            "Red Mushroom Block", "Mushroom Stem", "Bone Meal", "Honeycomb", "Bee Nest",
            "Beetroot", "Sweet Berries", "Brown Mushroom", "Red Mushroom", "Sugar"
        );
        register("Polished & Crafted Non-Wood",
            "Stone Bricks", "Chiseled Tuff Bricks", "Mossy Stone Bricks", "Tuff Bricks",
            "Chiseled Tuff", "Stone Brick Stairs", "Polished Granite Slab", "Polished Granite",
            "Polished Diorite", "Granite Wall", "Polished Diorite Stairs", "Polished Diorite Slab",
            "Polished Granite Stairs", "Stone Brick Slab", "Stone Brick Wall",
            "Chiseled Stone Bricks", "Cobblestone Wall", "Smooth Stone", "Smooth Stone Slab",
            "Diorite Wall", "Polished Andesite Slab", "Polished Andesite",
            "Polished Andesite Stairs", "Andesite Wall", "Polished Tuff", "Polished Tuff Slab",
            "Polished Tuff Stairs", "Polished Tuff Wall", "Tuff Wall", "Polished Deepslate",
            "Deepslate Bricks", "Deepslate Brick Wall", "Polished Deepslate Slab",
            "Deepslate Brick Slab", "Cobbled Deepslate Wall", "Polished Deepslate Wall",
            "Polished Deepslate Stairs", "Deepslate Brick Stairs"
        );
        register("Crafted Wood Blocks and Related",
            "Crafting Table", "Chest", "Furnace", "Ladder", "Spruce Door", "Jungle Door",
            "Dark Oak Door", "Oak Door", "Acacia Door", "Birch Door", "Cherry Door",
            "Pale Oak Door", "Oak Trapdoor", "Spruce Trapdoor", "Birch Trapdoor",
            "Jungle Trapdoor", "Acacia Trapdoor", "Dark Oak Trapdoor", "Pale Oak Trapdoor",
            "Cherry Trapdoor", "Oak Fence Gate", "Spruce Fence Gate", "Birch Fence Gate",
            "Jungle Fence Gate", "Acacia Fence Gate", "Dark Oak Fence Gate",
            "Pale Oak Fence Gate", "Cherry Fence Gate", "Oak Fence", "Spruce Fence",
            "Birch Fence", "Jungle Fence", "Acacia Fence", "Dark Oak Fence",
            "Pale Oak Fence", "Cherry Fence"
        );
        register("Building Blocks Misc.",
            "Anvil", "Chipped Anvil", "Damaged Anvil",
            "Bell", "Composter", "Cauldron", "Grindstone", "Lodestone", "Brewing Stand",
            "Dark Oak Boat", "Flower Pot", "Item Frame", "Glow Item Frame", "Name Tag",
            "Red Candle", "Bamboo Raft", "Pale Oak Boat", "Oak Boat", "Oak Sign",
            "Dark Oak Sign", "Cherry Sign", "Iron Bars", "Iron Chain", "Cherry Hanging Sign"
        );
        register("Nether/End",
            "Soul Sand", "Soul Soil", "Nether Brick Fence", "Nether Brick", "Eye of Ender",
            "Crimson Roots", "Polished Blackstone Bricks", "Warped Nylium",
            "Polished Blackstone Brick Stairs", "Nether Brick Wall", "Dragon's Breath",
            "Nether Brick Slab", "Shulker Box", "Warped Roots", "Gilded Blackstone",
            "End Rod", "Purpur Slab", "Shulker Shell", "Chiseled Polished Blackstone",
            "Magma Block", "Glowstone Dust", "Glowstone", "Shroomlight", "Crimson Fungus",
            "Nether Wart Block", "Block of Quartz", "Quartz Stairs", "Quartz Slab",
            "End Stone", "End Stone Bricks", "Polished Basalt", "Warped Fungus", "Nether Wart",
            "Bone Block", "Basalt", "Smooth Basalt", "Nether Bricks", "Crying Obsidian",
            "Blackstone", "Blackstone Wall", "Purpur Pillar", "Weeping Vines",
            "Cracked Polished Blackstone Bricks", "Purpur Block"
        );
        register("Tools",
            "Torch", "Netherite Upgrade", "Arrow of Slowness", "Arrow of Poison",
            "Golden Apple", "Enchanted Golden Apple", "Flint and Steel", "Bow", "Arrow",
            "Diamond Horse Armor", "Water Bucket", "Lava Bucket", "Bucket", "Compass",
            "Glass Bottle", "Saddle", "Shears", "Lead", "Crossbow", "Totem of Undying",
            "Diamond Sword", "Diamond Shovel", "Diamond Pickaxe", "Diamond Axe",
            "Diamond Hoe", "Diamond Helmet", "Diamond Chestplate", "Diamond Leggings",
            "Diamond Boots"
        );
        register("Mob Drops",
            "Bone", "Nautilus Shell", "Raw Salmon", "Slimeball", "Egg", "Brown Egg",
            "Magma Cream", "Blaze Powder", "Spider Eye", "Ghast Tear", "Blaze Rod",
            "Ender Pearl", "Wind Charge", "Breeze Rod", "String", "Feather", "Gunpowder",
            "Armadillo Scute", "Ink Sac", "Glow Ink Sac", "Phantom Membrane", "Leather"
        );
        register("Redstone",
            "Powered Rail", "Detector Rail", "Rail", "Activator Rail", "Fire Charge",
            "Dark Oak Button", "Pale Oak Pressure Plate", "TNT", "Armor Stand",
            "Redstone Lamp", "Pale Oak Boat with Chest", "Minecart with Chest",
            "Minecart with Furnace", "Minecart with Hopper", "Minecart",
            "Bamboo Raft with Chest", "Observer", "Hopper", "Dispenser", "Dropper",
            "Redstone Dust", "Redstone Torch", "Block of Redstone", "Redstone Repeater",
            "Redstone Comparator", "Piston", "Sticky Piston", "Crafter", "Tripwire Hook",
            "Daylight Detector", "Lightning Rod", "Target", "Lever", "Iron Door",
            "Light Weighted Pressure Plate", "Heavy Weighted Pressure Plate",
            "Stone Pressure Plate", "Pale Oak Button", "Minecart with TNT",
            "Iron Trapdoor", "Jungle Pressure Plate"
        );
        register("Mining",
            "Iron Ingot", "Copper Ingot", "Emerald", "Diamond", "Block of Emerald",
            "Netherite Scrap", "Flint", "Coal", "Charcoal", "Block of Coal",
            "Nether Quartz", "Nether Quartz Ore", "Block of Lapis Lazuli", "Lapis Lazuli",
            "Block of Gold", "Block of Diamond", "Block of Iron", "Gold Nugget",
            "Iron Nugget", "Copper Nugget", "Gold Ingot"
        );
        register("Food",
            "Golden Carrot", "Bowl", "Melon Slice", "Honey Bottle", "Cooked Porkchop",
            "Baked Potato", "Bread", "Cooked Cod", "Cooked Salmon", "Cake", "Apple",
            "Cookie", "Pumpkin Pie", "Suspicious Stew", "Cooked Mutton", "Steak",
            "Cooked Chicken"
        );
        register("Fireworks",
            "Firework Rocket", "Elytra"
        );
        register("Ocean",
            "Sponge", "Wet Sponge", "Prismarine", "Prismarine Slab", "Prismarine Brick Slab",
            "Dark Prismarine Slab", "Prismarine Wall", "Prismarine Bricks", "Dark Prismarine",
            "Prismarine Stairs", "Prismarine Brick Stairs", "Dark Prismarine Stairs",
            "Sea Lantern", "Prismarine Crystals", "Prismarine Shards"
        );
        register("Wool/Carpet",
            "Orange Wool", "Magenta Wool", "Light Blue Wool", "Yellow Wool", "Lime Wool",
            "Pink Wool", "Gray Wool", "Light Gray Wool", "Cyan Wool", "Purple Wool",
            "Blue Wool", "Green Wool", "Red Wool", "Black Wool", "White Wool", "Brown Wool",
            "Moss Carpet", "Orange Carpet", "Magenta Carpet", "Light Blue Carpet",
            "Yellow Carpet", "Lime Carpet", "Pink Carpet", "Gray Carpet", "Light Gray Carpet",
            "Cyan Carpet", "Purple Carpet", "Blue Carpet", "Green Carpet", "Red Carpet",
            "Black Carpet", "White Carpet", "Brown Carpet",
            "Pale Moss Block", "Pale Moss Carpet", "Moss Block"
        );
        register("Glass",
            "Tinted Glass", "Glass", "Glass Pane",
            "White Stained Glass", "Orange Stained Glass", "Magenta Stained Glass",
            "Light Blue Stained Glass", "Yellow Stained Glass", "Lime Stained Glass",
            "Pink Stained Glass", "Gray Stained Glass", "Light Gray Stained Glass",
            "Cyan Stained Glass", "Purple Stained Glass", "Blue Stained Glass",
            "Brown Stained Glass", "Green Stained Glass", "Red Stained Glass",
            "Black Stained Glass",
            "White Stained Glass Pane", "Orange Stained Glass Pane", "Magenta Stained Glass Pane",
            "Light Blue Stained Glass Pane", "Yellow Stained Glass Pane", "Lime Stained Glass Pane",
            "Pink Stained Glass Pane", "Gray Stained Glass Pane", "Light Gray Stained Glass Pane",
            "Cyan Stained Glass Pane", "Purple Stained Glass Pane", "Blue Stained Glass Pane",
            "Brown Stained Glass Pane", "Green Stained Glass Pane", "Red Stained Glass Pane",
            "Black Stained Glass Pane"
        );
        register("Dyes",
            "White Dye", "Orange Dye", "Magenta Dye", "Light Blue Dye", "Yellow Dye",
            "Lime Dye", "Pink Dye", "Gray Dye", "Light Gray Dye", "Cyan Dye", "Purple Dye",
            "Blue Dye", "Brown Dye", "Green Dye", "Red Dye", "Black Dye"
        );
        register("Copper Blocks",
            "Copper Bulb", "Copper Trapdoor", "Copper Door", "Copper Chest",
            "Waxed Copper Bulb", "Waxed Copper Trapdoor", "Waxed Copper Door", "Waxed Copper Chest",
            "Exposed Copper Bulb", "Exposed Copper Trapdoor", "Exposed Copper Door", "Exposed Copper Chest",
            "Waxed Exposed Copper Bulb", "Waxed Exposed Copper Trapdoor", "Waxed Exposed Copper Door", "Waxed Exposed Copper Chest",
            "Weathered Copper Bulb", "Weathered Copper Trapdoor", "Weathered Copper Door", "Weathered Copper Chest",
            "Waxed Weathered Copper Bulb", "Waxed Weathered Copper Trapdoor", "Waxed Weathered Copper Door", "Waxed Weathered Copper Chest",
            "Oxidized Copper Bulb", "Oxidized Copper Trapdoor", "Oxidized Copper Door", "Oxidized Copper Chest",
            "Waxed Oxidized Copper Bulb", "Waxed Oxidized Copper Trapdoor", "Waxed Oxidized Copper Door", "Waxed Oxidized Copper Chest",
            "Block of Copper", "Cut Copper", "Cut Copper Stairs", "Cut Copper Slab",
            "Chiseled Copper", "Copper Grate",
            "Waxed Block of Copper", "Waxed Cut Copper", "Waxed Cut Copper Stairs", "Waxed Cut Copper Slab",
            "Waxed Chiseled Copper", "Waxed Copper Grate",
            "Exposed Copper", "Exposed Cut Copper", "Exposed Cut Copper Stairs", "Exposed Cut Copper Slab",
            "Exposed Chiseled Copper", "Exposed Copper Grate",
            "Waxed Exposed Copper", "Waxed Exposed Cut Copper", "Waxed Exposed Cut Copper Stairs", "Waxed Exposed Cut Copper Slab",
            "Waxed Exposed Chiseled Copper", "Waxed Exposed Copper Grate",
            "Weathered Copper", "Weathered Cut Copper", "Weathered Cut Copper Stairs", "Weathered Cut Copper Slab",
            "Weathered Chiseled Copper", "Weather Copper Grate",
            "Waxed Weathered Copper", "Waxed Weathered Cut Copper", "Waxed Weathered Cut Copper Stairs", "Waxed Weathered Cut Copper Slab",
            "Waxed Weathered Chiseled Copper", "Waxed Weather Copper Grate",
            "Oxidized Copper", "Oxidized Cut Copper", "Oxidized Cut Copper Stairs", "Oxidized Cut Copper Slab",
            "Oxidized Chiseled Copper", "Oxidized Copper Grate",
            "Waxed Oxidized Copper", "Waxed Oxidized Cut Copper", "Waxed Oxidized Cut Copper Stairs", "Waxed Oxidized Cut Copper Slab",
            "Waxed Oxidized Chiseled Copper", "Waxed Oxidized Copper Grate"
        );
        register("Beds",
            "Orange Bed", "Magenta Bed", "Light Blue Bed", "Lime Bed", "Pink Bed",
            "Gray Bed", "Light Gray Bed", "Cyan Bed", "Purple Bed", "Blue Bed",
            "Brown Bed", "Green Bed", "Red Bed", "White Bed", "Yellow Bed", "Black Bed"
        );
        register("Villager Area",
            "Iron Ingot", "Stick", "Paper", "Rotten Flesh", "Bone Meal"
        );
    }

    private static void register(String category, String... materials) {
        for (String material : materials) {
            MATERIAL_TO_CATEGORIES.computeIfAbsent(material, k -> new ArrayList<>()).add(category);
        }
    }

    public static List<String> getCategories(String material) {
        return MATERIAL_TO_CATEGORIES.getOrDefault(material, List.of());
    }
}
