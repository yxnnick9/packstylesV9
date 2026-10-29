package de.yxnnick9.studio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Renders an item's real model (from the pack, falling back to vanilla) as a spinning 3D preview.
 * Supports cuboid "elements" models (Blockbench packs) and flat sprites (extruded).
 */
final class ModelPreview {
    private static final class Quad {
        float[] o, u, v, n;
        Tex tex;
        float ru, rv, rw, rh;
        boolean layer, floats;
        int shade;
    }

    private static final Map<String, Tex> TEX = new HashMap<>();
    private static final Set<String> BAD = new HashSet<>();

    static void clear() {
        for (Tex t : TEX.values()) Textures.destroy(t);
        TEX.clear();
        BAD.clear();
    }

    private final PackIndex pack;       // null = vanilla
    private final ResourceManager rm;
    private final List<Quad> quads = new ArrayList<>();
    private boolean ok;
    private boolean sprite;
    private float kdiv = 23f;
    private float centerY = 0f;

    private ModelPreview(PackIndex pack, ResourceManager rm) {
        this.pack = pack;
        this.rm = rm;
    }

    static ModelPreview build(PackIndex pack, ResourceManager rm, String itemId) {
        try {
            ModelPreview mp = new ModelPreview(pack, rm);
            JsonObject def = mp.json("minecraft/items/" + itemId + ".json");
            String model = def != null ? firstModel(def) : null;
            if (model == null) model = "minecraft:item/" + itemId;
            mp.load(model);
            return mp.ok ? mp : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------ loading

    private byte[] bytes(String rel) {
        try {
            if (pack != null) {
                byte[] b = pack.readAsset(rel);
                if (b != null) return b;
            }
            int i = rel.indexOf('/');
            List<Resource> rs = rm.getAllResources(Identifier.of(rel.substring(0, i), rel.substring(i + 1)));
            if (!rs.isEmpty()) {
                try (InputStream in = rs.get(0).getInputStream()) {
                    return in.readAllBytes();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private JsonObject json(String rel) {
        byte[] b = bytes(rel);
        if (b == null) return null;
        try {
            JsonElement e = JsonParser.parseString(new String(b, StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

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

    private static String[] id(String s) {
        int i = s.indexOf(':');
        return i < 0 ? new String[]{"minecraft", s} : new String[]{s.substring(0, i), s.substring(i + 1)};
    }

    private Tex tex(String ref, Map<String, String> map) {
        for (int i = 0; i < 8 && ref != null && ref.startsWith("#"); i++) ref = map.get(ref.substring(1));
        if (ref == null || ref.startsWith("#")) return null;
        String[] id = id(ref);
        String key = (pack == null ? "vanilla" : pack.id) + "|" + id[0] + "/" + id[1];
        if (TEX.containsKey(key)) return TEX.get(key);
        if (BAD.contains(key)) return null;
        byte[] b = bytes(id[0] + "/textures/" + id[1] + ".png");
        if (b != null) {
            try {
                Tex t = Textures.register("mp", b);
                TEX.put(key, t);
                return t;
            } catch (Exception ignored) {
            }
        }
        BAD.add(key);
        return null;
    }

    private void load(String modelId) {
        List<JsonObject> chain = new ArrayList<>();
        String cur = modelId;
        boolean generated = false;
        for (int i = 0; i < 10 && cur != null; i++) {
            String[] id = id(cur);
            if (id[1].equals("builtin/generated")) {
                generated = true;
                break;
            }
            if (id[1].startsWith("builtin/")) break;
            JsonObject m = json(id[0] + "/models/" + id[1] + ".json");
            if (m == null) break;
            chain.add(m);
            cur = m.has("parent") ? m.get("parent").getAsString() : null;
        }
        Map<String, String> textures = new HashMap<>();
        JsonArray elements = null;
        for (int i = chain.size() - 1; i >= 0; i--) {
            JsonObject m = chain.get(i);
            if (m.has("textures") && m.get("textures").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : m.getAsJsonObject("textures").entrySet()) {
                    if (e.getValue().isJsonPrimitive()) textures.put(e.getKey(), e.getValue().getAsString());
                }
            }
            if (m.has("elements") && m.get("elements").isJsonArray()) elements = m.getAsJsonArray("elements");
        }
        if (elements == null) {
            if (true) return;   // flat sprites are shown flat (only real 3D models get a 3D preview)
            if (!generated && !textures.containsKey("layer0")) return;
            Tex t = tex("#layer0", textures);
            if (t == null) return;
            sprite = true;
            float th = (t.h() > t.w() && t.h() % t.w() == 0) ? t.w() : t.h();
            for (int i = 0; i < 9; i++) {
                Quad q = new Quad();
                q.layer = true;
                float z = -0.6f + i * 0.15f;
                q.o = new float[]{-8, 8, z};
                q.u = new float[]{16, 0, 0};
                q.v = new float[]{0, -16, 0};
                q.n = new float[]{0, 0, 1};
                q.tex = t;
                q.ru = 0;
                q.rv = 0;
                q.rw = t.w();
                q.rh = th;
                quads.add(q);
            }
            ok = true;
            return;
        }
        for (JsonElement el : elements) {
            if (el.isJsonObject()) element(el.getAsJsonObject(), textures);
        }
        ok = !quads.isEmpty();
    }

    private static float[] arr(JsonObject o, String k, float d) {
        float[] r = {d, d, d};
        if (o.has(k) && o.get(k).isJsonArray()) {
            JsonArray a = o.getAsJsonArray(k);
            for (int i = 0; i < 3 && i < a.size(); i++) r[i] = a.get(i).getAsFloat();
        }
        return r;
    }

    private void element(JsonObject e, Map<String, String> textures) {
        float[] f = arr(e, "from", 0), t = arr(e, "to", 16);
        float dx = t[0] - f[0], dy = t[1] - f[1], dz = t[2] - f[2];
        String axis = null;
        float angle = 0;
        float[] org = {8, 8, 8};
        if (e.has("rotation") && e.get("rotation").isJsonObject()) {
            JsonObject r = e.getAsJsonObject("rotation");
            org = arr(r, "origin", 8);
            if (r.has("axis")) axis = r.get("axis").getAsString();
            if (r.has("angle")) angle = (float) Math.toRadians(r.get("angle").getAsFloat());
        }
        if (!e.has("faces") || !e.get("faces").isJsonObject()) return;
        JsonObject faces = e.getAsJsonObject("faces");
        String[] dirs = {"north", "south", "east", "west", "up", "down"};
        for (String d : dirs) {
            if (!faces.has(d) || !faces.get(d).isJsonObject()) continue;
            JsonObject fc = faces.getAsJsonObject(d);
            Tex tx = fc.has("texture") ? tex(fc.get("texture").getAsString(), textures) : null;
            if (tx == null) continue;
            float[] o, u, v, n;
            float[] uv;
            switch (d) {
                case "north" -> { o = new float[]{t[0], t[1], f[2]}; u = new float[]{-dx, 0, 0}; v = new float[]{0, -dy, 0}; n = new float[]{0, 0, -1};
                    uv = new float[]{16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]}; }
                case "south" -> { o = new float[]{f[0], t[1], t[2]}; u = new float[]{dx, 0, 0}; v = new float[]{0, -dy, 0}; n = new float[]{0, 0, 1};
                    uv = new float[]{f[0], 16 - t[1], t[0], 16 - f[1]}; }
                case "east" -> { o = new float[]{t[0], t[1], t[2]}; u = new float[]{0, 0, -dz}; v = new float[]{0, -dy, 0}; n = new float[]{1, 0, 0};
                    uv = new float[]{16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]}; }
                case "west" -> { o = new float[]{f[0], t[1], f[2]}; u = new float[]{0, 0, dz}; v = new float[]{0, -dy, 0}; n = new float[]{-1, 0, 0};
                    uv = new float[]{f[2], 16 - t[1], t[2], 16 - f[1]}; }
                case "up" -> { o = new float[]{f[0], t[1], f[2]}; u = new float[]{dx, 0, 0}; v = new float[]{0, 0, dz}; n = new float[]{0, 1, 0};
                    uv = new float[]{f[0], f[2], t[0], t[2]}; }
                default -> { o = new float[]{f[0], f[1], t[2]}; u = new float[]{dx, 0, 0}; v = new float[]{0, 0, -dz}; n = new float[]{0, -1, 0};
                    uv = new float[]{f[0], 16 - t[2], t[0], 16 - f[2]}; }
            }
            if (fc.has("uv") && fc.get("uv").isJsonArray() && fc.getAsJsonArray("uv").size() >= 4) {
                JsonArray a = fc.getAsJsonArray("uv");
                for (int i = 0; i < 4; i++) uv[i] = a.get(i).getAsFloat();
            }
            if (axis != null && angle != 0) {
                o = rotPoint(o, org, axis, angle);
                u = rotVec(u, axis, angle);
                v = rotVec(v, axis, angle);
                n = rotVec(n, axis, angle);
            }
            Quad q = new Quad();
            q.o = new float[]{o[0] - 8, o[1] - 8, o[2] - 8};
            q.u = u;
            q.v = v;
            q.n = n;
            q.tex = tx;
            float tw = tx.w(), th = (tx.h() > tx.w() && tx.h() % tx.w() == 0) ? tx.w() : tx.h();
            q.ru = Math.min(uv[0], uv[2]) / 16f * tw;
            q.rv = Math.min(uv[1], uv[3]) / 16f * th;
            q.rw = Math.abs(uv[2] - uv[0]) / 16f * tw;
            q.rh = Math.abs(uv[3] - uv[1]) / 16f * th;
            quads.add(q);
        }
    }

    private static float[] rotAxis(float[] v, float ax, float ay, float az, float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a), d = ax * v[0] + ay * v[1] + az * v[2];
        float cx = ay * v[2] - az * v[1], cy = az * v[0] - ax * v[2], cz = ax * v[1] - ay * v[0];
        return new float[]{v[0] * c + cx * s + ax * d * (1 - c), v[1] * c + cy * s + ay * d * (1 - c), v[2] * c + cz * s + az * d * (1 - c)};
    }

    private static float[] rotVec(float[] p, String axis, float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return switch (axis) {
            case "x" -> new float[]{p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c};
            case "y" -> new float[]{p[0] * c + p[2] * s, p[1], -p[0] * s + p[2] * c};
            default -> new float[]{p[0] * c - p[1] * s, p[0] * s + p[1] * c, p[2]};
        };
    }

    private static float[] rotPoint(float[] p, float[] org, String axis, float a) {
        float[] r = rotVec(new float[]{p[0] - org[0], p[1] - org[1], p[2] - org[2]}, axis, a);
        return new float[]{r[0] + org[0], r[1] + org[1], r[2] + org[2]};
    }


    // ------------------------------------------------------------ procedural boxes (entity textures)

    /** Adds a box in view space (y up, front = +z) using the Minecraft box UV layout. */
    private void addBox(Tex t, float u0, float v0, float su, float uw, float uh, float ud,
                        float cx, float cy, float cz, float w, float h, float d, float yaw, boolean floats) {
        addBox(t, u0, v0, su, uw, uh, ud, cx, cy, cz, w, h, d, yaw, floats, false, 0);
    }

    private void addBox(Tex t, float u0, float v0, float su, float uw, float uh, float ud,
                        float cx, float cy, float cz, float w, float h, float d, float yaw, boolean floats,
                        boolean tilt, int shade) {
        float a = w / 2, b = h / 2, c = d / 2;
        float[][][] f = {
                // origin, u, v, normal, region(u,v,w,h in uv units)
                {{-a, b, c}, {w, 0, 0}, {0, -h, 0}, {0, 0, 1}, {u0 + ud, v0 + ud, uw, uh}},
                {{a, b, -c}, {-w, 0, 0}, {0, -h, 0}, {0, 0, -1}, {u0 + 2 * ud + uw, v0 + ud, uw, uh}},
                {{a, b, c}, {0, 0, -d}, {0, -h, 0}, {1, 0, 0}, {u0 + ud + uw, v0 + ud, ud, uh}},
                {{-a, b, -c}, {0, 0, d}, {0, -h, 0}, {-1, 0, 0}, {u0, v0 + ud, ud, uh}},
                {{-a, b, -c}, {w, 0, 0}, {0, 0, d}, {0, 1, 0}, {u0 + ud, v0, uw, ud}},
                {{-a, -b, c}, {w, 0, 0}, {0, 0, -d}, {0, -1, 0}, {u0 + ud + uw, v0, uw, ud}},
        };
        for (float[][] q : f) {
            Quad k = new Quad();
            float[] o = rotVec(q[0], "y", yaw), uu = rotVec(q[1], "y", yaw), vv = rotVec(q[2], "y", yaw), nn = rotVec(q[3], "y", yaw);
            if (tilt) {   // same tilt the game applies to the crystal cubes: 60 deg around (sin45, 0, sin45)
                float ax = 0.7071f, az = 0.7071f, ang = (float) Math.toRadians(60);
                o = rotAxis(o, ax, 0, az, ang);
                uu = rotAxis(uu, ax, 0, az, ang);
                vv = rotAxis(vv, ax, 0, az, ang);
                nn = rotAxis(nn, ax, 0, az, ang);
            }
            k.o = new float[]{o[0] + cx, o[1] + cy, o[2] + cz};
            k.u = uu;
            k.v = vv;
            k.n = nn;
            k.shade = shade;
            k.tex = t;
            k.ru = q[4][0] * su;
            k.rv = q[4][1] * su;
            k.rw = q[4][2] * su;
            k.rh = q[4][3] * su;
            k.floats = floats;
            quads.add(k);
        }
    }

    /** End crystal: bedrock base (vanilla 64x32 layout), outer glass, inner glass and core; floats and spins. */
    static ModelPreview crystal(Tex t) {
        ModelPreview m = new ModelPreview(null, null);
        boolean wide = t.w() >= t.h() * 2;
        float su = t.w() / (wide ? 64f : 32f);
        float coreU = wide ? 32f : 0f, coreV = wide ? 0f : 16f;
        if (wide) m.addBox(t, 0, 16, su, 12, 4, 12, 0, -8f, 0, 12, 4, 12, 0f, false, false, 0x70000000);
        m.addBox(t, coreU, coreV, su, 8, 8, 8, 0, 4f, 0, 4.6f, 4.6f, 4.6f, 1.2f, true, true, 0);
        m.addBox(t, 0, 0, su, 8, 8, 8, 0, 4f, 0, 6.4f, 6.4f, 6.4f, 0.55f, true, true, 0);
        m.addBox(t, 0, 0, su, 8, 8, 8, 0, 4f, 0, 8f, 8f, 8f, 0f, true, true, 0);
        m.kdiv = 25f;
        m.centerY = 1.5f;
        m.ok = true;
        return m;
    }

    /** Armor on a blocky mannequin; only for vanilla-style 2:1 humanoid layer textures. */
    static ModelPreview mannequin(Tex layer, Tex legs) {
        if (layer.w() != layer.h() * 2) return null;
        ModelPreview m = new ModelPreview(null, null);
        float su = layer.w() / 64f;
        Tex lg = legs != null && legs.w() == legs.h() * 2 ? legs : layer;
        float lsu = lg.w() / 64f;
        m.addBox(layer, 40, 16, su, 4, 12, 4, -6, 2, 0, 4, 12, 4, 0f, false);
        m.addBox(lg, 0, 16, lsu, 4, 12, 4, -2, -10, 0, 4, 12, 4, 0f, false);
        m.addBox(lg, 0, 16, lsu, 4, 12, 4, 2, -10, 0, 4, 12, 4, 0f, false);
        m.addBox(layer, 16, 16, su, 8, 12, 4, 0, 2, 0, 8, 12, 4, 0f, false);
        m.addBox(layer, 0, 0, su, 8, 8, 8, 0, 12, 0, 8, 8, 8, 0f, false);
        m.addBox(layer, 40, 16, su, 4, 12, 4, 6, 2, 0, 4, 12, 4, 0f, false);
        m.kdiv = 37f;
        m.ok = true;
        return m;
    }

    /** Shield as a thin slab (64x64 entity texture). */
    static ModelPreview shield(Tex t) {
        if (t.w() != t.h()) return null;
        ModelPreview m = new ModelPreview(null, null);
        m.addBox(t, 0, 0, t.w() / 64f, 12, 22, 1, 0, 0, 0, 12, 22, 1, 0f, false);
        m.kdiv = 27f;
        m.ok = true;
        return m;
    }

    // ------------------------------------------------------------ drawing

    private static float[] proj(float[] p, double cy, double sy, double cp, double sp) {
        double x1 = p[0] * cy + p[2] * sy, z1 = -p[0] * sy + p[2] * cy;
        double y2 = p[1] * cp - z1 * sp, z2 = p[1] * sp + z1 * cp;
        return new float[]{(float) x1, (float) -y2, (float) z2};
    }

    private static void drawQuad(DrawContext c, Tex t, float ox, float oy, float ax, float ay, float bx, float by,
                                 float u, float v, float rw, float rh, int shade) {
        Matrix3x2fStack m = c.getMatrices();
        m.pushMatrix();
        m.translate(ox, oy);
        m.mul(new Matrix3x2f(ax / 16f, ay / 16f, bx / 16f, by / 16f, 0f, 0f));
        c.drawTexture(RenderPipelines.GUI_TEXTURED, t.id(), 0, 0, u, v, 16, 16,
                Math.max(1, Math.round(rw)), Math.max(1, Math.round(rh)), t.w(), t.h());
        if (shade != 0) c.fill(0, 0, 16, 16, shade);
        m.popMatrix();
    }

    private static float[] off(Quad q, float bob) {
        return q.floats ? new float[]{q.o[0], q.o[1] + bob, q.o[2]} : q.o;
    }

    void draw(DrawContext c, int x, int y, int size) {
        double yaw = (System.currentTimeMillis() % 8000L) / 8000.0 * Math.PI * 2;
        double pitch = 0.38;
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        float k = (size - 6) / (sprite ? 19f : kdiv);
        float cx = x + size / 2f, cyy = y + size / 2f + centerY * k * (float) cp;
        float bob = (float) Math.sin(System.currentTimeMillis() / 380.0) * 1.2f;
        List<float[]> order = new ArrayList<>();   // {depth, index}
        for (int i = 0; i < quads.size(); i++) {
            Quad q = quads.get(i);
            float[] p = proj(off(q, bob), cy, sy, cp, sp), a = proj(q.u, cy, sy, cp, sp), b = proj(q.v, cy, sy, cp, sp);
            float[] n = proj(q.n, cy, sy, cp, sp);
            if (!q.layer && n[2] <= 0) continue;
            order.add(new float[]{p[2] + (a[2] + b[2]) / 2f, i});
        }
        order.sort(Comparator.comparingDouble(o -> o[0]));
        for (float[] od : order) {
            Quad q = quads.get((int) od[1]);
            float[] p = proj(off(q, bob), cy, sy, cp, sp), a = proj(q.u, cy, sy, cp, sp), b = proj(q.v, cy, sy, cp, sp);
            float[] n = proj(q.n, cy, sy, cp, sp);
            float ox = cx + p[0] * k, oy = cyy + p[1] * k;
            if (q.layer && n[2] <= 0) {         // seen from behind: mirrored, keeps orientation positive
                ox += a[0] * k;
                oy += a[1] * k;
                drawQuad(c, q.tex, ox, oy, -a[0] * k, -a[1] * k, b[0] * k, b[1] * k, q.ru, q.rv, q.rw, q.rh, q.shade);
            } else {
                drawQuad(c, q.tex, ox, oy, a[0] * k, a[1] * k, b[0] * k, b[1] * k, q.ru, q.rv, q.rw, q.rh, q.shade);
            }
        }
    }
}
