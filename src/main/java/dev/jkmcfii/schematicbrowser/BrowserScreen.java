package dev.jkmcfii.schematicbrowser;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

final class BrowserScreen extends Screen {
    private EditBox searchBox;
    private String message = "Press Refresh after adding catalog feeds in config/schematicbrowser/catalogs.json.";
    private int firstVisible;
    private List<CatalogManager.SchematicEntry> filtered = List.of();

    BrowserScreen() {
        super(Component.literal("Schematic Browser"));
    }

    @Override
    protected void init() {
        int left = width / 2 - 150;
        searchBox = new EditBox(font, left, 34, 300, 20, Component.literal("Search schematics"));
        searchBox.setHint(Component.literal("Search by name, source, or description"));
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(Component.literal("Refresh catalogs"), button -> {
            CatalogManager.refresh();
            message = "Refreshing catalog feeds...";
        }).bounds(left, 59, 145, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(left + 155, 59, 145, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Previous"), button -> {
            firstVisible = Math.max(0, firstVisible - 5);
        }).bounds(left, height - 31, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Next"), button -> {
            firstVisible = Math.min(Math.max(0, filtered.size() - 5), firstVisible + 5);
        }).bounds(left + 210, height - 31, 90, 20).build());
        reloadFiltered();
    }

    private void reloadFiltered() {
        String query = searchBox == null ? "" : searchBox.getValue().toLowerCase(Locale.ROOT).trim();
        filtered = CatalogManager.entries().stream().filter(entry ->
                (entry.name() + " " + entry.source() + " " + entry.description()).toLowerCase(Locale.ROOT).contains(query)
        ).toList();
        firstVisible = Math.min(firstVisible, Math.max(0, filtered.size() - 1));
    }

    @Override
    public void tick() {
        reloadFiltered();
        message = CatalogManager.status();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        reloadFiltered();
        int startY = 91;
        int left = width / 2 - 150;
        for (int i = firstVisible; i < Math.min(filtered.size(), firstVisible + 5); i++) {
            CatalogManager.SchematicEntry entry = filtered.get(i);
            int y = startY + (i - firstVisible) * 47;
            graphics.fill(left, y, left + 300, y + 42, 0xAA20332F);
            graphics.drawString(font, trim(entry.name(), 37), left + 7, y + 5, 0xFFFFFF);
            graphics.drawString(font, trim(entry.source() + " · " + entry.description(), 52), left + 7, y + 19, 0xB6D2C8);
            if (!entry.thumbnailUrl().isBlank()) {
                graphics.drawString(font, "Thumbnail listed", left + 7, y + 31, 0x8BC7B4);
            }
            addRenderableWidget(Button.builder(Component.literal("Download"), button -> {
                message = "Downloading " + entry.name() + "...";
                CatalogManager.download(entry, result -> SchematicBrowserClient.minecraft().execute(() -> message = result));
            }).bounds(left + 222, y + 10, 70, 22).build());
        }
        graphics.drawCenteredString(font, filtered.isEmpty() ? "No schematics found" : (firstVisible + 1) + "–" + Math.min(filtered.size(), firstVisible + 5) + " of " + filtered.size(), width / 2, height - 44, 0xFFFFFF);
        graphics.drawCenteredString(font, trim(message, 95), width / 2, height - 56, 0xFFE59A);
        super.render(graphics, mouseX, mouseY, delta);
    }

    private String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
