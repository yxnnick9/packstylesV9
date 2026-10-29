package de.yxnnick9.studio;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Draws a panorama sphere (equirectangular texture) in place of the vanilla stars.
 *
 * Technique (sphere mesh 96x32 quads, radius 100, own render pass with the sky pipeline, texture bound as Sampler0,
 * DynamicTransforms uniform built from the model-view matrix) follows the approach of "Customization Studio"
 * by Marv (MIT License) - see NOTICE. Parts of the 1.21.11 render API that are only known by their intermediary
 * names are called through reflection, so a failure just disables the custom sky and keeps the vanilla one.
 */
public final class SkyRenderer {
    private static final int ROWS = 32, COLS = 96, INDICES = ROWS * COLS * 6;
    public static final Identifier TEXTURE = Identifier.of(StudioClient.ID, "textures/sky/active.png");

    private static final class Refl {
        Object format, quads, pipeline;
        Class<?> drawMode;
        Constructor<?> builderCtor;
        Method alloc, vertex, tex, color, end, getBuffer, write, seq, seqIndexBuffer, seqIndexType, fbColor, fbDepth,
                texView, texSampler, bindTexture, setIndexBuffer;
    }

    private static Refl R;
    private static GpuBuffer vertices;
    private static boolean failed;
    private static int errors;
    private static long frame;
    private static long resourceCheckedAt = -1000;
    private static boolean resourceOk;
    private static long lastDrawn = -1000;

    private SkyRenderer() {
    }

    public static boolean active() {
        Config c = Config.I;
        return !failed && c.skyOn && c.skyApplied;
    }

    /** True when the vanilla sky parts should be hidden (custom sky is really being drawn). */
    public static boolean shouldHide() {
        return active() && resourceOk && Config.I.skyHideVanilla && frame - lastDrawn < 10;
    }

    private static boolean textureAvailable(MinecraftClient mc) {
        if (frame - resourceCheckedAt > 90) {
            resourceCheckedAt = frame;
            try {
                resourceOk = mc.getResourceManager().getResource(TEXTURE).isPresent();
            } catch (Throwable t) {
                resourceOk = false;
            }
        }
        return resourceOk;
    }

    private static void init() throws Exception {
        if (R != null) return;
        Refl x = new Refl();
        Class<?> cBuilder = Class.forName("net.minecraft.class_287");
        Class<?> cConsumer = Class.forName("net.minecraft.class_4588");
        Class<?> cAlloc = Class.forName("net.minecraft.class_9799");
        Class<?> cBuilt = Class.forName("net.minecraft.class_9801");
        Class<?> cFormats = Class.forName("net.minecraft.class_290");
        Class<?> cPipelines = Class.forName("net.minecraft.class_10799");
        x.drawMode = Class.forName("com.mojang.blaze3d.vertex.VertexFormat$class_5596");
        x.format = cFormats.getField("field_1575").get(null);
        x.quads = x.drawMode.getField("field_27382").get(null);
        x.pipeline = cPipelines.getField("field_56875").get(null);
        x.alloc = cAlloc.getMethod("method_72201", int.class);
        x.builderCtor = cBuilder.getConstructor(cAlloc, x.drawMode, VertexFormat.class);
        x.vertex = cBuilder.getMethod("method_22912", float.class, float.class, float.class);
        x.tex = cConsumer.getMethod("method_22913", float.class, float.class);
        x.color = cConsumer.getMethod("method_39415", int.class);
        x.end = cBuilder.getMethod("method_60800");
        x.getBuffer = cBuilt.getMethod("method_60818");
        x.seq = RenderSystem.class.getMethod("getSequentialBuffer", x.drawMode);
        for (Method m : RenderPass.class.getMethods()) {
            if (m.getName().equals("bindTexture") && m.getParameterCount() == 3) x.bindTexture = m;
            if (m.getName().equals("setIndexBuffer") && m.getParameterCount() == 2) x.setIndexBuffer = m;
        }
        if (x.bindTexture == null || x.setIndexBuffer == null) throw new NoSuchMethodException("RenderPass methods");
        R = x;
    }

    /** Equirectangular sphere point (same mapping as the textures: u = longitude, v = latitude from the top). */
    private static float[] point(double u, double v) {
        double lat = (0.5 - v) * Math.PI, lon = u * Math.PI * 2;
        return new float[]{(float) (Math.cos(lat) * Math.cos(lon) * 100), (float) (Math.sin(lat) * 100),
                (float) (Math.cos(lat) * Math.sin(lon) * 100)};
    }

