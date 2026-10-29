package de.yxnnick9.studio;

import java.util.List;
import java.util.regex.Pattern;

/** Groups of files that are taken together from ONE pack. Paths are relative to assets/minecraft/. */
final class Categories {
    private Categories() {
    }

    static final class Cat {
        final String id, label, labelDe, desc, descDe, icon, sprite, preview, item;
        final Pattern files;    // matched against the path without extension (.png / .png.mcmeta / .ogg)
        final Pattern events;   // optional: sound events in sounds.json
        final Pattern itemPat;  // optional: vanilla item ids -> also matches items/ID.json and models/item/ID*.json
        final boolean cube;     // preview as 3D block (top + side)

        Cat(String id, String label, String labelDe, String desc, String descDe, String icon, String sprite,
            String files, String events, String preview, boolean cube, String item, String itemPat) {
            this.id = id;
            this.label = label;
            this.labelDe = labelDe;
            this.desc = desc;
            this.descDe = descDe;
            this.icon = icon;
            this.sprite = sprite;
            this.files = Pattern.compile(files);
            this.events = events == null ? null : Pattern.compile(events);
            this.preview = preview;
            this.cube = cube;
            this.item = item;
            this.itemPat = itemPat == null ? null : Pattern.compile("(" + itemPat + ")(_.*)?");
        }

        boolean matchesFile(String rel) {
            if (rel.endsWith(".json")) {
                if (itemPat == null) return false;
                String n = null;
                if (rel.startsWith("items/")) n = rel.substring(6, rel.length() - 5);
                else if (rel.startsWith("models/item/")) n = rel.substring(12, rel.length() - 5);
                return n != null && n.indexOf('/') < 0 && itemPat.matcher(n).matches();
            }
            String base;
            if (rel.endsWith(".png.mcmeta")) base = rel.substring(0, rel.length() - 11);
            else if (rel.endsWith(".png") || rel.endsWith(".ogg")) base = rel.substring(0, rel.length() - 4);
            else return false;
            return files.matcher(base).matches();
        }
    }

    private static final String HUD = "textures/gui/sprites/hud/";
    private static final String SP = "textures/gui/sprites/";

    private static Cat c(String id, String en, String de, String dEn, String dDe, String icon, String sprite,
                         String files, String events, String preview, boolean cube, String item, String itemPat) {
        return new Cat(id, en, de, dEn, dDe, icon, sprite, files, events, preview, cube, item, itemPat);
    }

