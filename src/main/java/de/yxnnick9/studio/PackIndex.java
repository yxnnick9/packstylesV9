package de.yxnnick9.studio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Liest nur lesend den Inhalt eines Texturepacks (Ordner oder .zip) im resourcepacks-Ordner. */
public final class PackIndex {
    private static final long MAX_BYTES = 16L * 1024 * 1024;

    public final String id;     // Dateiname, eindeutig
    public final String label;  // Anzeigename
    private final Path path;
    private final boolean zip;
    /** Alle Dateien (png, png.mcmeta, ogg, sounds.json) relativ zu assets/minecraft/ */
    public final Set<String> files = new TreeSet<>();
    /** Einzel-Texturen fuer den Items-Tab: "item/x" und "block/x" */
    public final Set<String> keys = new TreeSet<>();
    private final Map<String, List<String>> cats = new HashMap<>();

    private PackIndex(String id, String label, Path path, boolean zip) {
        this.id = id;
        this.label = label;
        this.path = path;
        this.zip = zip;
    }

    public static List<PackIndex> scan(Path dir) {
        List<PackIndex> out = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) return out;
        try (Stream<Path> st = Files.list(dir)) {
            st.sorted().forEach(p -> {
                String n = p.getFileName().toString();
                if (n.equals(Applier.PACK_NAME)) return;
                PackIndex pi = null;
                if (Files.isRegularFile(p) && n.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    pi = new PackIndex(n, n.substring(0, n.length() - 4), p, true);
                } else if (Files.isDirectory(p)) {
                    pi = new PackIndex(n, n, p, false);
                }
                if (pi != null) {
                    pi.index();
                    if (!pi.files.isEmpty()) out.add(pi);
                }
            });
        } catch (IOException ignored) {
        }
        return out;
    }

    static boolean safe(String rel) {
        return rel != null && !rel.contains("..") && !rel.startsWith("/")
                && rel.matches("[a-z0-9_.-]+(/[a-z0-9_.-]+)+");
    }

    private void addFile(String full) {
        if (!full.startsWith("assets/minecraft/")) return;
        String rel = full.substring("assets/minecraft/".length());
        boolean ok = rel.endsWith(".png") || rel.endsWith(".png.mcmeta") || rel.endsWith(".ogg")
                || rel.equals("sounds.json")
                || (rel.endsWith(".json") && (rel.startsWith("items/") || rel.startsWith("models/item/")));
        if (ok && (rel.equals("sounds.json") || safe(rel))) files.add(rel);
    }

    private void index() {
        try {
            if (zip) {
                try (ZipFile z = new ZipFile(path.toFile())) {
                    z.stream().filter(e -> !e.isDirectory()).forEach(e -> addFile(e.getName()));
                }
            } else {
                Path base = path.resolve("assets/minecraft");
                if (Files.isDirectory(base)) {
                    try (Stream<Path> s = Files.walk(base)) {
                        s.filter(Files::isRegularFile).forEach(f ->
                                addFile("assets/minecraft/" + base.relativize(f).toString().replace('\\', '/')));
                    }
                }
            }
        } catch (Exception ignored) {
        }

        for (String rel : files) {
            if (rel.matches("textures/(item|block)/[^/]+\\.png")) {
                String key = rel.substring("textures/".length(), rel.length() - 4);
                if (key.matches("(item|block)/[a-z0-9_-][a-z0-9_.-]*")) keys.add(key);
            }
        }

        JsonObject sounds = null;
        if (files.contains("sounds.json")) {
            try {
                byte[] b = readAsset("minecraft/sounds.json");
                if (b != null) sounds = JsonParser.parseString(new String(b, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception ignored) {
            }
        }
        for (Categories.Cat c : Categories.ALL) {
            List<String> l = new ArrayList<>();
            for (String rel : files) if (c.matchesFile(rel)) l.add(rel);
            if (c.events != null && sounds != null) {
                for (String ev : sounds.keySet()) {
                    if (c.events.matcher(ev).matches()) {
                        l.add("sounds.json");
                        break;
                    }
                }
            }
            if (!l.isEmpty()) cats.put(c.id, l);
        }
    }

    /** Dateien einer Kategorie (kann den Platzhalter "sounds.json" enthalten). */
    public List<String> catFiles(String catId) {
        return cats.getOrDefault(catId, List.of());
    }

    public int fileCount(String catId) {
        int n = 0;
        for (String f : catFiles(catId)) if (f.endsWith(".png") || f.endsWith(".ogg")) n++;
        return n;
    }

    public boolean has(String rel) {
        return files.contains(rel);
    }

    /** Beste Vorschau-Textur einer Kategorie: bevorzugt die Wunsch-Textur, sonst Item > Block > GUI > Rest > Entity. */
    public String previewPng(Categories.Cat c) {
        List<String> l = catFiles(c.id);
        if (c.preview != null && files.contains(c.preview + ".png") && c.matchesFile(c.preview + ".png")) {
            return c.preview + ".png";
        }
        String best = null;
        int bestScore = -1;
        for (String f : l) {
            if (!f.endsWith(".png")) continue;
            int sc = f.startsWith("textures/item/") ? 5 : f.startsWith("textures/block/") ? 4
                    : f.startsWith("textures/gui/") ? 3 : f.startsWith("textures/entity/") ? 1 : 2;
            if (sc > bestScore) {
                bestScore = sc;
                best = f;
            }
        }
        return best;
    }

    public JsonObject soundsJson() {
        try {
            byte[] b = readAsset("minecraft/sounds.json");
            if (b != null) return JsonParser.parseString(new String(b, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Alt-API fuer den Items-Tab: key = "item/x", suffix = ".png" oder ".png.mcmeta". */
    public byte[] read(String key, String suffix) throws IOException {
        return readAsset("minecraft/textures/" + key + suffix);
    }

    /** relToAssets z.B. "minecraft/textures/item/x.png"; null falls nicht vorhanden/unsicher. */
    public byte[] readAsset(String relToAssets) throws IOException {
        if (relToAssets == null || relToAssets.contains("..") || relToAssets.startsWith("/")) return null;
        if (!relToAssets.matches("[a-z0-9_.-]+(/[a-z0-9_.-]+)+")) return null;
        String name = "assets/" + relToAssets;
        if (zip) {
            try (ZipFile z = new ZipFile(path.toFile())) {
                ZipEntry e = z.getEntry(name);
                if (e == null || e.getSize() > MAX_BYTES) return null;
                try (InputStream in = z.getInputStream(e)) {
                    return in.readNBytes((int) MAX_BYTES);
                }
            }
        }
        Path f = path.resolve(name).normalize();
        if (!f.startsWith(path.normalize()) || !Files.isRegularFile(f) || Files.size(f) > MAX_BYTES) return null;
        return Files.readAllBytes(f);
    }
}
