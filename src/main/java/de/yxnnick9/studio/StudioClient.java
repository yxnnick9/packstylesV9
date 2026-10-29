package de.yxnnick9.studio;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class StudioClient implements ClientModInitializer {
    public static final String ID = "yxnnick9studio";
    public static KeyBinding OPEN;

    @Override
    public void onInitializeClient() {
        Config.load();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of(ID, "main"));
        OPEN = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key." + ID + ".open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, cat));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN.wasPressed()) {
                if (!(mc.currentScreen instanceof StudioScreen)) {
                    mc.setScreen(new StudioScreen());
                }
            }
        });
    }
}
