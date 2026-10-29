package de.yxnnick9.studio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/** Builds the generated pack (yxnnick9_texturestudio) in the resourcepacks folder and enables it on top. */
public final class Applier {
    public static final String PACK_NAME = "yxnnick9_texturestudio";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String MCMETA = """
            {
              "pack": {
                "description": "yxnnick9 Texture Studio",
                "pack_format": 75,
                "supported_formats": { "min_inclusive": 34, "max_inclusive": 75 }
              }
            }
            """;

    private Applier() {
    }

    private static PackIndex find(List<PackIndex> packs, String id) {
        if (id == null) return null;
        for (PackIndex q : packs) if (q.id.equals(id)) return q;
        return null;
    }

    private static void write(Path root, String rel, byte[] data) throws IOException {
        Path target = root.resolve(rel).normalize();
        if (!target.startsWith(root)) return;
        Files.createDirectories(target.getParent());
        Files.write(target, data);
    }

    public static int apply(MinecraftClient mc, List<PackIndex> packs, Config cfg) throws IOException {
        Path root = mc.getResourcePackDir().resolve(PACK_NAME).toAbsolutePath().normalize();
        deleteTree(root);
        Files.createDirectories(root);
        Files.writeString(root.resolve("pack.mcmeta"), MCMETA);

        int n = 0;
        JsonObject sounds = new JsonObject();

        // 1) Categories (files + model dependencies + sounds)
        for (Categories.Cat c : Categories.ALL) {
            String selId = cfg.catSelections.get(c.id);
            if (c.id.equals("crystal") && selId != null && selId.startsWith("builtin:")) {
                n += builtinCrystal(mc, root, selId.substring(8));
                continue;
            }
            PackIndex p = find(packs, selId);
            float vol = c.id.equals("totemsound") ? cfg.totemVolume : c.id.equals("hitsound") ? cfg.hitVolume : 1f;
            if (p == null) {
                if (c.id.equals("totemsound") && Math.abs(vol - 1f) > 0.01f) {
                    JsonObject ev = new JsonObject();
                    ev.addProperty("replace", true);
                    JsonArray arr = new JsonArray();
                    JsonObject snd = new JsonObject();
                    snd.addProperty("name", "item/totem/use");
                    snd.addProperty("volume", vol);
                    arr.add(snd);
                    ev.add("sounds", arr);
                    sounds.add("item.totem.use", ev);
                }
                continue;
            }
            for (String rel : p.catFiles(c.id)) {
                if (rel.equals("sounds.json")) continue;
                byte[] b = p.readAsset("minecraft/" + rel);
                if (b == null) continue;
                write(root, "assets/minecraft/" + rel, b);
                if (rel.endsWith(".png") || rel.endsWith(".ogg")) n++;
                if (rel.endsWith(".json")) crawl(root, p, "minecraft/" + rel);
            }
            if (c.events != null) n += mergeSounds(root, p, c, sounds, vol);
        }

        // 2) Single textures per item/block (override categories)
        for (Map.Entry<String, String> e : cfg.selections.entrySet()) {
            PackIndex p = find(packs, e.getValue());
            if (p == null) continue;
            byte[] png = p.read(e.getKey(), ".png");
            if (png == null) continue;
            write(root, "assets/minecraft/textures/" + e.getKey() + ".png", png);
            byte[] meta = p.read(e.getKey(), ".png.mcmeta");
            if (meta != null) write(root, "assets/minecraft/textures/" + e.getKey() + ".png.mcmeta", meta);
            n++;
        }

        // 3) Hand-drawn textures
        for (Map.Entry<String, String> e : cfg.custom.entrySet()) {
            String fn = Path.of(e.getValue()).getFileName().toString();
            Path f = Config.DIR.resolve("custom").resolve(fn);
            if (!Files.isRegularFile(f) || !e.getKey().matches("(item|block)/[a-z0-9_-][a-z0-9_.-]*")) continue;
            write(root, "assets/minecraft/textures/" + e.getKey() + ".png", Files.readAllBytes(f));
            n++;
        }

        // 4) Mining cracks color
        if (cfg.breakColorEnabled && Config.validHex(cfg.breakColor)) {
            n += breakStages(mc, root, find(packs, cfg.catSelections.get("mining")), cfg.breakColor);
        }

        // 5) Totem size in the hands (display.firstperson_/thirdperson_ scale of the totem model)
        if (Math.abs(cfg.totemHand - 1f) > 0.01f) {
            try {
                totemModel(root, find(packs, cfg.catSelections.get("totem")), cfg.totemHand);
            } catch (Exception ignored) {
            }
        }

        // 6) Sky: own panorama renderer. The chosen panorama is written as yxnnick9studio:textures/sky/active.png
        byte[] skyPng = null;
        if (cfg.skyOn) {
            try {
                if (cfg.skyCustom != null && !cfg.skyCustom.isBlank()) {
                    Path sf = Config.DIR.resolve("skies").resolve(Path.of(cfg.skyCustom).getFileName().toString());
                    if (Files.isRegularFile(sf)) skyPng = Files.readAllBytes(sf);
                } else if (cfg.sky >= 0 && cfg.sky < Skies.COUNT) {
                    try (InputStream in = Applier.class.getResourceAsStream(Skies.resource(cfg.sky))) {
                        if (in != null) {
                            BufferedImage im = ImageIO.read(in);
                            ByteArrayOutputStream bo = new ByteArrayOutputStream();
                            ImageIO.write(im, "png", bo);
                            skyPng = bo.toByteArray();
                        }
                    }
                }
            } catch (Exception ignored) {
                skyPng = null;
            }
        }
        if (skyPng != null) {
            write(root, "assets/yxnnick9studio/textures/sky/active.png", skyPng);
            write(root, "assets/yxnnick9studio/textures/sky/active.png.mcmeta",
                    "{\"texture\":{\"blur\":true,\"clamp\":false}}".getBytes(StandardCharsets.UTF_8));
            cfg.skyApplied = true;
            n++;
        } else {
            cfg.skyApplied = false;
        }

        if (sounds.size() > 0) {
            write(root, "assets/minecraft/sounds.json", GSON.toJson(sounds).getBytes(StandardCharsets.UTF_8));
        }

        // Enable the pack (last entry = highest priority) and reload
        ResourcePackManager m = mc.getResourcePackManager();
        m.scanPacks();
        List<String> before = new ArrayList<>(m.getEnabledIds());
        List<String> ids = new ArrayList<>(before);
        String id = "file/" + PACK_NAME;
        ids.remove(id);
        ids.add(id);
        m.setEnabledProfiles(ids);
        mc.options.refreshResourcePacks(m);
        if (ids.equals(before)) mc.reloadResources();
        return n;
    }