    // Order = apply order (later overrides earlier, e.g. HUD (All) -> Hearts)
    static final List<Cat> ALL = List.of(
            c("hud", "HUD (All)", "HUD (Alles)", "All HUD sprites (hearts, hunger, XP, hotbar ...)", "Alle HUD-Sprites (Herzen, Hunger, XP, Hotbar ...)",
                    "item_frame", SP + "hud/crosshair.png", HUD + ".*", null, HUD + "heart/full", false, null, null),
            c("hearts", "Hearts", "Herzen", "Health bar", "Lebensanzeige",
                    "red_dye", HUD + "heart/full.png", HUD + "heart/.*", null, HUD + "heart/full", false, null, null),
            c("hunger", "Hunger", "Hunger", "Food bar (drumsticks)", "Hungerleiste (Keulen)",
                    "cooked_beef", HUD + "food_full.png", HUD + "food_.*", null, HUD + "food_full", false, null, null),
            c("xp", "XP Bar", "XP-Leiste", "Experience bar and jump bar", "Erfahrungsleiste und Sprungleiste",
                    "experience_bottle", null, HUD + "(experience_bar_.*|jump_bar_.*)", null, HUD + "experience_bar_progress", false, null, null),
            c("hotbar", "Hotbar & Slots", "Hotbar & Slots", "Hotbar, selection, slots (animation via .mcmeta)", "Hotbar, Auswahl, Slots (Animation per .mcmeta)",
                    "chest", SP + "container/slot.png", SP + "(hud/hotbar.*|container/slot.*)", null, HUD + "hotbar", false, null, null),
            c("inventory", "Inventory", "Inventar", "Inventory screen", "Inventar-Fenster",
                    "crafting_table", null, "textures/gui/(container/inventory|sprites/container/inventory/.*|container/creative_inventory/.*)",
                    null, "textures/gui/container/inventory", false, null, null),
            c("sword", "Sword", "Schwert", "All swords", "Alle Schwerter",
                    "diamond_sword", null, "textures/item/[a-z]+_sword(_.*)?", null, "textures/item/diamond_sword", false,
                    "diamond_sword", "[a-z]+_sword"),
            c("tools", "Tools", "Werkzeuge", "Axe, pickaxe, shovel, hoe", "Axt, Spitzhacke, Schaufel, Hacke",
                    "diamond_pickaxe", null, "textures/item/[a-z]+_(axe|pickaxe|shovel|hoe)(_.*)?", null, "textures/item/diamond_pickaxe", false,
                    "diamond_pickaxe", "[a-z]+_(axe|pickaxe|shovel|hoe)"),
            c("spear", "Spear", "Speer", "All spears", "Alle Speere",
                    "diamond_spear", null, "textures/item/[a-z]+_spear(_.*)?", null, "textures/item/diamond_spear", false,
                    "diamond_spear", "[a-z]+_spear"),
            c("mace", "Mace", "Streitkolben", "Mace (3D preview)", "Streitkolben (3D-Vorschau)",
                    "mace", null, "textures/item/mace.*", null, "textures/item/mace", false, "mace", "mace"),
            c("trident", "Trident", "Dreizack", "Item + entity", "Item + Entity",
                    "trident", null, "textures/(item|entity)/trident.*", null, "textures/item/trident", false, "trident", "trident"),
            c("bow", "Bow", "Bogen", "Bow and pulling frames", "Bogen und Spann-Frames",
                    "bow", null, "textures/item/bow(_.*)?", null, "textures/item/bow", false, "bow", "bow"),
            c("arrow", "Arrow", "Pfeil", "Arrow, Spectral Arrow, Tipped Arrow", "Pfeil, Leuchtpfeil, Tränkepfeil",
                    "arrow", null, "textures/item/(arrow|spectral_arrow|tipped_arrow.*)|textures/entity/projectiles/.*arrow.*", null,
                    "textures/item/arrow", false, "arrow", "arrow|spectral_arrow|tipped_arrow"),
            c("crossbow", "Crossbow", "Armbrust", "Crossbow and loading frames", "Armbrust und Lade-Frames",
                    "crossbow", null, "textures/item/crossbow(_.*)?", null, "textures/item/crossbow_standby", false, "crossbow", "crossbow"),
            c("shield", "Shield", "Schild", "Item + held shield (3D preview)", "Item + getragener Schild (3D-Vorschau)",
                    "shield", null, "textures/(item/shield.*|entity/shield.*)", null, "textures/item/shield", false, null, "shield"),
            c("armor", "Armor (Items)", "Rüstung (Items)", "Helmet, chestplate, leggings, boots", "Helm, Brustplatte, Hose, Schuhe",
                    "diamond_chestplate", null, "textures/item/[a-z]+_(helmet|chestplate|leggings|boots)(_.*)?", null,
                    "textures/item/diamond_chestplate", false, "diamond_chestplate", "[a-z]+_(helmet|chestplate|leggings|boots)"),
            c("armorworn", "Armor (Worn)", "Rüstung (getragen)", "How the armor looks on the body", "Wie die Rüstung am Körper aussieht",
                    "netherite_helmet", null, "textures/(entity/equipment/(humanoid|humanoid_leggings)/.*|models/armor/.*)", null,
                    "textures/entity/equipment/humanoid/diamond", false, null, null),
            c("elytra", "Elytra", "Elytren", "Item + wings", "Item + Flügel",
                    "elytra", null, "textures/(item/elytra.*|entity/(equipment/wings/)?elytra.*)", null, "textures/item/elytra", false,
                    "elytra", "elytra"),
            c("totem", "Totem of Undying", "Totem der Unsterblichkeit", "Totem item + pop size", "Totem-Item + Pop-Größe",
                    "totem_of_undying", null, "textures/item/totem_of_undying", null, "textures/item/totem_of_undying", false,
                    "totem_of_undying", "totem_of_undying"),
            c("potions", "Potions", "Tränke", "All potions (splash shown first)", "Alle Tränke (Wurftrank zuerst)",
                    "splash_potion", null, "textures/item/(.*potion.*|glass_bottle)", null, "textures/item/splash_potion", false,
                    "splash_potion", "potion|splash_potion|lingering_potion|glass_bottle"),
            c("gapple", "Golden Apple", "Goldener Apfel", "Golden Apple", "Goldener Apfel",
                    "golden_apple", null, "textures/item/golden_apple", null, "textures/item/golden_apple", false, "golden_apple", "golden_apple"),
            c("egap", "Enchanted Golden Apple", "Verzauberter Goldener Apfel", "Notch apple", "Notch-Apfel",
                    "enchanted_golden_apple", null, "textures/item/enchanted_golden_apple", null, "textures/item/enchanted_golden_apple", false,
                    "enchanted_golden_apple", "enchanted_golden_apple"),
            c("pearl", "Ender Pearl", "Enderperle", "Ender Pearl", "Enderperle",
                    "ender_pearl", null, "textures/item/ender_pearl", null, "textures/item/ender_pearl", false, "ender_pearl", "ender_pearl"),
            c("xpbottle", "Bottle o' Enchanting", "Erfahrungsfläschchen", "Bottle + experience orb", "Fläschchen + Erfahrungskugel",
                    "experience_bottle", null, "textures/(item/experience_bottle|entity/experience_orb)", null, "textures/item/experience_bottle", false,
                    "experience_bottle", "experience_bottle"),
            c("wind", "Wind Charge", "Windladung", "Item + projectile", "Item + Projektil",
                    "wind_charge", null, "textures/.*wind_charge.*", null, "textures/item/wind_charge", false, "wind_charge", "wind_charge"),
            c("buckets", "Water & Lava Bucket", "Wasser- & Lavaeimer", "Buckets", "Eimer",
                    "lava_bucket", null, "textures/item/(water|lava)_bucket", null, "textures/item/lava_bucket", false,
                    "lava_bucket", "water_bucket|lava_bucket"),
            c("crystal", "End Crystal", "Endkristall", "Entity texture (3D preview)", "Entity-Textur (3D-Vorschau)",
                    "end_crystal", null, "textures/(item|entity)/end_crystal.*", null, "textures/item/end_crystal", false, null, "end_crystal"),
            c("obsidian", "Obsidian", "Obsidian", "Obsidian", "Obsidian",
                    "obsidian", null, "textures/block/obsidian", null, "textures/block/obsidian", true, null, null),
            c("crying", "Crying Obsidian", "Weinender Obsidian", "Crying Obsidian", "Weinender Obsidian",
                    "crying_obsidian", null, "textures/block/crying_obsidian", null, "textures/block/crying_obsidian", true, null, null),
            c("cobweb", "Cobweb", "Spinnennetz", "Cobweb", "Spinnennetz",
                    "cobweb", null, "textures/(block|item)/cobweb", null, "textures/block/cobweb", false, null, null),
            c("anchor", "Respawn Anchor", "Wiederbelebungsanker", "Respawn Anchor", "Wiederbelebungsanker",
                    "respawn_anchor", null, "textures/block/respawn_anchor_.*", null, "textures/block/respawn_anchor", true, null, null),
            c("glowstone", "Glowstone", "Leuchtstein", "Glowstone", "Leuchtstein",
                    "glowstone", null, "textures/block/glowstone", null, "textures/block/glowstone", true, null, null),
            c("fire", "Fire", "Feuer", "Fire and Soul Fire", "Feuer und Seelenfeuer",
                    "flint_and_steel", null, "textures/block/(fire_[01]|soul_fire_[01])", null, "textures/block/fire_0", false, null, null),
            c("netherrack", "Netherrack", "Netherrack", "Netherrack", "Netherrack",
                    "netherrack", null, "textures/block/netherrack", null, "textures/block/netherrack", true, null, null),
            c("debris", "Ancient Debris", "Antiker Schutt", "Ancient Debris", "Antiker Schutt",
                    "ancient_debris", null, "textures/block/ancient_debris_.*", null, "textures/block/ancient_debris", true, null, null),
            c("grass", "Grass", "Gras", "Grass Block, grass, grass color", "Grasblock, Gras, Grasfarbe",
                    "grass_block", null, "textures/(block/(grass_block_.*|short_grass|tall_grass_.*|grass)|colormap/grass)", null,
                    "textures/block/grass_block", true, null, null),
            c("deepslate", "Deepslate", "Tiefenschiefer", "All deepslate blocks", "Alle Tiefenschiefer-Blöcke",
                    "deepslate", null, "textures/block/.*deepslate.*", null, "textures/block/deepslate", true, null, null),
            c("glint", "Enchantment Glint", "Verzauberungsglanz", "Enchanted shimmer", "Verzauberungs-Schimmer",
                    "enchanted_book", null, "textures/misc/enchanted_glint_.*", null, "textures/misc/enchanted_glint_item", false, null, null),
            c("crit", "Crit Particles", "Crit-Partikel", "Critical hit + enchanted hit particles", "Kritische-Treffer- und Verzauberungs-Partikel",
                    "iron_axe", "textures/particle/critical_hit.png", "textures/particle/(critical_hit|crit.*|enchanted_hit)", null,
                    "textures/particle/critical_hit", false, null, null),
            c("mining", "Mining Cracks", "Abbau-Risse", "Block breaking animation (color in Design tab)", "Block-Abbau-Animation (Farbe im Design-Tab)",
                    "diamond_pickaxe", null, "textures/block/destroy_stage_.*", null, "textures/block/destroy_stage_5", false, null, null),
            c("totemsound", "Totem Pop Sound", "Totem-Sound", "Totem sound + volume", "Totem-Sound + Lautstärke",
                    "bell", null, "sounds/item/totem/.*", "item\\.totem\\..*", null, false, null, null),
            c("hitsound", "Hit Sounds", "Treffer-Sounds", "Attack / hit sounds + volume", "Angriffs- / Treffersounds + Lautstärke",
                    "note_block", null, "sounds/(entity/player/attack/.*|damage/hit.*)", "entity\\.player\\.attack\\..*", null, false, null, null)
    );
}
