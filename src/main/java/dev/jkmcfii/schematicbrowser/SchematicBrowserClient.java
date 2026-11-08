package dev.jkmcfii.schematicbrowser;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class SchematicBrowserClient implements ClientModInitializer {
    private static KeyMapping openBrowser;

    @Override
    public void onInitializeClient() {
        openBrowser = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.schematicbrowser.open",
                GLFW.GLFW_KEY_O,
                "category.schematicbrowser"
        ));
        CatalogManager.initialize();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openBrowser.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new BrowserScreen());
                } else if (client.player != null) {
                    client.player.displayClientMessage(
                            Component.literal("Close the current screen before opening the schematic browser."), true);
                }
            }
        });
    }

    static Minecraft minecraft() {
        return Minecraft.getInstance();
    }
}
