package dev.jkmcfii.schematicbrowser;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CatalogSetupScreen extends Screen {
    private final Screen parent;
    private EditBox nameBox;
    private EditBox urlBox;
    private EditBox hostsBox;
    private String error = "";

    CatalogSetupScreen(Screen parent) {
        super(Component.literal("Add schematic catalog"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(440, width - 32);
        int left = (width - panelWidth) / 2 + 16;
        int fieldWidth = panelWidth - 32;
        nameBox = new EditBox(font, left, 62, fieldWidth, 20, Component.literal("Catalog name"));
        nameBox.setHint(Component.literal("My schematic catalog"));
        addRenderableWidget(nameBox);
        urlBox = new EditBox(font, left, 105, fieldWidth, 20, Component.literal("Feed URL"));
        urlBox.setHint(Component.literal("https://example.com/schematics.json"));
        addRenderableWidget(urlBox);
        hostsBox = new EditBox(font, left, 148, fieldWidth, 20, Component.literal("Allowed hosts"));
        hostsBox.setHint(Component.literal("example.com, cdn.example.com"));
        addRenderableWidget(hostsBox);
        setInitialFocus(nameBox);

        addRenderableWidget(Button.builder(Component.literal("Save and refresh"), button -> save())
                .bounds(width / 2 - 104, height - 38, 130, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(width / 2 + 32, height - 38, 72, 20).build());
    }

    private void save() {
        String name = nameBox.getValue().trim();
        String url = urlBox.getValue().trim();
        List<String> hosts = new ArrayList<>();
        for (String value : hostsBox.getValue().split(",")) {
            String host = value.trim().toLowerCase(Locale.ROOT);
            if (!host.isEmpty()) hosts.add(host);
        }
        try {
            if (name.isBlank()) throw new IllegalArgumentException("Enter a catalog name.");
            if (hosts.isEmpty()) throw new IllegalArgumentException("Enter at least one allowed host.");
            validateHostList(url, hosts);
            Path config = SchematicBrowserClient.minecraft().gameDirectory.toPath()
                    .resolve("config").resolve("schematicbrowser").resolve("catalogs.json");
            Files.createDirectories(config.getParent());
            JsonArray oldFeeds = Files.exists(config)
                    ? JsonParser.parseString(Files.readString(config)).getAsJsonArray() : new JsonArray();
            JsonArray feeds = new JsonArray();
            for (JsonElement element : oldFeeds) {
                if (element.isJsonObject()) {
                    String existingUrl = element.getAsJsonObject().has("url")
                            ? element.getAsJsonObject().get("url").getAsString() : "";
                    if (!existingUrl.equals("https://example.org/schematics.json")) feeds.add(element);
                } else {
                    feeds.add(element);
                }
            }
            JsonObject feed = new JsonObject();
            feed.addProperty("name", name);
            feed.addProperty("url", url);
            JsonArray allowedHosts = new JsonArray();
            hosts.forEach(allowedHosts::add);
            feed.add("allowedHosts", allowedHosts);
            feeds.add(feed);
            Files.writeString(config, new GsonBuilder().setPrettyPrinting().create().toJson(feeds) + System.lineSeparator());
            CatalogManager.refresh();
            minecraft.setScreen(new BrowserScreen());
        } catch (IllegalArgumentException | IOException exception) {
            error = exception.getMessage() == null ? "Couldn't save catalog settings." : exception.getMessage();
        } catch (RuntimeException exception) {
            error = "Couldn't read catalogs.json. Fix or remove that file, then try again.";
        }
    }

    private static void validateHostList(String url, List<String> allowedHosts) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Enter a valid HTTPS feed URL.");
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                || uri.getPort() != -1 && uri.getPort() != 443) {
            throw new IllegalArgumentException("Feed URL must use HTTPS and cannot include credentials or a custom port.");
        }
        for (String allowed : allowedHosts) {
            if (!allowed.matches("(?i)[a-z0-9.-]+") || allowed.startsWith(".") || allowed.endsWith(".")
                    || allowed.contains("..")) {
                throw new IllegalArgumentException("Allowed hosts must be hostnames only, separated by commas.");
            }
        }
        boolean permitted = allowedHosts.stream().anyMatch(allowed -> host.equalsIgnoreCase(allowed)
                || host.toLowerCase(Locale.ROOT).endsWith("." + allowed));
        if (!permitted) throw new IllegalArgumentException("The feed URL hostname must be in the allowed hosts list.");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        int panelWidth = Math.min(440, width - 32);
        int left = (width - panelWidth) / 2;
        graphics.fill(left, 20, left + panelWidth, height - 20, 0xE8172527);
        graphics.drawCenteredString(font, "ADD A CATALOG", width / 2, 32, 0xFFFFFFFF);
        graphics.drawString(font, "Catalog name", left + 16, 51, 0xFFE1E9E5);
        graphics.drawString(font, "HTTPS feed URL (a JSON array of schematic entries)", left + 16, 94, 0xFFE1E9E5);
        graphics.drawString(font, "Allowed download/thumbnail hosts, comma-separated", left + 16, 137, 0xFFE1E9E5);
        graphics.drawString(font, "The feed host must be listed; redirects are blocked.", left + 16, 178, 0xFFB6CDC4);
        graphics.drawCenteredString(font, error, width / 2, 202, 0xFFFF8080);
        graphics.drawCenteredString(font, "Downloads are saved to the Minecraft schematics folder.", width / 2,
                height - 57, 0xFFB6CDC4);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
