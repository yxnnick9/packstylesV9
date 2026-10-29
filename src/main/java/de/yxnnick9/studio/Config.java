package de.yxnnick9.studio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Config {
    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("yxnnick9-texturestudio");
    public static final Path BG_DIR = DIR.resolve("backgrounds");
    private static final Path FILE = DIR.resolve("config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static Config I = new Config();

    /** Anteil des Vollbilds: 1.0 = Studio fuellt den ganzen Bildschirm */
    public float scale = 1.0f;
    public float dim = 0.55f;
    /** Deckkraft des Studios (niedrig = Spiel scheint durch) */
    public float opacity = 0.55f;
    /** UI-Sprache: false = English, true = Deutsch */
    public boolean german = false;
    /** Lautstaerke der Sound-Kategorien (1.0 = unveraendert) */
    public float totemVolume = 1.0f;
    public float hitVolume = 1.0f;
    /** Totem-Pop-Groesse: 0 klein, 1 normal, 2 gross */
    public int totemSize = 1;
    /** Groesse des Totems in der Hand (1.0 = Vanilla) */
    public float totemHand = 1.0f;
    /** Gewaehlter Himmel (0..19) oder -1 */
    public int sky = -1;
    /** Eigener Himmel (Datei in config/.../skies), hat Vorrang vor sky */
    public String skyCustom = "";
    /** Eigener Himmel-Renderer (Panorama-Kugel): an/aus, wurde angewendet, Helligkeit, Vanilla-Himmel ausblenden */
    public boolean skyOn = false;
    public boolean skyApplied = false;
    public float skyBrightness = 1.0f;
    public boolean skyHideVanilla = true;
    /** Selbst gemalte Texturen: Schluessel (item/x) -> Dateiname in config/.../custom */
    public Map<String, String> custom = new LinkedHashMap<>();
    public String accent = "#FF5F9E";
    public String accent2 = "#FFB347";
    public String background = "#140B1A";
    public String panel = "#20122B";
    public String text = "#FFF1F7";
    public String backgroundImage = "";
    /** Farbe der Abbau-Risse (destroy_stage) */
    public String breakColor = "#FF3B3B";
    public boolean breakColorEnabled = false;
    /** Fensterposition: 0 Mitte, 1 oben links, 2 oben rechts, 3 unten links, 4 unten rechts */
    public int corner = 0;
    public String headName = "yxnnick9";
    public boolean fetchHead = true;
    /** Items, die in der Liste "Auswahl" erscheinen (Item-IDs ohne "minecraft:") */
    public List<String> items = defaultItems();
    /** Texturschluessel (z.B. item/diamond_sword) -> Dateiname des Packs */
    public Map<String, String> selections = new LinkedHashMap<>();
    /** Kategorie-ID (z.B. hearts) -> Dateiname des Packs */
    public Map<String, String> catSelections = new LinkedHashMap<>();

    public static List<String> defaultItems() {
        return new ArrayList<>(List.of(
                "diamond_sword", "netherite_sword", "iron_sword", "golden_sword", "stone_sword", "wooden_sword",
                "mace", "trident", "bow", "crossbow", "shield", "arrow", "totem_of_undying", "golden_apple",
                "enchanted_golden_apple", "ender_pearl", "experience_bottle", "end_crystal", "obsidian",
                "respawn_anchor", "glowstone", "cobweb", "potion", "splash_potion", "elytra", "firework_rocket",
                "netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots",
                "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots",
                "netherite_pickaxe", "diamond_pickaxe", "netherite_axe", "diamond_axe", "fishing_rod",
                "flint_and_steel", "water_bucket", "lava_bucket", "golden_carrot", "cooked_beef"));
    }

    public static void load() {
        try {
            Files.createDirectories(BG_DIR);
            if (Files.exists(FILE)) {
                Config c = GSON.fromJson(Files.readString(FILE), Config.class);
                if (c != null) I = c;
            }
        } catch (Exception ignored) {
        }
        if (I.selections == null) I.selections = new LinkedHashMap<>();
        if (I.totemHand < 0.3f || I.totemHand > 3f) I.totemHand = 1.0f;
        if (I.skyBrightness < 0.2f || I.skyBrightness > 1.5f) I.skyBrightness = 1.0f;
        if (I.skyCustom == null) I.skyCustom = "";
        if (I.custom == null) I.custom = new LinkedHashMap<>();
        if (I.catSelections == null) I.catSelections = new LinkedHashMap<>();
        if (I.items == null) I.items = defaultItems();
        if (I.scale > 1f || I.scale < 0.3f) I.scale = 1.0f;
        if (I.opacity < 0.1f || I.opacity > 1f) I.opacity = 0.55f;
        if (I.breakColor == null) I.breakColor = "#FF3B3B";
        if (I.accent2 == null) I.accent2 = "#FFB347";
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(DIR);
            Files.writeString(FILE, GSON.toJson(I));
        } catch (Exception ignored) {
        }
    }

    public static int color(String hex) {
        try {
            return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16);
        } catch (Exception e) {
            return 0xFFFF00FF;
        }
    }

    public static boolean validHex(String h) {
        return h != null && h.matches("#?[0-9a-fA-F]{6}");
    }
}
