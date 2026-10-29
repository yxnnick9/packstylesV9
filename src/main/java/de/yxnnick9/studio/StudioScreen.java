package de.yxnnick9.studio;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class StudioScreen extends Screen {
    private static final int W = 440, H = 282, MARGIN = 8;

    private record Preset(String name, String accent, String accent2, String bg, String panel, String text) {}
    private record Btn(int x, int y, int w, int h, Runnable action) {}
    private interface Pv { void draw(DrawContext c, int x, int y, int size); }
    private record Card(String title, String sub, boolean selected, Pv pv, Runnable onClick) {}

    private static final Preset[] PRESETS = {
            new Preset("Sunset", "#FF5F9E", "#FFB347", "#140B1A", "#20122B", "#FFF1F7"),
            new Preset("Ocean", "#38BDF8", "#6EE7B7", "#07131B", "#0D1F2B", "#E3F3FA"),
            new Preset("Lime", "#A3E635", "#22D3EE", "#0B1209", "#121C10", "#F0FBE8"),
            new Preset("Clean", "#7C9CFF", "#5EEAD4", "#0F1115", "#171A21", "#E8EAF0"),
            new Preset("Light", "#3B6CFF", "#A855F7", "#F2F4F8", "#FFFFFF", "#14171C"),
            new Preset("Black", "#F5F5F5", "#8A8A93", "#000000", "#0B0B0D", "#F4F4F5"),
            new Preset("Purple", "#C084FC", "#7C3AED", "#0E0618", "#1A0B2E", "#F5EEFF"),
            new Preset("Rose", "#FB7185", "#F9A8D4", "#140A0F", "#22101A", "#FFEFF4"),
    };
    private static final String[] SLOT_EN = {"Accent", "Accent 2", "Background", "Panel", "Text", "Mining cracks"};
    private static final String[] SLOT_DE = {"Akzent", "Akzent 2", "Hintergrund", "Panel", "Text", "Abbau-Risse"};
    private static final String[] SKY_NAMES = {"Violet Nebula", "Pink Dream", "Cyan Rift", "Aurora", "Deep Space", "Crimson Storm",
            "Blue Horizon", "Magenta Core", "Emerald Mist", "Sunset Galaxy", "Ice Crystal", "Royal Purple", "Neon Night",
            "Twilight", "Cosmic Dust", "Orion", "Andromeda", "Pulsar", "Nova", "Eclipse"};
    private static final int[] PALETTE = {0xFF000000, 0xFFFFFFFF, 0xFF9CA3AF, 0xFF4B5563, 0xFFEF4444, 0xFFF97316, 0xFFFACC15,
            0xFF22C55E, 0xFF06B6D4, 0xFF3B82F6, 0xFFA855F7, 0xFFEC4899};

    private final Config cfg = Config.I;
    private final List<Btn> btns = new ArrayList<>();
    private List<PackIndex> packs = new ArrayList<>();
    private final List<Item> all = new ArrayList<>();
    private List<Item> shown = new ArrayList<>();
    private final Map<Item, ItemStack> stacks = new HashMap<>();
    private final Map<Item, String> names = new HashMap<>();
    private final Map<String, ItemStack> catIcons = new HashMap<>();
    private final Set<String> allKeys = new HashSet<>();
    private final Map<String, Tex> previews = new HashMap<>();
    private final Set<String> failed = new HashSet<>();
    private final Map<String, ModelPreview> models = new HashMap<>();
    private final List<String> bgFiles = new ArrayList<>();
    private ItemStack glintSword;

    private Tex bgTex;
    private Item selected;
    private Categories.Cat selCat;
    private int tab, listScroll, optScroll, catScroll, catOptScroll, skyScroll, focus = -1;
    private boolean onlyWithTex = false, showAll = false, initialized, dragging, full = true;
    private String search = "", status = "";
    private float ox, oy, s = 1f, grabX, grabY;
    private int bg, pan, acc, acc2, txt, dimTxt, hc;

    // design tab picker + generic drag state
    private int slot = 0;
    private final float[] pick = {0f, 1f, 1f};
    private int dragKind;
    private boolean dragInit;
    private float dragX, dragY, dragW, dragH, dragOx, dragOy, dragS = 1f;

    // sky studio (image in the sky)
    private BufferedImage skySrc, skyCut;
    private Tex skyCutTex;
    private boolean skyCutOn = true;
    private float skyTol = 0.30f, skySize = 0.8f, skyPosY = 0.5f;
    private int skyFaceSel = 0;
    private boolean skyDirtyTol;
    private final List<String> combos = new ArrayList<>();
    private final Map<String, Tex> comboTex = new HashMap<>();

    // pixel editor
    private int cw = 16, tool = 0;
    private final int[] px = new int[32 * 32];
    private final float[] pen = {0f, 0f, 1f};
    private boolean targetBlock;

    public StudioScreen() {
        super(Text.literal("yxnnick9 Texture Studio"));
    }

    private String t(String en, String de) {
        return cfg.german ? de : en;
    }

    // ---------------------------------------------------------------- init

    @Override
    protected void init() {
        if (initialized) return;
        initialized = true;
        packs = PackIndex.scan(client.getResourcePackDir());
        for (PackIndex p : packs) allKeys.addAll(p.keys);
        for (Item it : Registries.ITEM) if (it != Items.AIR) all.add(it);
        all.sort(Comparator.comparing(this::path));
        refilter();
        selectSlot(0);
        scanBackgrounds();
        loadBackground();
        scanCombos();
        if (packs.isEmpty()) status = t("No packs found in the resourcepacks folder.", "Keine Packs im resourcepacks-Ordner gefunden.");
    }

    private void scanBackgrounds() {
        bgFiles.clear();
        try (Stream<Path> st = Files.list(Config.BG_DIR)) {
            st.map(p -> p.getFileName().toString())
                    .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted().limit(4).forEach(bgFiles::add);
        } catch (Exception ignored) {
        }
    }

    private void loadBackground() {
        Textures.destroy(bgTex);
        bgTex = null;
        String n = cfg.backgroundImage;
        if (n == null || n.isBlank()) return;
        try {
            Path f = Config.BG_DIR.resolve(Path.of(n).getFileName().toString());
            bgTex = Textures.register("bg", Files.readAllBytes(f));
        } catch (Exception e) {
            status = t("Could not load the background image.", "Hintergrundbild konnte nicht geladen werden.");
        }
    }

    // ---------------------------------------------------------------- background image: upload / delete

    private static boolean isPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private void importBackground(Path src) {
        try {
            if (Files.size(src) > 30L * 1024 * 1024) {
                status = t("Image is too large (max 30 MB).", "Bild ist zu groß (max. 30 MB).");
                return;
            }
            byte[] b = Files.readAllBytes(src);
            byte[] png;
            if (isPng(b)) {
                png = b;
            } else {
                BufferedImage im = ImageIO.read(new ByteArrayInputStream(b));
                if (im == null) {
                    status = t("That is not a valid image (PNG/JPG).", "Das ist kein gültiges Bild (PNG/JPG).");
                    return;
                }
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                ImageIO.write(im, "png", bo);
                png = bo.toByteArray();
            }
            String name = src.getFileName().toString().replaceAll("[^A-Za-z0-9_.-]", "_");
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            if (base.isBlank()) base = "background";
            Files.createDirectories(Config.BG_DIR);
            String fn = base + ".png";
            Files.write(Config.BG_DIR.resolve(fn), png);
            cfg.backgroundImage = fn;
            scanBackgrounds();
            loadBackground();
            Config.save();
            status = "";
        } catch (Throwable t) {
            status = t("Could not import the image.", "Bild konnte nicht importiert werden.");
        }
    }

    private void deleteBackground(String fn) {
        try {
            Files.deleteIfExists(Config.BG_DIR.resolve(Path.of(fn).getFileName().toString()));
        } catch (Exception ignored) {
        }
        if (fn.equals(cfg.backgroundImage)) {
            cfg.backgroundImage = "";
            loadBackground();
        }
        scanBackgrounds();
        Config.save();
    }

    private void handleDrop(List<Path> paths) {
        for (Path p : paths) {
            String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
            if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")) {
                if (tab == 3) {
                    importSkyImage(p);
                } else {
                    tab = 4;
                    importBackground(p);
                }
                return;
            }
        }
        status = t("Drop a PNG or JPG image into the window.", "Zieh ein PNG- oder JPG-Bild ins Fenster.");
    }

    // The method name depends on the mappings - both exist (no @Override).
    public void onFilesDropped(List<Path> paths) {
        handleDrop(paths);
    }

    public void filesDragged(List<Path> paths) {
        handleDrop(paths);
    }

    private Path pickImagePath() {
        String res;
        try (MemoryStack st = MemoryStack.stackPush()) {
            PointerBuffer f = st.mallocPointer(3);
            f.put(st.UTF8("*.png")).put(st.UTF8("*.jpg")).put(st.UTF8("*.jpeg")).flip();
            Class<?> k = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
            Method m = k.getMethod("tinyfd_openFileDialog", CharSequence.class, CharSequence.class, PointerBuffer.class,
                    CharSequence.class, boolean.class);
            res = (String) m.invoke(null, "Choose an image", "", f, "Images (PNG/JPG)", false);
        } catch (Throwable t) {
            status = t("File dialog unavailable - drop the image into the window instead.",
                    "Datei-Dialog nicht verfügbar - zieh das Bild einfach ins Fenster.");
            return null;
        }
        return res == null || res.isBlank() ? null : Path.of(res);
    }

    private void pickBackground() {
        Path p = pickImagePath();
        if (p != null) importBackground(p);
    }

    private void pickSkyImage() {
        Path p = pickImagePath();
        if (p != null) importSkyImage(p);
    }

    // ---------------------------------------------------------------- data helpers

    private String path(Item it) {
        return Registries.ITEM.getId(it).getPath();
    }

    private ItemStack stack(Item it) {
        return stacks.computeIfAbsent(it, ItemStack::new);
    }

    private String name(Item it) {
        if (cfg.german) return stack(it).getName().getString();
        return names.computeIfAbsent(it, k -> {
            String p = path(it);
            if (p.equals("experience_bottle")) return "Bottle o' Enchanting";
            StringBuilder sb = new StringBuilder();
            for (String w : p.split("_")) {
                if (w.isEmpty()) continue;
                boolean small = sb.length() > 0 && (w.equals("of") || w.equals("the") || w.equals("and") || w.equals("on"));
                if (sb.length() > 0) sb.append(' ');
                sb.append(small ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1));
            }
            return sb.toString();
        });
    }

    private String catLabel(Categories.Cat c) {
        return cfg.german ? c.labelDe : c.label;
    }

    private String catDesc(Categories.Cat c) {
        return cfg.german ? c.descDe : c.desc;
    }

    private ItemStack iconOf(Categories.Cat cat) {
        return catIcons.computeIfAbsent(cat.id, k -> {
            Identifier id = Identifier.of("minecraft", cat.icon);
            return new ItemStack(Registries.ITEM.containsId(id) ? Registries.ITEM.get(id) : Items.BARRIER);
        });
    }

    private ItemStack glintSword() {
        if (glintSword == null) {
            glintSword = new ItemStack(Items.DIAMOND_SWORD);
            glintSword.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
        }
        return glintSword;
    }

    private List<String> keysFor(Item it) {
        List<String> out = new ArrayList<>();
        String p = path(it);
        if (allKeys.contains("item/" + p)) out.add("item/" + p);
        if (allKeys.contains("block/" + p)) out.add("block/" + p);
        return out;
    }

    private void refilter() {
        String q = search.toLowerCase(Locale.ROOT).trim();
        shown = new ArrayList<>();
        for (Item it : all) {
            if (!showAll && !cfg.items.contains(path(it))) continue;
            if (!q.isEmpty() && !path(it).contains(q) && !name(it).toLowerCase(Locale.ROOT).contains(q)) continue;
            if (onlyWithTex && keysFor(it).isEmpty()) continue;
            shown.add(it);
        }
    }

    // ---------------------------------------------------------------- color helpers

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int alpha(int c, int a) {
        return (a << 24) | (c & 0xFFFFFF);
    }

    private static boolean light(int c) {
        return (((c >> 16) & 255) * 0.299 + ((c >> 8) & 255) * 0.587 + (c & 255) * 0.114) > 150;
    }

    private static int on(int c) {
        return light(c) ? 0xFF101216 : 0xFFFFFFFF;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static int hsv(float h, float s, float v) {
        float r, g, b;
        float hh = (h - (float) Math.floor(h)) * 6f;
        int i = (int) Math.floor(hh);
        float f = hh - i;
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        if (i == 0) { r = v; g = t; b = p; }
        else if (i == 1) { r = q; g = v; b = p; }
        else if (i == 2) { r = p; g = v; b = t; }
        else if (i == 3) { r = p; g = q; b = v; }
        else if (i == 4) { r = t; g = p; b = v; }
        else { r = v; g = p; b = q; }
        return 0xFF000000 | ((int) (r * 255 + 0.5f) << 16) | ((int) (g * 255 + 0.5f) << 8) | (int) (b * 255 + 0.5f);
    }

    private static float[] toHsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h /= 6f;
        if (h < 0) h += 1f;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    private String slotHex(int i) {
        return new String[]{cfg.accent, cfg.accent2, cfg.background, cfg.panel, cfg.text, cfg.breakColor}[i];
    }

    private void selectSlot(int i) {
        slot = i;
        float[] hsv = toHsv(Config.color(slotHex(i)));
        pick[0] = hsv[0];
        pick[1] = hsv[1];
        pick[2] = hsv[2];
    }

    private void applyPick() {
        String h = String.format("#%06X", hsv(pick[0], pick[1], pick[2]) & 0xFFFFFF);
        switch (slot) {
            case 0 -> cfg.accent = h;
            case 1 -> cfg.accent2 = h;
            case 2 -> cfg.background = h;
            case 3 -> cfg.panel = h;
            case 4 -> cfg.text = h;
            default -> {
                cfg.breakColor = h;
                cfg.breakColorEnabled = true;
            }
        }
    }

    // ---------------------------------------------------------------- draw helpers

    private void hgrad(DrawContext c, int x, int y, int w, int h, int c1, int c2) {
        hgradA(c, x, y, w, h, c1, c2, 255);
    }

    private void hgradA(DrawContext c, int x, int y, int w, int h, int c1, int c2, int aa) {
        for (int i = 0; i < w; i += 4) {
            int sw = Math.min(4, w - i);
            c.fill(x + i, y, x + i + sw, y + h, alpha(mix(c1, c2, (float) i / Math.max(1, w)), aa));
        }
    }

    private void rbox(DrawContext c, int x, int y, int w, int h, int col, int r) {
        if (r <= 1) {
            c.fill(x + 1, y, x + w - 1, y + h, col);
            c.fill(x, y + 1, x + 1, y + h - 1, col);
            c.fill(x + w - 1, y + 1, x + w, y + h - 1, col);
        } else {
            c.fill(x + 2, y, x + w - 2, y + 1, col);
            c.fill(x + 1, y + 1, x + w - 1, y + 2, col);
            c.fill(x, y + 2, x + w, y + h - 2, col);
            c.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, col);
            c.fill(x + 2, y + h - 1, x + w - 2, y + h, col);
        }
    }

    private void outline(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x, y, x + w, y + 1, col);
        c.fill(x, y + h - 1, x + w, y + h, col);
        c.fill(x, y, x + 1, y + h, col);
        c.fill(x + w - 1, y, x + w, y + h, col);
    }

    private void click(int x, int y, int w, int h, Runnable a) {
        btns.add(new Btn(x, y, w, h, a));
    }

    private boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void centered(DrawContext c, String label, int x, int y, int w, int h, int col) {
        String l = textRenderer.trimToWidth(label, w - 4);
        c.drawText(textRenderer, l, x + (w - textRenderer.getWidth(l)) / 2, y + (h - 8) / 2 + 1, col, false);
    }

    private void small(DrawContext c, String t, int x, int y, int col, float sc, int boxW) {
        String tt = textRenderer.trimToWidth(t, (int) ((boxW - 4) / sc));
        float w = textRenderer.getWidth(tt) * sc;
        Matrix3x2fStack m = c.getMatrices();
        m.pushMatrix();
        m.translate(x + (boxW - w) / 2f, y);
        m.scale(sc, sc);
        c.drawText(textRenderer, tt, 0, 0, col, false);
        m.popMatrix();
    }

    private void smallLeft(DrawContext c, String t, int x, int y, int col, float sc) {
        Matrix3x2fStack m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(sc, sc);
        c.drawText(textRenderer, t, 0, 0, col, false);
        m.popMatrix();
    }

    private void button(DrawContext c, int x, int y, int w, int h, String label, int mx, int my,
                        boolean active, Runnable a) {
        boolean hov = over(mx, my, x, y, w, h);
        if (active) {
            hgrad(c, x, y, w, h, acc, acc2);
            centered(c, label, x, y, w, h, on(mix(acc, acc2, 0.5f)));
        } else {
            rbox(c, x, y, w, h, hov ? mix(pan, acc, 0.40f) : mix(pan, txt, 0.12f), 1);
            centered(c, label, x, y, w, h, txt);
        }
        click(x, y, w, h, a);
    }

    private void pill(DrawContext c, int x, int y, int w, int h, String label, int mx, int my,
                      boolean active, Runnable a) {
        boolean hov = over(mx, my, x, y, w, h);
        rbox(c, x, y, w, h, active ? 0xF0FFFFFF : alpha(0x000000, hov ? 0x70 : 0x45), 1);
        centered(c, label, x, y, w, h, active ? 0xFF101216 : hc);
        click(x, y, w, h, a);
    }

    private void searchField(DrawContext c, int x, int y, int w, int h) {
        boolean f = focus == 0;
        if (f) hgrad(c, x - 1, y - 1, w + 2, h + 2, acc, acc2);
        else c.fill(x - 1, y - 1, x + w + 1, y + h + 1, mix(pan, txt, 0.18f));
        c.fill(x, y, x + w, y + h, mix(bg, pan, 0.5f));
        String sh = search.isEmpty() && !f ? t("Search items...", "Items suchen...") : search + (f && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        c.drawText(textRenderer, textRenderer.trimToWidth(sh, w - 6), x + 3, y + (h - 8) / 2 + 1,
                search.isEmpty() && !f ? dimTxt : txt, false);
        click(x, y, w, h, () -> focus = 0);
    }

    private void box(DrawContext c, int x, int y, int w, int h) {
        int a = bgTex != null ? 0xB0 : (int) (0x40 + 0xA0 * cfg.opacity);
        rbox(c, x, y, w, h, alpha(pan, a), 2);
    }

    private void slider(DrawContext c, int x, int y, int w, int h, float v, int kind) {
        rbox(c, x, y, w, h, alpha(txt, 0x28), 1);
        int fw = (int) (w * clamp01(v));
        if (fw > 0) hgrad(c, x, y, fw, h, acc, acc2);
        c.fill(x + fw - 2, y - 2, x + fw + 2, y + h + 2, 0xFFFFFFFF);
        click(x - 2, y - 3, w + 4, h + 6, () -> startDrag(kind, x, y, w, h));
    }

    private void startDrag(int kind, float x, float y, float w, float h) {
        dragKind = kind;
        dragInit = false;
        dragX = x;
        dragY = y;
        dragW = w;
        dragH = h;
    }

    private void flag(DrawContext c, int x, int y, int w, int h, boolean de) {
        if (de) {
            c.fill(x, y, x + w, y + h / 3, 0xFF000000);
            c.fill(x, y + h / 3, x + w, y + 2 * h / 3, 0xFFDD0000);
            c.fill(x, y + 2 * h / 3, x + w, y + h, 0xFFFFCE00);
            return;
        }
        float mxx = (w - 1) / 2f, myy = (h - 1) / 2f;
        for (int py = 0; py < h; py++) {
            for (int pxl = 0; pxl < w; pxl++) {
                float d1 = Math.abs(pxl - py * (w - 1f) / (h - 1f)), d2 = Math.abs(pxl - (h - 1 - py) * (w - 1f) / (h - 1f));
                float dd = Math.min(d1, d2);
                int col;
                if (Math.abs(pxl - mxx) <= 0.8f || Math.abs(py - myy) <= 0.8f) col = 0xFFC8102E;
                else if (Math.abs(pxl - mxx) <= 2.2f || Math.abs(py - myy) <= 2.0f) col = 0xFFFFFFFF;
                else if (dd <= 0.8f) col = 0xFFC8102E;
                else if (dd <= 1.8f) col = 0xFFFFFFFF;
                else col = 0xFF012169;
                c.fill(x + pxl, y + py, x + pxl + 1, y + py + 1, col);
            }
        }
    }

    // ---------------------------------------------------------------- textures: loading

    private Tex preview(PackIndex p, String rel) {
        String k = p.id + "|" + rel;
        if (previews.containsKey(k)) return previews.get(k);
        if (failed.contains(k)) return null;
        try {
            byte[] b = p.readAsset("minecraft/" + rel);
            if (b != null) {
                Tex t = Textures.register("preview", b);
                previews.put(k, t);
                return t;
            }
        } catch (Exception ignored) {
        }
        failed.add(k);
        return null;
    }

    private Tex vanilla(String... rels) {
        for (String rel : rels) {
            String k = "vanilla|" + rel;
            if (previews.containsKey(k)) return previews.get(k);
            if (failed.contains(k)) continue;
            try {
                List<Resource> rs = client.getResourceManager().getAllResources(Identifier.of("minecraft", rel));
                if (!rs.isEmpty()) {
                    try (InputStream in = rs.get(0).getInputStream()) {
                        Tex t = Textures.register("vpreview", in.readAllBytes());
                        previews.put(k, t);
                        return t;
                    }
                }
            } catch (Exception ignored) {
            }
            failed.add(k);
        }
        return null;
    }

    private Tex texFor(PackIndex p, String... rels) {
        if (p == null) return vanilla(rels);
        for (String r : rels) if (p.has(r)) return preview(p, r);
        return null;
    }

    private String findIn(PackIndex p, Categories.Cat cat, String contains) {
        for (String f : p.catFiles(cat.id)) {
            if (f.endsWith(".png") && f.contains(contains) && !f.contains("beam")) return f;
        }
        return null;
    }

    private void drawFit(DrawContext c, Tex t, int x, int y, int bw, int bh) {
        int fw = t.w(), fh = t.h();
        if (fh > fw && fh % fw == 0) fh = fw;
        float sc = Math.min((float) bw / fw, (float) bh / fh);
        int dw = Math.max(1, Math.round(fw * sc)), dh = Math.max(1, Math.round(fh * sc));
        c.drawTexture(RenderPipelines.GUI_TEXTURED, t.id(), x + (bw - dw) / 2, y + (bh - dh) / 2, 0f, 0f, dw, dh,
                fw, fh, t.w(), t.h());
    }

    private void bigItem(DrawContext c, ItemStack st, int x, int y, int size) {
        float sc = size / 16f;
        Matrix3x2fStack m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(sc, sc);
        c.drawItem(st, 0, 0);
        m.popMatrix();
    }

    /** Category symbol: real HUD sprite where there is one, otherwise an item icon. */
    private void drawIcon(DrawContext c, Categories.Cat cat, int x, int y, int size) {
        if (cat.sprite != null) {
            Tex t = vanilla(cat.sprite);
            if (t != null) {
                drawFit(c, t, x, y, size, size);
                return;
            }
        }
        bigItem(c, iconOf(cat), x, y, size);
    }

    // ---------------------------------------------------------------- previews

    private ModelPreview model(PackIndex p, String itemId) {
        String key = (p == null ? "vanilla" : p.id) + "|" + itemId;
        if (models.containsKey(key)) return models.get(key);
        ModelPreview mp = ModelPreview.build(p, client.getResourceManager(), itemId);
        models.put(key, mp);
        return mp;
    }

    private String itemFor(PackIndex p, Categories.Cat cat) {
        String pref = cat.item;
        if (pref == null) return null;
        if (p.has("textures/item/" + pref + ".png") || p.has("items/" + pref + ".json") || p.has("models/item/" + pref + ".json"))
            return pref;
        for (String f : p.catFiles(cat.id)) {
            String id = null;
            if (f.startsWith("textures/item/") && f.endsWith(".png")) id = f.substring(14, f.length() - 4);
            else if (f.startsWith("items/") && f.endsWith(".json")) id = f.substring(6, f.length() - 5);
            if (id != null && id.indexOf('/') < 0 && Registries.ITEM.containsId(Identifier.of("minecraft", id))) return id;
        }
        return pref;
    }

    private ModelPreview special(PackIndex p, Categories.Cat cat) {
        if (!cat.id.equals("crystal")) return null;
        String key = "sp|" + (p == null ? "vanilla" : p.id) + "|" + cat.id;
        if (models.containsKey(key)) return models.get(key);
        ModelPreview mp = null;
        try {
            Tex t = texFor(p, "textures/entity/end_crystal/end_crystal.png", "textures/entity/end_crystal.png");
            if (t == null && p != null) {
                String r = findIn(p, cat, "textures/entity/end_crystal");
                if (r != null) t = preview(p, r);
            }
            if (t != null) mp = ModelPreview.crystal(t);
        } catch (Throwable ignored) {
        }
        models.put(key, mp);
        return mp;
    }

    private byte[] vanillaBytes(String... rels) {
        for (String rel : rels) {
            try {
                List<Resource> rs = client.getResourceManager().getAllResources(Identifier.of("minecraft", rel));
                if (!rs.isEmpty()) try (InputStream in = rs.get(0).getInputStream()) {
                    return in.readAllBytes();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private ModelPreview builtinCrystalPreview(BuiltIn.Style st) {
        String key = "bi|" + st.id();
        if (models.containsKey(key)) return models.get(key);
        ModelPreview mp = null;
        try {
            byte[] src = vanillaBytes("textures/entity/end_crystal/end_crystal.png", "textures/entity/end_crystal.png");
            byte[] out = src == null ? null : BuiltIn.crystal(src, st);
            if (out != null) mp = ModelPreview.crystal(Textures.register("bicrystal", out));
        } catch (Throwable ignored) {
        }
        models.put(key, mp);
        return mp;
    }

    private void crystalBg(DrawContext c, int x, int y, int size) {
        c.fillGradient(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF12071F, 0xFF402372);
    }

    private void drawGlint(DrawContext c, Tex g, int x, int y, int size) {
        bigItem(c, glintSword(), x + (size - 30) / 2, y + 3, 30);
        if (g != null) {
            int sx = x + 4, sy = y + size - 15, sw = size - 8, sh = 11;
            int rw = Math.max(1, g.w() / 2);
            int rh = Math.max(1, Math.min(g.h(), rw * sh / sw));
            int range = Math.max(1, g.w() - rw);
            double tt = (System.currentTimeMillis() / 30.0) % (2.0 * range);
            float u = (float) (range - Math.abs(tt - range));
            float v = Math.max(0, (g.h() - rh) / 2f);
            c.drawTexture(RenderPipelines.GUI_TEXTURED, g.id(), sx, sy, u, v, sw, sh, rw, rh, g.w(), g.h());
            c.fill(sx, sy, sx + sw, sy + sh, 0x549B5CFF);
        }
    }

    private void face(DrawContext c, Tex t, float ox0, float oy0, float ax, float ay, float bx, float by, int shade) {
        Matrix3x2fStack m = c.getMatrices();
        m.pushMatrix();
        m.translate(ox0, oy0);
        m.mul(new org.joml.Matrix3x2f(ax / 16f, ay / 16f, bx / 16f, by / 16f, 0f, 0f));
        int r = Math.min(t.w(), t.h());
        c.drawTexture(RenderPipelines.GUI_TEXTURED, t.id(), 0, 0, 0f, 0f, 16, 16, r, r, t.w(), t.h());
        if (shade != 0) c.fill(0, 0, 16, 16, shade);
        m.popMatrix();
    }

    private void drawCube(DrawContext c, Tex top, Tex side, int x, int y, int size, boolean grassTint) {
        float cx = x + size / 2f, hw = size / 2f, q = size / 4f, h = size / 2f;
        face(c, top, cx, y, hw, q, -hw, q, grassTint ? 0x6048B030 : 0);
        face(c, side, cx - hw, y + q, hw, q, 0, h, 0x40000000);
        face(c, side, cx, y + size / 2f, hw, -q, 0, h, 0x78000000);
    }

    private String pickRel(PackIndex p, String... rels) {
        for (String r : rels) if (p.has(r)) return r;
        return null;
    }

    private boolean tryCube(DrawContext c, PackIndex p, String base, int x, int y, int size, boolean grass) {
        String top = pickRel(p, base + "_top.png", base + ".png");
        String side = pickRel(p, base + "_side.png", base + "_side0.png", base + ".png");
        if (top == null || side == null) return false;
        Tex tt = preview(p, top), ts = preview(p, side);
        if (tt == null || ts == null) return false;
        int cs = size - 8;
        drawCube(c, tt, ts, x + 4, y + (size - cs) / 2, cs, grass);
        return true;
    }

    private void drawCatPreview(DrawContext c, PackIndex p, Categories.Cat cat, int x, int y, int size) {
        if (cat.id.equals("glint")) {
            drawGlint(c, texFor(p, "textures/misc/enchanted_glint_item.png"), x, y, size);
            return;
        }
        if (cat.id.equals("crystal")) {
            crystalBg(c, x, y, size);
            ModelPreview sp = special(p, cat);
            if (sp != null) {
                sp.draw(c, x, y, size);
                return;
            }
        }
        if (cat.item != null) {
            String it = itemFor(p, cat);
            ModelPreview mp = it == null ? null : model(p, it);   // only real 3D models (e.g. 3D mace packs)
            if (mp != null) {
                mp.draw(c, x, y, size);
                return;
            }
        }
        if (cat.cube && cat.preview != null && tryCube(c, p, cat.preview, x, y, size, cat.id.equals("grass"))) return;
        String rel;
        switch (cat.id) {   // entity UV sheets look wrong flat -> use the pack's item icon instead
            case "armorworn" -> rel = pickRel(p, "textures/item/diamond_chestplate.png", "textures/item/netherite_chestplate.png",
                    "textures/item/iron_chestplate.png");
            case "shield" -> rel = pickRel(p, "textures/item/shield.png");
            case "crystal" -> rel = pickRel(p, "textures/item/end_crystal.png");
            default -> rel = p.previewPng(cat);
        }
        if (rel != null) {
            Tex t = preview(p, rel);
            if (t != null) {
                drawFit(c, t, x + 3, y + 3, size - 6, size - 6);
                return;
            }
        }
        drawIcon(c, cat, x + 3, y + 3, size - 6);
    }

    private void drawDefaultPreview(DrawContext c, Categories.Cat cat, int x, int y, int size) {
        if (cat.id.equals("glint")) {
            drawGlint(c, vanilla("textures/misc/enchanted_glint_item.png"), x, y, size);
            return;
        }
        if (cat.id.equals("crystal")) {
            crystalBg(c, x, y, size);
            ModelPreview sp = special(null, cat);
            if (sp != null) {
                sp.draw(c, x, y, size);
                return;
            }
        }
        if (cat.item != null) {
            ModelPreview mp = model(null, cat.item);
            if (mp != null) {
                mp.draw(c, x, y, size);
                return;
            }
        }
        drawIcon(c, cat, x + 3, y + 3, size - 6);
    }

    private static boolean cubeBlock(String name) {
        return !name.matches("cobweb|fire|soul_fire|.*grass|fern|.*sapling|.*torch|.*rail|ladder|vine|.*pane.*|.*bars"
                + "|lantern|chain|.*door.*|.*flower.*|.*tulip|.*orchid|.*mushroom|.*fan|kelp.*|seagrass.*|bamboo.*"
                + "|.*crop.*|wheat.*|.*stage.*|.*_top|.*_side.*");
    }

    private void drawKeyPreview(DrawContext c, PackIndex p, String key, int x, int y, int size) {
        if (key.startsWith("item/")) {
            String id = key.substring(5);
            if (Registries.ITEM.containsId(Identifier.of("minecraft", id))) {
                ModelPreview mp = model(p, id);
                if (mp != null) {
                    mp.draw(c, x, y, size);
                    return;
                }
            }
        }
        if (key.startsWith("block/") && cubeBlock(key.substring(6))
                && tryCube(c, p, "textures/" + key, x, y, size, key.contains("grass_block"))) return;
        Tex t = preview(p, "textures/" + key + ".png");
        if (t != null) drawFit(c, t, x + 3, y + 3, size - 6, size - 6);
    }

    // ---------------------------------------------------------------- card grid

    private int drawCards(DrawContext c, int mx, int my, List<Card> cards, int scroll) {
        final int cols = 3, cwid = 82, ch = 76, gx = 5, gy = 3, x0 = 170, y0 = 82, vis = 2;
        int rowsTotal = (cards.size() + cols - 1) / cols;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rowsTotal - vis)));
        for (int r = 0; r < vis; r++) {
            for (int col = 0; col < cols; col++) {
                int idx = (scroll + r) * cols + col;
                if (idx >= cards.size()) break;
                Card cd = cards.get(idx);
                int x = x0 + col * (cwid + gx), y = y0 + r * (ch + gy);
                boolean hov = over(mx, my, x, y, cwid, ch);
                if (cd.selected()) hgrad(c, x, y, cwid, ch, mix(pan, acc, 0.65f), mix(pan, acc2, 0.65f));
                else rbox(c, x, y, cwid, ch, hov ? alpha(acc, 0x45) : alpha(txt, 0x12), 2);
                rbox(c, x + 14, y + 3, 54, 54, alpha(0x000000, 0x55), 1);
                cd.pv().draw(c, x + 14, y + 3, 54);
                small(c, cd.title(), x, y + 60, txt, 0.65f, cwid);
                small(c, cd.sub(), x, y + 68, cd.selected() ? txt : dimTxt, 0.55f, cwid);
                click(x, y, cwid, ch, cd.onClick());
            }
        }
        if (rowsTotal > vis) {
            int th = vis * ch + gy, bh = Math.max(14, th * vis / rowsTotal);
            int by = y0 + (th - bh) * scroll / Math.max(1, rowsTotal - vis);
            c.fill(428, y0, 431, y0 + th, alpha(txt, 0x20));
            hgrad(c, 428, by, 3, bh, acc, acc2);
        }
        return scroll;
    }

    // ---------------------------------------------------------------- window position

    private float[] anchor(int corner, float ww, float wh) {
        return switch (corner) {
            case 1 -> new float[]{MARGIN, MARGIN};
            case 2 -> new float[]{width - ww - MARGIN, MARGIN};
            case 3 -> new float[]{MARGIN, height - wh - MARGIN};
            case 4 -> new float[]{width - ww - MARGIN, height - wh - MARGIN};
            default -> new float[]{(width - ww) / 2f, (height - wh) / 2f};
        };
    }

    private int nearestAnchor(float ww, float wh) {
        float cx = ox + ww / 2, cy = oy + wh / 2;
        int best = 0;
        float bd = Float.MAX_VALUE;
        for (int k = 0; k < 5; k++) {
            float[] a = anchor(k, ww, wh);
            float dx = a[0] + ww / 2 - cx, dy = a[1] + wh / 2 - cy;
            float d = dx * dx + dy * dy;
            if (d < bd) {
                bd = d;
                best = k;
            }
        }
        return best;
    }

    private void drawCover(DrawContext c, int x, int y, int w, int h) {
        if (bgTex == null) return;
        double wa = (double) w / h, ta = (double) bgTex.w() / bgTex.h();
        int rw, rh, u = 0, v = 0;
        if (ta > wa) {
            rh = bgTex.h();
            rw = (int) (rh * wa);
            u = (bgTex.w() - rw) / 2;
        } else {
            rw = bgTex.w();
            rh = (int) (rw / wa);
            v = (bgTex.h() - rh) / 2;
        }
        c.drawTexture(RenderPipelines.GUI_TEXTURED, bgTex.id(), x, y, (float) u, (float) v, w, h, rw, rh,
                bgTex.w(), bgTex.h());
        c.fill(x, y, x + w, y + h, alpha(bg, (int) (clamp01(cfg.dim) * 255)));
    }

    private void drawBackdrop(DrawContext c, int x, int y, int w, int h) {
        if (bgTex != null) {
            drawCover(c, x, y, w, h);
        } else {
            int a = (int) (clamp01(cfg.opacity) * 0.9f * 255);
            c.fillGradient(x, y, x + w, y + h, alpha(bg, a), alpha(mix(bg, acc2, 0.30f), a));
        }
    }

    private int headerAlpha() {
        return 0x80 + (int) (0x7F * clamp01(cfg.opacity));
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (dragKind != 0) {
            if (!dragInit) {
                dragOx = ox;
                dragOy = oy;
                dragS = s;
                dragInit = true;
            }
            long hd = GLFW.glfwGetCurrentContext();
            if (GLFW.glfwGetMouseButton(hd, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
                dragKind = 0;
                if (skyDirtyTol) {
                    skyDirtyTol = false;
                    recut();
                }
                Config.save();
            } else {
                float lx = (mouseX - dragOx) / dragS, ly = (mouseY - dragOy) / dragS;
                float fx = clamp01((lx - dragX) / dragW), fy = clamp01((ly - dragY) / dragH);
                switch (dragKind) {
                    case 1 -> {
                        pick[1] = fx;
                        pick[2] = 1f - fy;
                        applyPick();
                    }
                    case 2 -> {
                        pick[0] = fx;
                        applyPick();
                    }
                    case 3 -> cfg.opacity = 0.1f + 0.9f * fx;
                    case 4 -> cfg.dim = fx;
                    case 5 -> cfg.scale = Math.round((0.4f + 0.6f * fx) * 20) / 20f;
                    case 6 -> {
                        pen[1] = fx;
                        pen[2] = 1f - fy;
                    }
                    case 7 -> pen[0] = fx;
                    case 8 -> paint(fx, fy);
                    case 9 -> cfg.totemVolume = Math.round(fx * 2f * 20) / 20f;
                    case 10 -> cfg.hitVolume = Math.round(fx * 2f * 20) / 20f;
                    case 14 -> cfg.totemHand = Math.round((0.3f + 2.7f * fx) * 20) / 20f;
                    case 15 -> cfg.skyBrightness = Math.round((0.2f + 1.3f * fx) * 20) / 20f;
                    case 11 -> {
                        float nv = Math.round(fx * 0.8f * 20) / 20f;
                        if (nv != skyTol) {
                            skyTol = nv;
                            if (!skyDirtyTol) skyDirtyTol = true;
                        }
                    }
                    case 12 -> skySize = 0.2f + 0.8f * fx;
                    case 13 -> skyPosY = fx;
                    default -> {
                    }
                }
            }
        }

        float fit = Math.min(width / (float) W, height / (float) H);
        float k = Math.max(0.3f, Math.min(1f, cfg.scale));
        s = fit * k;
        full = k >= 0.97f;
        float ww = W * s, wh = H * s;
        bg = Config.color(cfg.background);
        pan = Config.color(cfg.panel);
        acc = Config.color(cfg.accent);
        acc2 = Config.color(cfg.accent2);
        txt = Config.color(cfg.text);
        dimTxt = mix(pan, txt, 0.6f);
        hc = on(mix(acc, acc2, 0.5f));
        btns.clear();

        if (full) {
            dragging = false;
            ox = (width - ww) / 2f;
            oy = (height - wh) / 2f;
            drawBackdrop(ctx, 0, 0, width, height);
            hgradA(ctx, 0, (int) oy, width, (int) Math.ceil(36 * s), acc, acc2, headerAlpha());
            ctx.fill(0, (int) (oy + 36 * s), width, (int) (oy + 38 * s), alpha(0x000000, 0x40));
        } else {
            ctx.fill(0, 0, width, height, alpha(0x000000, (int) (0x30 + 0x60 * cfg.opacity)));
            if (dragging) {
                long handle = GLFW.glfwGetCurrentContext();
                if (GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
                    dragging = false;
                    cfg.corner = nearestAnchor(ww, wh);
                    Config.save();
                } else {
                    ox = Math.max(0, Math.min(width - ww, mouseX - grabX));
                    oy = Math.max(0, Math.min(height - wh, mouseY - grabY));
                    int near = nearestAnchor(ww, wh);
                    for (int i = 0; i < 5; i++) {
                        float[] a = anchor(i, ww, wh);
                        boolean hot = i == near;
                        ctx.fill((int) a[0], (int) a[1], (int) (a[0] + ww), (int) (a[1] + wh), alpha(acc, hot ? 0x40 : 0x14));
                        outline(ctx, (int) a[0], (int) a[1], (int) ww, (int) wh, alpha(acc2, hot ? 0xE0 : 0x40));
                    }
                }
            }
            if (!dragging) {
                float[] a = anchor(cfg.corner, ww, wh);
                ox = a[0];
                oy = a[1];
            }
        }

        int lx = (int) ((mouseX - ox) / s), ly = (int) ((mouseY - oy) / s);
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(ox, oy);
        m.scale(s, s);
        drawWindow(ctx, lx, ly);
        m.popMatrix();
    }

    private void drawWindow(DrawContext c, int mx, int my) {
        if (!full) {
            c.fill(-3, -3, W + 3, H + 3, 0x40000000);
            drawBackdrop(c, 0, 0, W, H);
            hgradA(c, 0, 0, W, 34, acc, acc2, headerAlpha());
            c.fill(0, 34, W, 36, alpha(0x000000, 0x40));
        }

        Tex head = Head.get();
        if (head != null) {
            c.fill(7, 4, 33, 30, alpha(0xFFFFFF, 0xC0));
            c.drawTexture(RenderPipelines.GUI_TEXTURED, head.id(), 8, 5, 0f, 0f, 24, 24, head.w(), head.h(),
                    head.w(), head.h());
        } else {
            rbox(c, 8, 5, 24, 24, alpha(0x000000, 0x50), 1);
            centered(c, "Y", 8, 5, 24, 24, hc);
        }
        c.drawText(textRenderer, Text.literal("yxnnick9").formatted(Formatting.BOLD), 40, 8, hc, false);
        c.drawText(textRenderer, "Texture Studio", 40, 19, alpha(hc, 0xC0), false);

        pill(c, 116, 10, 62, 15, t("Categories", "Kategorien"), mx, my, tab == 0, () -> tab = 0);
        pill(c, 182, 10, 38, 15, "Items", mx, my, tab == 1, () -> tab = 1);
        pill(c, 224, 10, 38, 15, t("Draw", "Malen"), mx, my, tab == 2, () -> tab = 2);
        pill(c, 266, 10, 38, 15, t("Sky", "Himmel"), mx, my, tab == 3, () -> tab = 3);
        pill(c, 308, 10, 44, 15, "Design", mx, my, tab == 4, () -> tab = 4);
        pill(c, 356, 10, 36, 15, t("Apply", "Anwenden"), mx, my, false, this::applyNow);
        pill(c, 396, 10, 12, 15, "-", mx, my, false, () -> zoom(-0.05f));
        pill(c, 410, 10, 12, 15, "+", mx, my, false, () -> zoom(0.05f));

        switch (tab) {
            case 0 -> drawCategories(c, mx, my);
            case 1 -> drawTextures(c, mx, my);
            case 2 -> drawPixel(c, mx, my);
            case 3 -> drawSky(c, mx, my);
            default -> drawDesign(c, mx, my);
        }
        if (tab == 0 || tab == 1 || tab == 3) drawActions(c, mx, my);

        // footer: status + language switch
        if (!status.isEmpty()) {
            c.drawText(textRenderer, textRenderer.trimToWidth(status, W - 80), 10, H - 14, acc2, false);
        }
        int fy = H - 15;
        flag(c, W - 54, fy, 20, 12, true);
        flag(c, W - 30, fy, 20, 12, false);
        outline(c, W - 55, fy - 1, 22, 14, cfg.german ? 0xFFFFFFFF : alpha(0x000000, 0x80));
        outline(c, W - 31, fy - 1, 22, 14, !cfg.german ? 0xFFFFFFFF : alpha(0x000000, 0x80));
        click(W - 55, fy - 1, 22, 14, () -> {
            cfg.german = true;
            names.clear();
            refilter();
            Config.save();
        });
        click(W - 31, fy - 1, 22, 14, () -> {
            cfg.german = false;
            names.clear();
            refilter();
            Config.save();
        });
        if (!full) outline(c, 0, 0, W, H, alpha(acc, 0x90));
    }

    private void zoom(float d) {
        cfg.scale = Math.round(Math.max(0.4f, Math.min(1f, cfg.scale + d)) * 20) / 20f;
        Config.save();
    }

    // ---------------------------------------------------------------- actions

    private void drawActions(DrawContext c, int mx, int my) {
        button(c, 170, 244, 190, 16, t("Apply", "Anwenden"), mx, my, true, this::applyNow);
        button(c, 366, 244, 60, 16, "Reset", mx, my, false, () -> {
            cfg.selections.clear();
            cfg.catSelections.clear();
            status = t("All selections cleared - press Apply.", "Alle Auswahlen entfernt - Anwenden druecken.");
        });
    }

    private void applyNow() {
        try {
            Config.save();
            int n = Applier.apply(client, packs, cfg);
            Config.save();
            status = t(n + " files applied.", n + " Dateien angewendet.");
        } catch (Exception e) {
            status = t("Error: ", "Fehler: ") + e.getMessage();
        }
    }

    // ---------------------------------------------------------------- tab: categories

    private void drawCategories(DrawContext c, int mx, int my) {
        box(c, 8, 42, 150, 220);
        List<Categories.Cat> cats = Categories.ALL;
        int rows = 11;
        catScroll = Math.max(0, Math.min(catScroll, Math.max(0, cats.size() - rows)));
        for (int i = 0; i < rows; i++) {
            int idx = catScroll + i;
            if (idx >= cats.size()) break;
            Categories.Cat cat = cats.get(idx);
            int y = 48 + i * 18;
            boolean sel = cat == selCat;
            boolean hov = over(mx, my, 10, y, 138, 17);
            if (sel) hgrad(c, 10, y, 138, 17, mix(pan, acc, 0.65f), mix(pan, acc2, 0.65f));
            else if (hov) c.fill(10, y, 148, y + 17, alpha(acc, 0x35));
            boolean chosen = cfg.catSelections.containsKey(cat.id);
            c.fill(10, y, 12, y + 17, chosen ? acc2 : alpha(txt, 0x30));
            drawIcon(c, cat, 14, y, 16);
            c.drawText(textRenderer, textRenderer.trimToWidth(catLabel(cat), 108), 34, y + 5, txt, false);
            click(10, y, 138, 17, () -> {
                selCat = cat;
                catOptScroll = 0;
            });
        }
        if (cats.size() > rows) {
            int th = rows * 18 - 1, bh = Math.max(12, th * rows / cats.size());
            int by = 48 + (th - bh) * catScroll / Math.max(1, cats.size() - rows);
            c.fill(153, 48, 156, 48 + th, alpha(txt, 0x20));
            hgrad(c, 153, by, 3, bh, acc, acc2);
        }

        box(c, 164, 42, 268, 220);
        if (selCat == null) {
            c.drawText(textRenderer, t("Choose a category on the left.", "Wähle links eine Kategorie."), 174, 54, txt, false);
            c.drawText(textRenderer, t("A category takes all of its files", "Eine Kategorie übernimmt alle Dateien"), 174, 72, dimTxt, false);
            c.drawText(textRenderer, t("(textures, models, sounds) from ONE pack.", "(Texturen, Modelle, Sounds) aus EINEM Pack."), 174, 84, dimTxt, false);
            c.drawText(textRenderer, t("Packs found: ", "Packs gefunden: ") + packs.size(), 174, 102, dimTxt, false);
            return;
        }
        final Categories.Cat cat = selCat;
        drawIcon(c, cat, 172, 46, 32);
        c.drawText(textRenderer, catLabel(cat), 212, 52, txt, true);
        c.drawText(textRenderer, textRenderer.trimToWidth(catDesc(cat), 150), 212, 64, dimTxt, false);

        // extra settings: totem size / sound volume
        if (cat.id.equals("totem")) {
            c.drawText(textRenderer, t("Hand size  ", "Hand-Größe  ") + Math.round(cfg.totemHand * 100) + "%", 300, 44, txt, false);
            slider(c, 300, 58, 124, 7, (cfg.totemHand - 0.3f) / 2.7f, 14);
            button(c, 300, 67, 40, 11, "100%", mx, my, false, () -> cfg.totemHand = 1.0f);
        } else if (cat.id.equals("totemsound") || cat.id.equals("hitsound")) {
            boolean totem = cat.id.equals("totemsound");
            float v = totem ? cfg.totemVolume : cfg.hitVolume;
            c.drawText(textRenderer, t("Volume ", "Lautstärke ") + Math.round(v * 100) + "%", 300, 44, txt, false);
            slider(c, 300, 58, 124, 7, v / 2f, totem ? 9 : 10);
        }

        String cur = cfg.catSelections.get(cat.id);
        List<Card> cards = new ArrayList<>();
        cards.add(new Card(t("Default", "Standard"), "Minecraft", cur == null,
                (cc, x, y, size) -> drawDefaultPreview(cc, cat, x, y, size),
                () -> {
                    cfg.catSelections.remove(cat.id);
                    status = t("Selection changed - press Apply.", "Auswahl geändert - Anwenden drücken.");
                }));
        if (cat.id.equals("crystal")) {
            for (BuiltIn.Style st : BuiltIn.CRYSTAL) {
                cards.add(new Card(st.label(), t("Built-in", "Eingebaut"), ("builtin:" + st.id()).equals(cur),
                        (cc, x, y, size) -> {
                            crystalBg(cc, x, y, size);
                            ModelPreview mp = builtinCrystalPreview(st);
                            if (mp != null) mp.draw(cc, x, y, size);
                        },
                        () -> {
                            cfg.catSelections.put("crystal", "builtin:" + st.id());
                            status = t("Selection changed - press Apply.", "Auswahl geändert - Anwenden drücken.");
                        }));
            }
        }
        for (PackIndex p : packs) {
            if (p.catFiles(cat.id).isEmpty()) continue;
            int cnt = p.fileCount(cat.id);
            String sub = cnt > 0 ? cnt + t(cnt == 1 ? " file" : " files", cnt == 1 ? " Datei" : " Dateien") : t("Sound events", "Sound-Events");
            cards.add(new Card(p.label, sub, p.id.equals(cur),
                    (cc, x, y, size) -> drawCatPreview(cc, p, cat, x, y, size),
                    () -> {
                        cfg.catSelections.put(cat.id, p.id);
                        status = t("Selection changed - press Apply.", "Auswahl geändert - Anwenden drücken.");
                    }));
        }
        if (cards.size() == 1) {
            c.drawText(textRenderer, t("No pack contains files for this category.", "Kein Pack hat Dateien für diese Kategorie."), 174, 90, dimTxt, false);
            return;
        }
        catOptScroll = drawCards(c, mx, my, cards, catOptScroll);
    }

    // ---------------------------------------------------------------- tab: items

    private void drawTextures(DrawContext c, int mx, int my) {
        box(c, 8, 42, 150, 220);
        searchField(c, 12, 46, 142, 14);
        button(c, 12, 63, 70, 12, showAll ? t("List: All", "Liste: Alle") : t("List: Selected", "Liste: Auswahl"), mx, my, false, () -> {
            showAll = !showAll;
            refilter();
            listScroll = 0;
        });
        button(c, 84, 63, 70, 12, onlyWithTex ? t("Has pack: ON", "Mit Pack: AN") : t("Has pack: OFF", "Mit Pack: AUS"), mx, my, onlyWithTex, () -> {
            onlyWithTex = !onlyWithTex;
            refilter();
            listScroll = 0;
        });

        int rows = 10;
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, shown.size() - rows)));
        for (int i = 0; i < rows; i++) {
            int idx = listScroll + i;
            if (idx >= shown.size()) break;
            Item it = shown.get(idx);
            int y = 79 + i * 18;
            boolean sel = it == selected;
            boolean hov = over(mx, my, 10, y, 138, 17);
            if (sel) hgrad(c, 10, y, 138, 17, mix(pan, acc, 0.65f), mix(pan, acc2, 0.65f));
            else if (hov) c.fill(10, y, 148, y + 17, alpha(acc, 0x35));
            boolean hasTex = !keysFor(it).isEmpty();
            c.fill(10, y, 12, y + 17, hasTex ? acc2 : alpha(txt, 0x30));
            c.drawItem(stack(it), 14, y);
            c.drawText(textRenderer, textRenderer.trimToWidth(name(it), 94), 34, y + 5, hasTex ? txt : dimTxt, false);
            click(10, y, 124, 17, () -> {
                selected = it;
                optScroll = 0;
            });
            boolean inList = cfg.items.contains(path(it));
            int bx = 135;
            boolean bh = over(mx, my, bx, y + 2, 12, 13);
            rbox(c, bx, y + 2, 12, 13, bh ? mix(pan, acc, 0.5f) : alpha(0x000000, 0x40), 1);
            centered(c, inList ? "-" : "+", bx, y + 2, 12, 13, txt);
            click(bx, y + 2, 12, 13, () -> {
                String p = path(it);
                if (inList) cfg.items.remove(p);
                else cfg.items.add(p);
                int keep = listScroll;
                refilter();
                listScroll = keep;
                Config.save();
            });
        }
        if (shown.isEmpty()) {
            c.drawText(textRenderer, t("No items.", "Keine Items."), 16, 84, dimTxt, false);
            if (!showAll) c.drawText(textRenderer, t("'List: All' -> add with +", "'Liste: Alle' -> mit + hinzufügen"), 16, 96, dimTxt, false);
        }
        if (shown.size() > rows) {
            int th = 180, bh = Math.max(12, th * rows / shown.size());
            int by = 79 + (th - bh) * listScroll / Math.max(1, shown.size() - rows);
            c.fill(153, 79, 156, 79 + th, alpha(txt, 0x20));
            hgrad(c, 153, by, 3, bh, acc, acc2);
        }

        box(c, 164, 42, 268, 220);
        if (selected == null) {
            c.drawText(textRenderer, t("Choose an item on the left.", "Wähle links ein Item."), 174, 54, txt, false);
            c.drawText(textRenderer, t("Packs found: ", "Packs gefunden: ") + packs.size(), 174, 68, dimTxt, false);
            c.drawText(textRenderer, t("Use + / - to decide which items", "Mit + / - bestimmst du, welche Items"), 174, 86, dimTxt, false);
            c.drawText(textRenderer, t("appear in the 'Selected' list.", "in der Auswahl-Liste stehen."), 174, 98, dimTxt, false);
            return;
        }
        final Item item = selected;
        bigItem(c, stack(item), 172, 46, 32);
        c.drawText(textRenderer, textRenderer.trimToWidth(name(item), 200), 212, 52, txt, true);
        c.drawText(textRenderer, path(item), 212, 64, dimTxt, false);

        List<Card> cards = new ArrayList<>();
        for (String key : keysFor(item)) {
            String cur = cfg.selections.get(key);
            cards.add(new Card(t("Default", "Standard"), key, cur == null,
                    (cc, x, y, size) -> bigItem(cc, stack(item), x + 3, y + 3, size - 6),
                    () -> {
                        cfg.selections.remove(key);
                        status = t("Selection changed - press Apply.", "Auswahl geändert - Anwenden drücken.");
                    }));
            for (PackIndex p : packs) {
                if (!p.keys.contains(key)) continue;
                cards.add(new Card(p.label, key, p.id.equals(cur),
                        (cc, x, y, size) -> drawKeyPreview(cc, p, key, x, y, size),
                        () -> {
                            cfg.selections.put(key, p.id);
                            status = t("Selection changed - press Apply.", "Auswahl geändert - Anwenden drücken.");
                        }));
            }
        }
        if (cards.isEmpty()) {
            c.drawText(textRenderer, t("No pack has a texture for this item.", "Kein Pack hat eine Textur für dieses Item."), 174, 90, dimTxt, false);
            return;
        }
        optScroll = drawCards(c, mx, my, cards, optScroll);
    }

    // ---------------------------------------------------------------- tab: draw (pixel editor)

    private String targetKey() {
        if (selected == null) return null;
        return (targetBlock ? "block/" : "item/") + path(selected);
    }

    private void paint(float fx, float fy) {
        int gx = Math.min(cw - 1, (int) (fx * cw)), gy = Math.min(cw - 1, (int) (fy * cw));
        int i = gy * cw + gx;
        switch (tool) {
            case 0 -> px[i] = hsv(pen[0], pen[1], pen[2]);
            case 1 -> px[i] = 0;
            case 2 -> {
                floodFill(gx, gy, px[i], hsv(pen[0], pen[1], pen[2]));
                dragKind = 0;
            }
            default -> {
                if ((px[i] >>> 24) != 0) {
                    float[] h = toHsv(px[i] & 0xFFFFFF);
                    pen[0] = h[0];
                    pen[1] = h[1];
                    pen[2] = h[2];
                }
                dragKind = 0;
                tool = 0;
            }
        }
    }

    private void floodFill(int x, int y, int target, int repl) {
        if (target == repl) return;
        Deque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{x, y});
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (p[0] < 0 || p[1] < 0 || p[0] >= cw || p[1] >= cw || px[p[1] * cw + p[0]] != target) continue;
            px[p[1] * cw + p[0]] = repl;
            q.add(new int[]{p[0] + 1, p[1]});
            q.add(new int[]{p[0] - 1, p[1]});
            q.add(new int[]{p[0], p[1] + 1});
            q.add(new int[]{p[0], p[1] - 1});
        }
    }

    private void setSize(int n) {
        cw = n;
        Arrays.fill(px, 0);
    }

    private void loadCurrent() {
        String key = targetKey();
        if (key == null) return;
        try {
            byte[] b = null;
            PackIndex p = null;
            String pid = cfg.selections.get(key);
            for (PackIndex q : packs) if (q.id.equals(pid)) p = q;
            if (p != null) b = p.read(key, ".png");
            if (b == null) {
                List<Resource> rs = client.getResourceManager().getAllResources(Identifier.of("minecraft", "textures/" + key + ".png"));
                if (!rs.isEmpty()) try (InputStream in = rs.get(0).getInputStream()) {
                    b = in.readAllBytes();
                }
            }
            if (b == null) return;
            BufferedImage im = ImageIO.read(new ByteArrayInputStream(b));
            int src = Math.min(im.getWidth(), im.getHeight());
            Arrays.fill(px, 0);
            for (int y = 0; y < cw; y++) for (int x = 0; x < cw; x++) px[y * cw + x] = im.getRGB(x * src / cw, y * src / cw);
        } catch (Throwable ignored) {
            status = t("Could not load that texture.", "Textur konnte nicht geladen werden.");
        }
    }

    private void saveCustom() {
        String key = targetKey();
        if (key == null) {
            status = t("Select an item in the Items tab first.", "Wähle zuerst im Items-Tab ein Item.");
            return;
        }
        try {
            BufferedImage out = new BufferedImage(cw, cw, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < cw; y++) for (int x = 0; x < cw; x++) out.setRGB(x, y, px[y * cw + x]);
            Files.createDirectories(Config.DIR.resolve("custom"));
            String fn = key.replace('/', '_') + ".png";
            ImageIO.write(out, "png", Config.DIR.resolve("custom").resolve(fn).toFile());
            cfg.custom.put(key, fn);
            Config.save();
            status = t("Saved - press Apply to use it.", "Gespeichert - Anwenden drücken.");
        } catch (Throwable e) {
            status = t("Could not save.", "Speichern fehlgeschlagen.");
        }
    }

    private void drawCanvas(DrawContext c, int x, int y, int size) {
        int cell = Math.max(1, size / cw);
        for (int gy = 0; gy < cw; gy++) {
            for (int gx = 0; gx < cw; gx++) {
                int base = ((gx + gy) & 1) == 0 ? 0xFF3A3A44 : 0xFF2C2C34;
                c.fill(x + gx * cell, y + gy * cell, x + (gx + 1) * cell, y + (gy + 1) * cell, base);
                int v = px[gy * cw + gx];
                if ((v >>> 24) != 0) c.fill(x + gx * cell, y + gy * cell, x + (gx + 1) * cell, y + (gy + 1) * cell, v);
            }
        }
    }

    private void drawPixel(DrawContext c, int mx, int my) {
        box(c, 8, 42, 424, 220);
        c.drawText(textRenderer, t("Pixel art - draw your own texture", "Pixel Art - zeichne deine eigene Textur"), 16, 48, txt, true);

        int cx0 = 16, cy0 = 60, cs = 128;
        drawCanvas(c, cx0, cy0, cs);
        int cell = Math.max(1, cs / cw);
        outline(c, cx0 - 1, cy0 - 1, cell * cw + 2, cell * cw + 2, alpha(txt, 0x80));
        click(cx0, cy0, cell * cw, cell * cw, () -> startDrag(8, cx0, cy0, cell * cw, cell * cw));

        String[] tn = {t("Pen", "Stift"), t("Erase", "Radierer"), t("Fill", "Füllen"), t("Pick", "Pipette")};
        for (int i = 0; i < 4; i++) {
            int ti = i;
            button(c, 16 + i * 34, 194, 32, 13, tn[i], mx, my, tool == i, () -> tool = ti);
        }
        button(c, 16, 211, 30, 13, "16", mx, my, cw == 16, () -> setSize(16));
        button(c, 48, 211, 30, 13, "32", mx, my, cw == 32, () -> setSize(32));
        button(c, 80, 211, 36, 13, t("Clear", "Leeren"), mx, my, false, () -> Arrays.fill(px, 0));
        button(c, 120, 211, 38, 13, t("Load", "Laden"), mx, my, false, this::loadCurrent);

        // picker
        int ppx = 170, ppy = 60, pw = 110, ph = 72;
        for (int i = 0; i < pw; i += 2) {
            c.fillGradient(ppx + i, ppy, ppx + Math.min(i + 2, pw), ppy + ph, hsv(pen[0], (float) i / pw, 1f), 0xFF000000);
        }
        outline(c, ppx - 1, ppy - 1, pw + 2, ph + 2, alpha(txt, 0x60));
        int mk = ppx + (int) (pen[1] * pw), mkY = ppy + (int) ((1f - pen[2]) * ph);
        outline(c, mk - 3, mkY - 3, 7, 7, 0xFFFFFFFF);
        outline(c, mk - 2, mkY - 2, 5, 5, 0xFF000000);
        click(ppx, ppy, pw, ph, () -> startDrag(6, ppx, ppy, pw, ph));
        int hy = ppy + ph + 5;
        for (int i = 0; i < pw; i += 2) c.fill(ppx + i, hy, ppx + Math.min(i + 2, pw), hy + 9, hsv((float) i / pw, 1f, 1f));
        outline(c, ppx - 1, hy - 1, pw + 2, 11, alpha(txt, 0x60));
        c.fill(ppx + (int) (pen[0] * pw) - 1, hy - 2, ppx + (int) (pen[0] * pw) + 2, hy + 11, 0xFFFFFFFF);
        click(ppx, hy - 2, pw, 13, () -> startDrag(7, ppx, hy, pw, 9));

        // current color + palette
        c.fill(290, 60, 322, 80, hsv(pen[0], pen[1], pen[2]));
        outline(c, 290, 60, 32, 20, alpha(txt, 0x80));
        for (int i = 0; i < PALETTE.length; i++) {
            int col = PALETTE[i];
            int sx = 170 + (i % 6) * 18, sy = 156 + (i / 6) * 16;
            c.fill(sx, sy, sx + 15, sy + 13, col);
            if (over(mx, my, sx, sy, 15, 13)) outline(c, sx, sy, 15, 13, txt);
            click(sx, sy, 15, 13, () -> {
                float[] h = toHsv(col & 0xFFFFFF);
                pen[0] = h[0];
                pen[1] = h[1];
                pen[2] = h[2];
                tool = 0;
            });
        }

        // live preview
        c.drawText(textRenderer, t("Preview", "Vorschau"), 340, 52, dimTxt, false);
        drawCanvas(c, 340, 62, 64);
        outline(c, 339, 61, Math.max(1, 64 / cw) * cw + 2, Math.max(1, 64 / cw) * cw + 2, alpha(txt, 0x60));

        // target + save
        String key = targetKey();
        c.drawText(textRenderer, key == null ? t("Target: select an item in the Items tab", "Ziel: wähle im Items-Tab ein Item")
                : t("Target: ", "Ziel: ") + key, 170, 194, key == null ? dimTxt : txt, false);
        button(c, 330, 190, 50, 13, targetBlock ? "block/" : "item/", mx, my, false, () -> targetBlock = !targetBlock);
        button(c, 170, 208, 100, 14, t("Save to pack", "Im Pack speichern"), mx, my, true, this::saveCustom);
        button(c, 274, 208, 70, 14, t("Remove", "Entfernen"), mx, my, false, () -> {
            String k2 = targetKey();
            if (k2 != null && cfg.custom.remove(k2) != null) {
                try {
                    Files.deleteIfExists(Config.DIR.resolve("custom").resolve(k2.replace('/', '_') + ".png"));
                } catch (Exception ignored) {
                }
                Config.save();
                status = t("Removed - press Apply.", "Entfernt - Anwenden drücken.");
            }
        });
        c.drawText(textRenderer, t("Custom textures: ", "Eigene Texturen: ") + cfg.custom.size()
                + t("  (press Apply to use them)", "  (mit Anwenden aktivieren)"), 170, 228, dimTxt, false);
    }

    // ---------------------------------------------------------------- tab: sky

    private static final String[] FACE_NAMES = {"N", "E", "S", "W", "Up"};
    private static final int[] FACE_IDX = {1, 4, 2, 5, 3};



    private void scanCombos() {
        combos.clear();
        try (Stream<Path> st = Files.list(Config.DIR.resolve("skies"))) {
            st.map(p -> p.getFileName().toString()).filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted().forEach(combos::add);
        } catch (Exception ignored) {
        }
    }

    private void importSkyImage(Path src) {
        try {
            if (Files.size(src) > 30L * 1024 * 1024) {
                status = t("Image is too large (max 30 MB).", "Bild ist zu groß (max. 30 MB).");
                return;
            }
            BufferedImage im = ImageIO.read(src.toFile());
            if (im == null) {
                status = t("That is not a valid image (PNG/JPG).", "Das ist kein gültiges Bild (PNG/JPG).");
                return;
            }
            int max = 1400;
            if (Math.max(im.getWidth(), im.getHeight()) > max) {
                double sc = max / (double) Math.max(im.getWidth(), im.getHeight());
                BufferedImage sm = new BufferedImage((int) (im.getWidth() * sc), (int) (im.getHeight() * sc), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = sm.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(im, 0, 0, sm.getWidth(), sm.getHeight(), null);
                g.dispose();
                im = sm;
            }
            skySrc = im;
            tab = 3;
            recut();
            status = "";
        } catch (Throwable t) {
            status = t("Could not import the image.", "Bild konnte nicht importiert werden.");
        }
    }

    private void recut() {
        Textures.destroy(skyCutTex);
        skyCutTex = null;
        skyCut = null;
        if (skySrc == null) return;
        try {
            skyCut = skyCutOn ? cutout(skySrc, skyTol) : skySrc;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ImageIO.write(skyCut, "png", bo);
            skyCutTex = Textures.register("skycut", bo.toByteArray());
        } catch (Throwable ignored) {
        }
    }

    /** Background removal: flood fill from the borders over pixels close to the background color (not a neural net). */
    private static BufferedImage cutout(BufferedImage src, float tol) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        if ((out.getRGB(0, 0) >>> 24) < 128 || (out.getRGB(w - 1, 0) >>> 24) < 128
                || (out.getRGB(0, h - 1) >>> 24) < 128 || (out.getRGB(w - 1, h - 1) >>> 24) < 128) return out;
        long r = 0, gg = 0, b = 0;
        int cnt = 0;
        int[][] corners = {{0, 0}, {w - 3, 0}, {0, h - 3}, {w - 3, h - 3}};
        for (int[] cc : corners) {
            for (int dy = 0; dy < 3; dy++) {
                for (int dx = 0; dx < 3; dx++) {
                    int px0 = out.getRGB(Math.max(0, cc[0] + dx), Math.max(0, cc[1] + dy));
                    r += (px0 >> 16) & 255;
                    gg += (px0 >> 8) & 255;
                    b += px0 & 255;
                    cnt++;
                }
            }
        }
        int br = (int) (r / cnt), bgc = (int) (gg / cnt), bb = (int) (b / cnt);
        double maxDist = tol * 441.67;
        boolean[] seen = new boolean[w * h];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            q.add(x);
            q.add((h - 1) * w + x);
        }
        for (int y = 0; y < h; y++) {
            q.add(y * w);
            q.add(y * w + w - 1);
        }
        while (!q.isEmpty()) {
            int i = q.poll();
            if (seen[i]) continue;
            seen[i] = true;
            int x = i % w, y = i / w;
            int col = out.getRGB(x, y);
            double dr = ((col >> 16) & 255) - br, dg = ((col >> 8) & 255) - bgc, db = (col & 255) - bb;
            if (Math.sqrt(dr * dr + dg * dg + db * db) > maxDist) continue;
            out.setRGB(x, y, 0);
            if (x > 0 && !seen[i - 1]) q.add(i - 1);
            if (x < w - 1 && !seen[i + 1]) q.add(i + 1);
            if (y > 0 && !seen[i - w]) q.add(i - w);
            if (y < h - 1 && !seen[i + w]) q.add(i + w);
        }
        List<int[]> edge = new ArrayList<>();
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                if ((out.getRGB(x, y) >>> 24) == 0) continue;
                if ((out.getRGB(x - 1, y) >>> 24) == 0 || (out.getRGB(x + 1, y) >>> 24) == 0
                        || (out.getRGB(x, y - 1) >>> 24) == 0 || (out.getRGB(x, y + 1) >>> 24) == 0) edge.add(new int[]{x, y});
            }
        }
        for (int[] e : edge) out.setRGB(e[0], e[1], (170 << 24) | (out.getRGB(e[0], e[1]) & 0xFFFFFF));
        return out;
    }

    /** Galaxy sky (3x2 cube layout, upscaled) with the cut-out image placed on one face. */
    private BufferedImage composite() throws java.io.IOException {
        int base = Math.max(0, Math.min(Skies.COUNT - 1, cfg.sky));
        BufferedImage sky;
        try (InputStream in = StudioScreen.class.getResourceAsStream(Skies.resource(base))) {
            sky = ImageIO.read(in);
        }
        int w = 4096, h = 2048;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(sky, 0, 0, w, h, null);
        if (skyCut != null) {
            double[] az = {0.75, 0.0, 0.25, 0.5, 0.75};            // N, E, S, W, Up as position in the panorama (u)
            double elev = skyFaceSel == 4 ? 78 : 75 - skyPosY * 70;  // degrees above the horizon
            double figH = skySize * 70;                              // angular height in degrees
            int ph = (int) (figH / 180.0 * h);
            double aspect = skyCut.getWidth() / (double) skyCut.getHeight();
            double cosl = Math.max(0.25, Math.cos(Math.toRadians(elev)));
            int pw = (int) Math.min(w / 2.0, figH * aspect / cosl / 360.0 * w);
            int cx = (int) (az[skyFaceSel] * w), cy = (int) ((90 - elev) / 180.0 * h);
            for (int k = -1; k <= 1; k++) {       // wrap around the seam
                g.drawImage(skyCut, cx - pw / 2 + k * w, cy - ph / 2, pw, ph, null);
            }
        }
        g.dispose();
        return out;
    }

    private void saveCombo() {
        try {
            BufferedImage out = composite();
            Files.createDirectories(Config.DIR.resolve("skies"));
            String fn = "combo_" + (System.currentTimeMillis() / 1000) + ".png";
            ImageIO.write(out, "png", Config.DIR.resolve("skies").resolve(fn).toFile());
            scanCombos();
            cfg.skyCustom = fn;
            Config.save();
            status = t("Saved - press Apply to use it.", "Gespeichert - Anwenden drücken.");
        } catch (Throwable e) {
            status = t("Could not save the combo.", "Kombi konnte nicht gespeichert werden.");
        }
    }

    private void deleteCombo() {
        String fn = cfg.skyCustom;
        if (fn == null || fn.isBlank()) return;
        try {
            Files.deleteIfExists(Config.DIR.resolve("skies").resolve(Path.of(fn).getFileName().toString()));
        } catch (Exception ignored) {
        }
        Textures.destroy(comboTex.remove(fn));
        cfg.skyCustom = "";
        scanCombos();
        Config.save();
    }

    private Tex comboTexOf(String fn) {
        if (comboTex.containsKey(fn)) return comboTex.get(fn);
        Tex t = null;
        try {
            BufferedImage im = ImageIO.read(Config.DIR.resolve("skies").resolve(Path.of(fn).getFileName().toString()).toFile());
            BufferedImage th = new BufferedImage(256, 128, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = th.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(im, 0, 0, 256, 128, null);
            g.dispose();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ImageIO.write(th, "png", bo);
            t = Textures.register("combo", bo.toByteArray());
        } catch (Throwable ignored) {
        }
        comboTex.put(fn, t);
        return t;
    }

    private static final Identifier SKY_ATLAS = Identifier.of(StudioClient.ID, "textures/sky/preview_atlas.png");

    private void drawSky(DrawContext c, int mx, int my) {
        box(c, 8, 42, 150, 220);
        c.drawText(textRenderer, t("Sky", "Himmel"), 16, 47, txt, true);
        button(c, 16, 58, 134, 12, cfg.skyOn ? t("Custom sky: ON", "Eigener Himmel: AN") : t("Custom sky: OFF", "Eigener Himmel: AUS"),
                mx, my, cfg.skyOn, () -> {
                    cfg.skyOn = !cfg.skyOn;
                    status = t("Press Apply.", "Anwenden drücken.");
                });
        smallLeft(c, t("Brightness ", "Helligkeit ") + Math.round(cfg.skyBrightness * 100) + "%", 16, 75, txt, 0.8f);
        slider(c, 16, 85, 134, 6, (cfg.skyBrightness - 0.2f) / 1.3f, 15);
        button(c, 16, 96, 134, 12, cfg.skyHideVanilla ? t("Hide vanilla sky: ON", "Vanilla-Himmel aus: AN") : t("Hide vanilla sky: OFF", "Vanilla-Himmel aus: AUS"),
                mx, my, cfg.skyHideVanilla, () -> cfg.skyHideVanilla = !cfg.skyHideVanilla);

        c.drawText(textRenderer, t("Image in the sky", "Bild im Himmel"), 16, 114, txt, true);
        button(c, 16, 125, 134, 12, t("Add image...", "Bild hinzufügen..."), mx, my, true, this::pickSkyImage);
        c.fill(16, 141, 46, 171, alpha(0x000000, 0x55));
        if (skyCutTex != null) drawFit(c, skyCutTex, 17, 142, 28, 28);
        else smallLeft(c, t("drop", "hier"), 20, 153, dimTxt, 0.7f);
        button(c, 50, 141, 100, 12, skyCutOn ? t("Cut out: ON", "Freistellen: AN") : t("Cut out: OFF", "Freistellen: AUS"), mx, my, skyCutOn, () -> {
            skyCutOn = !skyCutOn;
            recut();
        });
        smallLeft(c, t("Tolerance ", "Toleranz ") + Math.round(skyTol * 100) + "%", 50, 157, txt, 0.75f);
        slider(c, 50, 166, 100, 5, skyTol / 0.8f, 11);
        for (int i = 0; i < 5; i++) {
            int fi = i;
            button(c, 16 + i * 27, 177, 25, 11, FACE_NAMES[i], mx, my, skyFaceSel == i, () -> skyFaceSel = fi);
        }
        smallLeft(c, t("Size ", "Größe ") + Math.round(skySize * 100) + "%", 16, 192, txt, 0.75f);
        slider(c, 16, 201, 134, 5, (skySize - 0.2f) / 0.8f, 12);
        smallLeft(c, t("Height", "Höhe"), 16, 210, txt, 0.75f);
        slider(c, 16, 219, 134, 5, skyPosY, 13);
        button(c, 16, 230, 134, 12, t("Save as combo", "Als Kombi speichern"), mx, my, true, this::saveCombo);
        button(c, 16, 245, 134, 12, t("Delete selected combo", "Gewählte Kombi löschen"), mx, my, false, this::deleteCombo);

        box(c, 164, 42, 268, 220);
        c.drawText(textRenderer, t("Choose a sky", "Wähle einen Himmel"), 174, 50, txt, true);
        String cur = !cfg.skyCustom.isEmpty() ? cfg.skyCustom : cfg.sky >= 0 ? Skies.NAMES[cfg.sky] : t("none selected", "nichts gewählt");
        c.drawText(textRenderer, textRenderer.trimToWidth(cur, 250), 174, 64, dimTxt, false);

        List<Card> cards = new ArrayList<>();
        for (String fn : combos) {
            cards.add(new Card(fn.replace(".png", ""), t("My combo", "Meine Kombi"), fn.equals(cfg.skyCustom),
                    (cc, x, y, size) -> {
                        Tex ct = comboTexOf(fn);
                        if (ct != null) cc.drawTexture(RenderPipelines.GUI_TEXTURED, ct.id(), x + 1, y + 13, 0f, 0f, 52, 26, ct.w(), ct.h(), ct.w(), ct.h());
                    },
                    () -> {
                        cfg.skyCustom = fn;
                        cfg.skyOn = true;
                        Config.save();
                        status = t("Sky changed - press Apply.", "Himmel geändert - Anwenden drücken.");
                    }));
        }
        for (int i = 0; i < Skies.COUNT; i++) {
            int si = i;
            cards.add(new Card(Skies.NAMES[i], Skies.FAMILY[i], cfg.skyCustom.isEmpty() && cfg.sky == i,
                    (cc, x, y, size) -> cc.drawTexture(RenderPipelines.GUI_TEXTURED, SKY_ATLAS, x + 1, y + 13, (float) ((si % 6) * 256),
                            (float) ((si / 6) * 128), 52, 26, 256, 128, 1536, 512),
                    () -> {
                        cfg.sky = si;
                        cfg.skyCustom = "";
                        cfg.skyOn = true;
                        Config.save();
                        status = t("Sky changed - press Apply.", "Himmel geändert - Anwenden drücken.");
                    }));
        }
        skyScroll = drawCards(c, mx, my, cards, skyScroll);
    }

    // ---------------------------------------------------------------- tab: design

    private void drawDesign(DrawContext c, int mx, int my) {
        box(c, 8, 42, 424, 220);

        c.drawText(textRenderer, "Colorway", 16, 48, txt, true);
        for (int i = 0; i < PRESETS.length; i++) {
            Preset p = PRESETS[i];
            int x = 16 + i * 52;
            c.fill(x, 58, x + 49, 76, Config.color(p.bg()));
            hgrad(c, x, 71, 49, 5, Config.color(p.accent()), Config.color(p.accent2()));
            centered(c, p.name(), x, 58, 49, 13, Config.color(p.text()));
            if (over(mx, my, x, 58, 49, 18)) outline(c, x, 58, 49, 18, txt);
            click(x, 58, 49, 18, () -> {
                cfg.accent = p.accent();
                cfg.accent2 = p.accent2();
                cfg.background = p.bg();
                cfg.panel = p.panel();
                cfg.text = p.text();
                selectSlot(slot);
            });
        }

        String[] slotNames = cfg.german ? SLOT_DE : SLOT_EN;
        for (int i = 0; i < slotNames.length; i++) {
            int y = 84 + i * 15;
            boolean sel = i == slot;
            if (sel) hgrad(c, 16, y, 140, 13, mix(pan, acc, 0.6f), mix(pan, acc2, 0.6f));
            else if (over(mx, my, 16, y, 140, 13)) c.fill(16, y, 156, y + 13, alpha(acc, 0x35));
            c.fill(19, y + 2, 29, y + 11, Config.color(slotHex(i)));
            outline(c, 19, y + 2, 10, 9, alpha(txt, 0x80));
            c.drawText(textRenderer, slotNames[i], 34, y + 3, txt, false);
            int si = i;
            click(16, y, 140, 13, () -> selectSlot(si));
        }
        button(c, 16, 176, 140, 12, cfg.breakColorEnabled ? t("Mining cracks color: ON", "Abbau-Farbe: AN") : t("Mining cracks color: OFF", "Abbau-Farbe: AUS"),
                mx, my, cfg.breakColorEnabled, () -> {
                    cfg.breakColorEnabled = !cfg.breakColorEnabled;
                    status = t("Press Apply to use it.", "Anwenden drücken.");
                });

        int px0 = 168, py0 = 84, pw = 122, ph = 78;
        for (int i = 0; i < pw; i += 2) {
            c.fillGradient(px0 + i, py0, px0 + Math.min(i + 2, pw), py0 + ph, hsv(pick[0], (float) i / pw, 1f), 0xFF000000);
        }
        outline(c, px0 - 1, py0 - 1, pw + 2, ph + 2, alpha(txt, 0x60));
        int mxk = px0 + (int) (pick[1] * pw), myk = py0 + (int) ((1f - pick[2]) * ph);
        outline(c, mxk - 3, myk - 3, 7, 7, 0xFFFFFFFF);
        outline(c, mxk - 2, myk - 2, 5, 5, 0xFF000000);
        click(px0, py0, pw, ph, () -> startDrag(1, px0, py0, pw, ph));
        int hy = py0 + ph + 6, hh = 10;
        for (int i = 0; i < pw; i += 2) c.fill(px0 + i, hy, px0 + Math.min(i + 2, pw), hy + hh, hsv((float) i / pw, 1f, 1f));
        outline(c, px0 - 1, hy - 1, pw + 2, hh + 2, alpha(txt, 0x60));
        int hx = px0 + (int) (pick[0] * pw);
        c.fill(hx - 1, hy - 2, hx + 2, hy + hh + 2, 0xFFFFFFFF);
        click(px0, hy - 2, pw, hh + 4, () -> startDrag(2, px0, hy, pw, hh));
        c.drawText(textRenderer, slotNames[slot], px0, hy + 16, dimTxt, false);

        int sx = 304, sw = 120;
        c.drawText(textRenderer, t("Window opacity  ", "Fenster-Deckkraft  ") + Math.round(cfg.opacity * 100) + "%", sx, 82, txt, false);
        slider(c, sx, 93, sw, 7, (cfg.opacity - 0.1f) / 0.9f, 3);
        c.drawText(textRenderer, t("Background dim  ", "Hintergrund abdunkeln  ") + Math.round(cfg.dim * 100) + "%", sx, 108, txt, false);
        slider(c, sx, 119, sw, 7, cfg.dim, 4);
        c.drawText(textRenderer, t("Size  ", "Größe  ") + Math.round(cfg.scale * 100) + "%", sx, 134, txt, false);
        slider(c, sx, 145, sw, 7, (cfg.scale - 0.4f) / 0.6f, 5);
        c.drawText(textRenderer, t("Corner (below 100%)", "Ecke (unter 100%)"), sx, 160, txt, false);
        String[] pos = {"C", "TL", "TR", "BL", "BR"};
        for (int i = 0; i < 5; i++) {
            int k = i;
            button(c, sx + i * 25, 171, 23, 12, pos[i], mx, my, cfg.corner == i, () -> {
                cfg.corner = k;
                Config.save();
            });
        }

        c.drawText(textRenderer, t("Background image", "Hintergrundbild"), 16, 198, txt, true);
        button(c, 16, 210, 104, 13, t("Upload image...", "Bild hochladen..."), mx, my, true, this::pickBackground);
        button(c, 124, 210, 50, 13, t("None", "Keins"), mx, my, cfg.backgroundImage.isEmpty(), () -> {
            cfg.backgroundImage = "";
            loadBackground();
            Config.save();
        });
        c.drawText(textRenderer, t("or drop a PNG/JPG into the window", "oder PNG/JPG ins Fenster ziehen"), 182, 213, dimTxt, false);
        for (int i = 0; i < bgFiles.size(); i++) {
            String f = bgFiles.get(i);
            int x = 16 + i * 106;
            button(c, x, 227, 90, 13, f, mx, my, f.equals(cfg.backgroundImage), () -> {
                cfg.backgroundImage = f;
                loadBackground();
                Config.save();
            });
            boolean xh = over(mx, my, x + 92, 227, 13, 13);
            rbox(c, x + 92, 227, 13, 13, xh ? 0xFFB3261E : mix(pan, txt, 0.12f), 1);
            centered(c, "x", x + 92, 227, 13, 13, txt);
            click(x + 92, 227, 13, 13, () -> deleteBackground(f));
        }
    }

    // ---------------------------------------------------------------- input

    private boolean ctrlDown() {
        long h = GLFW.glfwGetCurrentContext();
        return GLFW.glfwGetKey(h, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            double lx = (click.x() - ox) / s, ly = (click.y() - oy) / s;
            focus = -1;
            boolean hit = false;
            for (int i = btns.size() - 1; i >= 0; i--) {
                Btn b = btns.get(i);
                if (lx >= b.x() && lx < b.x() + b.w() && ly >= b.y() && ly < b.y() + b.h()) {
                    b.action().run();
                    hit = true;
                    break;
                }
            }
            if (!hit && !full && lx >= 0 && lx < W && ly >= 0 && ly < 36) {
                dragging = true;
                grabX = (float) (click.x() - ox);
                grabY = (float) (click.y() - oy);
            }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (ctrlDown()) {
            zoom(verticalAmount > 0 ? 0.05f : -0.05f);
            return true;
        }
        int d = verticalAmount > 0 ? -1 : 1;
        double lx = (mouseX - ox) / s;
        if (tab == 0) {
            if (lx < 160) catScroll += d * 2;
            else catOptScroll += d;
        } else if (tab == 1) {
            if (lx < 160) listScroll += d * 2;
            else optScroll += d;
        } else if (tab == 3 && lx >= 160) {
            skyScroll += d;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (focus == 0) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!search.isEmpty()) {
                    search = search.substring(0, search.length() - 1);
                    refilter();
                    listScroll = 0;
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_KP_ENTER) {
                focus = -1;
                return true;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) {
            zoom(0.05f);
            return true;
        }
        if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) {
            zoom(-0.05f);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (focus != 0) return super.charTyped(input);
        if (input.codepoint() >= 32 && search.length() < 24) {
            search += new String(Character.toChars(input.codepoint()));
            refilter();
            listScroll = 0;
        }
        return true;
    }

    @Override
    public void removed() {
        for (Tex t : previews.values()) Textures.destroy(t);
        previews.clear();
        models.clear();
        ModelPreview.clear();
        for (Tex t : comboTex.values()) Textures.destroy(t);
        comboTex.clear();
        Textures.destroy(skyCutTex);
        skyCutTex = null;
        Textures.destroy(bgTex);
        bgTex = null;
        Config.save();
    }
}