    private static int builtinCrystal(MinecraftClient mc, Path root, String styleId) {
        BuiltIn.Style st = BuiltIn.byId(styleId);
        if (st == null) return 0;
        for (String rel : new String[]{"textures/entity/end_crystal/end_crystal.png", "textures/entity/end_crystal.png"}) {
            try {
                List<Resource> rs = mc.getResourceManager().getAllResources(Identifier.of("minecraft", rel));
                if (rs.isEmpty()) continue;
                byte[] src;
                try (InputStream in = rs.get(0).getInputStream()) {
                    src = in.readAllBytes();
                }
                byte[] out = BuiltIn.crystal(src, st);
                if (out != null) {
                    write(root, "assets/minecraft/" + rel, out);
                    return 1;
                }
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ models: copy dependencies

    private static String[] split(String id) {
        int i = id.indexOf(':');
        String ns = i < 0 ? "minecraft" : id.substring(0, i), path = i < 0 ? id : id.substring(i + 1);
        if (!ns.matches("[a-z0-9_.-]+") || !path.matches("[a-z0-9_./-]+") || path.contains("..")) return null;
        return new String[]{ns, path};
    }

    private static void models(JsonElement e, Deque<String> q) {
        if (e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
                JsonElement v = en.getValue();
                if (en.getKey().equals("model") && v.isJsonPrimitive()) {
                    String[] id = split(v.getAsString());
                    if (id != null) q.add(id[0] + "/models/" + id[1] + ".json");
                } else {
                    models(v, q);
                }
            }
        } else if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) models(x, q);
        }
    }