    private static void vtx(Object builder, double u, double v) throws Exception {
        float[] p = point(u, v);
        Object consumer = R.vertex.invoke(builder, p[0], p[1], p[2]);
        R.tex.invoke(consumer, (float) u, (float) v);
        R.color.invoke(consumer, -1);
    }

    private static void build() throws Exception {
        init();
        VertexFormat format = (VertexFormat) R.format;
        Object allocator = R.alloc.invoke(null, ROWS * COLS * 4 * format.getVertexSize());
        try {
            Object builder = R.builderCtor.newInstance(allocator, R.quads, format);
            for (int i = 0; i < ROWS; i++) {
                for (int j = 0; j < COLS; j++) {
                    double u = j / (double) COLS, v = i / (double) ROWS, du = 1.0 / COLS, dv = 1.0 / ROWS;
                    vtx(builder, u, v);
                    vtx(builder, u, v + dv);
                    vtx(builder, u + du, v + dv);
                    vtx(builder, u + du, v);
                }
            }
            Object built = R.end.invoke(builder);
            try {
                ByteBuffer buf = (ByteBuffer) R.getBuffer.invoke(built);
                GpuDevice device = RenderSystem.getDevice();
                vertices = device.createBuffer(() -> "Yxnnick9 sky", 32, buf);
            } finally {
                ((AutoCloseable) built).close();
            }
        } finally {
            ((AutoCloseable) allocator).close();
        }
    }

    /** Called from the mixin in place of the vanilla star rendering. Returns true if the sky was drawn. */
    public static boolean draw() {
        frame++;
        if (!active()) return false;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getOverlay() != null || !textureAvailable(mc)) return false;
            if (vertices == null) build();
            Refl r = R;

            Object fb = mc.getFramebuffer();
            if (r.fbColor == null) {
                r.fbColor = fb.getClass().getMethod("method_71639");
                r.fbDepth = fb.getClass().getMethod("method_71640");
            }
            Object tex = mc.getTextureManager().getTexture(TEXTURE);
            if (r.texView == null) {
                r.texView = tex.getClass().getMethod("method_71659");
                r.texSampler = tex.getClass().getMethod("method_75484");
            }
            float b = Config.I.skyBrightness;
            Object uniforms = RenderSystem.getDynamicUniforms();
            if (r.write == null) {
                r.write = uniforms.getClass().getMethod("method_71106", Matrix4fc.class, Vector4fc.class, Vector3fc.class, Matrix4fc.class);
            }
            GpuBufferSlice slice = (GpuBufferSlice) r.write.invoke(uniforms, RenderSystem.getModelViewMatrix(),
                    new Vector4f(b, b, b, 1f), new Vector3f(), new Matrix4f());
            Object seq = r.seq.invoke(null, r.quads);
            if (r.seqIndexBuffer == null) {
                r.seqIndexBuffer = seq.getClass().getMethod("method_68274", int.class);
                r.seqIndexType = seq.getClass().getMethod("method_31924");
            }
            GpuBuffer indexBuffer = (GpuBuffer) r.seqIndexBuffer.invoke(seq, INDICES);
            Object indexType = r.seqIndexType.invoke(seq);
            GpuTextureView color = (GpuTextureView) r.fbColor.invoke(fb);
            GpuTextureView depth = (GpuTextureView) r.fbDepth.invoke(fb);
            GpuTextureView texView = (GpuTextureView) r.texView.invoke(tex);
            Object sampler = r.texSampler.invoke(tex);

            GpuDevice device = RenderSystem.getDevice();
            try (RenderPass pass = device.createCommandEncoder().createRenderPass(() -> "Yxnnick9 sky", color,
                    OptionalInt.empty(), depth, OptionalDouble.empty())) {
                pass.setPipeline((RenderPipeline) r.pipeline);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", slice);
                r.bindTexture.invoke(pass, "Sampler0", texView, sampler);
                pass.setVertexBuffer(0, vertices);
                r.setIndexBuffer.invoke(pass, indexBuffer, indexType);
                pass.drawIndexed(0, 0, INDICES, 1);
            }
            errors = 0;
            lastDrawn = frame;
            return true;
        } catch (Throwable t) {
            if (++errors >= 3) {
                failed = true;
                System.err.println("[Yxnnick9 Studio] Custom sky unavailable; keeping vanilla sky: " + t);
            }
            return false;
        }
    }
}
