package dev.jkmcfii.schematicbrowser;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class SchematicBrowserClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("schematicbrowser", "main")
    );
    private static KeyMapping openBrowser;

    @Override
    public void onInitializeClient() {
        openBrowser = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.schematicbrowser.open",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_O,
                CATEGORY
        ));
        CatalogManager.initialize();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openBrowser.consumeClick()) {
                // Open only during gameplay, not while another menu or chat screen is active.
                if (client.player != null && client.screen == null) {
                    client.setScreen(new BrowserScreen());
                } else if (client.player != null && client.screen != null) {
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