    /** Copies a model/item-definition json and everything it references (parents, models, textures). */
    private static void crawl(Path root, PackIndex p, String start) throws IOException {
        Deque<String> q = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        q.add(start);
        while (!q.isEmpty() && seen.size() < 500) {
            String rel = q.poll();
            if (!seen.add(rel)) continue;
            byte[] b = p.readAsset(rel);
            if (b == null) continue;
            write(root, "assets/" + rel, b);
            JsonElement el;
            try {
                el = JsonParser.parseString(new String(b, StandardCharsets.UTF_8));
            } catch (Exception ex) {
                continue;
            }
            if (rel.contains("/items/")) {
                models(el, q);
            } else if (el.isJsonObject()) {
                JsonObject o = el.getAsJsonObject();
                if (o.has("parent") && o.get("parent").isJsonPrimitive()) {
                    String par = o.get("parent").getAsString();
                    String[] id = split(par);
                    if (id != null && !id[1].startsWith("builtin/")) q.add(id[0] + "/models/" + id[1] + ".json");
                }
                if (o.has("textures") && o.get("textures").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> t : o.getAsJsonObject("textures").entrySet()) {
                        if (!t.getValue().isJsonPrimitive()) continue;
                        String v = t.getValue().getAsString();
                        if (v.startsWith("#")) continue;
                        String[] id = split(v);
                        if (id == null) continue;
                        String tr = id[0] + "/textures/" + id[1];
                        byte[] png = p.readAsset(tr + ".png");
                        if (png != null) write(root, "assets/" + tr + ".png", png);
                        byte[] meta = p.readAsset(tr + ".png.mcmeta");
                        if (meta != null) write(root, "assets/" + tr + ".png.mcmeta", meta);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ totem size

    private static String firstModel(JsonElement e) {
        if (e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
                if (en.getKey().equals("model") && en.getValue().isJsonPrimitive()) return en.getValue().getAsString();
                String r = firstModel(en.getValue());
                if (r != null) return r;
            }
        } else if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                String r = firstModel(x);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static JsonArray vec(float a, float b, float c) {
        JsonArray arr = new JsonArray();
        arr.add(a);
        arr.add(b);
        arr.add(c);
        return arr;
    }

    private static void totemModel(Path root, PackIndex tp, float factor) throws IOException {
        String modelId = "minecraft:item/totem_of_undying";
        if (tp != null) {
            byte[] def = tp.readAsset("minecraft/items/totem_of_undying.json");
            if (def != null) {
                String m = firstModel(JsonParser.parseString(new String(def, StandardCharsets.UTF_8)));
                if (m != null) modelId = m;
            }
        }
        String[] id = split(modelId);
        if (id == null) return;
        String rel = id[0] + "/models/" + id[1] + ".json";
        JsonObject model = null;
        if (tp != null) {
            byte[] b = tp.readAsset(rel);
            if (b != null) model = JsonParser.parseString(new String(b, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        if (model == null) {
            model = new JsonObject();
            model.addProperty("parent", "minecraft:item/generated");
            JsonObject t = new JsonObject();
            t.addProperty("layer0", "minecraft:item/totem_of_undying");
            model.add("textures", t);
        }
        JsonObject display = model.has("display") ? model.getAsJsonObject("display") : new JsonObject();
        // defaults of minecraft:item/generated
        String[] keys = {"thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand", "firstperson_lefthand"};
        float[][] rot = {{0, 0, 0}, {0, 0, 0}, {0, -90, 25}, {0, 90, -25}};
        float[][] tr = {{0, 3, 1}, {0, 3, 1}, {1.13f, 3.2f, 1.13f}, {1.13f, 3.2f, 1.13f}};
        float[] sc = {0.55f, 0.55f, 0.68f, 0.68f};
        for (int i = 0; i < keys.length; i++) {
            JsonObject o = display.has(keys[i]) && display.get(keys[i]).isJsonObject() ? display.getAsJsonObject(keys[i]) : null;
            float[] base = {sc[i], sc[i], sc[i]};
            if (o == null) {
                o = new JsonObject();
                o.add("rotation", vec(rot[i][0], rot[i][1], rot[i][2]));
                o.add("translation", vec(tr[i][0], tr[i][1], tr[i][2]));
            } else if (o.has("scale") && o.get("scale").isJsonArray() && o.getAsJsonArray("scale").size() >= 3) {
                JsonArray a = o.getAsJsonArray("scale");
                base = new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
            }
            o.add("scale", vec(base[0] * factor, base[1] * factor, base[2] * factor));
            display.add(keys[i], o);
        }
        model.add("display", display);
        write(root, "assets/" + rel, GSON.toJson(model).getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ sounds

    private static JsonElement withVolume(JsonElement el, float vol) {
        if (Math.abs(vol - 1f) < 0.01f) return el;
        if (el.isJsonPrimitive()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", el.getAsString());
            o.addProperty("volume", vol);
            return o;
        }
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject().deepCopy();
            float old = o.has("volume") ? o.get("volume").getAsFloat() : 1f;
            o.addProperty("volume", old * vol);
            return o;
        }
        return el;
    }

    /** Takes matching events from the pack's sounds.json (replace:true) and copies the referenced .ogg files. */
    private static int mergeSounds(Path root, PackIndex p, Categories.Cat c, JsonObject out, float vol) throws IOException {
        JsonObject src = p.soundsJson();
        int events = 0;
        if (src != null) {
            for (String ev : src.keySet()) {
                if (!c.events.matcher(ev).matches() || !src.get(ev).isJsonObject()) continue;
                JsonObject copy = src.get(ev).getAsJsonObject().deepCopy();
                copy.addProperty("replace", true);
                if (copy.has("sounds") && copy.get("sounds").isJsonArray()) {
                    JsonArray nw = new JsonArray();
                    for (JsonElement el : copy.getAsJsonArray("sounds")) {
                        nw.add(withVolume(el, vol));
                        String name = null;
                        if (el.isJsonPrimitive()) name = el.getAsString();
                        else if (el.isJsonObject() && el.getAsJsonObject().has("name")
                                && !"event".equals(el.getAsJsonObject().has("type") ? el.getAsJsonObject().get("type").getAsString() : ""))
                            name = el.getAsJsonObject().get("name").getAsString();
                        if (name == null) continue;
                        String[] id = split(name);
                        if (id == null) continue;
                        String rel = id[0] + "/sounds/" + id[1] + ".ogg";
                        byte[] b = p.readAsset(rel);
                        if (b != null) write(root, "assets/" + rel, b);
                    }
                    copy.add("sounds", nw);
                }
                out.add(ev, copy);
                events++;
            }
        }
        if (events == 0 && Math.abs(vol - 1f) > 0.01f) {
            // Pack replaces vanilla paths without sounds.json -> build events from the file names
            Map<String, JsonArray> groups = new LinkedHashMap<>();
            for (String f : p.catFiles(c.id)) {
                if (!f.startsWith("sounds/") || !f.endsWith(".ogg")) continue;
                String base = f.substring(7, f.length() - 4);
                if (base.startsWith("damage/")) continue;
                String ev = base.replaceAll("[0-9]+$", "").replace('/', '.');
                JsonObject snd = new JsonObject();
                snd.addProperty("name", base);
                snd.addProperty("volume", vol);
                groups.computeIfAbsent(ev, k -> new JsonArray()).add(snd);
            }
            for (Map.Entry<String, JsonArray> g : groups.entrySet()) {
                JsonObject ev = new JsonObject();
                ev.addProperty("replace", true);
                ev.add("sounds", g.getValue());
                out.add(g.getKey(), ev);
                events++;
            }
        }
        return events;
    }

    // ------------------------------------------------------------------ mining cracks

    private static int breakStages(MinecraftClient mc, Path root, PackIndex pack, String hex) {
        int rgb = Integer.parseInt(hex.replace("#", ""), 16);
        int n = 0;
        for (int i = 0; i < 10; i++) {
            String rel = "textures/block/destroy_stage_" + i + ".png";
            byte[] src = null;
            try {
                if (pack != null) src = pack.readAsset("minecraft/" + rel);
            } catch (IOException ignored) {
            }
            if (src == null) {
                try {
                    List<Resource> rs = mc.getResourceManager().getAllResources(Identifier.of("minecraft", rel));
                    if (!rs.isEmpty()) {
                        try (InputStream in = rs.get(0).getInputStream()) {
                            src = in.readAllBytes();
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            if (src == null) continue;
            byte[] out = tint(src, rgb);
            if (out == null) continue;
            try {
                write(root, "assets/minecraft/" + rel, out);
                n++;
            } catch (IOException ignored) {
            }
        }
        return n;
    }

    private static byte[] tint(byte[] png, int rgb) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null) return null;
            BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int argb = img.getRGB(x, y);
                    int a = argb >>> 24;
                    if (a == 0) {
                        out.setRGB(x, y, 0);
                        continue;
                    }
                    int lum = (((argb >> 16) & 255) + ((argb >> 8) & 255) + (argb & 255)) / 3;
                    float f = 0.65f + 0.35f * (lum / 255f);
                    int r = (int) (((rgb >> 16) & 255) * f), g = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
                    int na = Math.min(255, (int) (a * 1.4f) + 20);
                    out.setRGB(x, y, (na << 24) | (r << 16) | (g << 8) | b);
                }
            }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bo);
            return bo.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> s = Files.walk(root)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
