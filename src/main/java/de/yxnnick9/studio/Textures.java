package de.yxnnick9.studio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;

record Tex(Identifier id, int w, int h) {
}

final class Textures {
    private static int counter = 0;

    private Textures() {
    }

    static Tex register(String path, byte[] png) throws IOException {
        NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
        Identifier id = Identifier.of(StudioClient.ID, path + "_" + (counter++));
        MinecraftClient.getInstance().getTextureManager()
                .registerTexture(id, new NativeImageBackedTexture(() -> id.toString(), img));
        return new Tex(id, img.getWidth(), img.getHeight());
    }

    static void destroy(Tex t) {
        if (t != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(t.id());
    }
}
