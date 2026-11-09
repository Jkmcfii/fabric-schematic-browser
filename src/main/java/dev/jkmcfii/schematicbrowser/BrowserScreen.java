package dev.jkmcfii.schematicbrowser;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class BrowserScreen extends Screen {
    private static final int PAGE_SIZE = 5;
    private EditBox searchBox;
    private String message = "Search configured schematic catalogs.";
    private int firstVisible;
    private List<CatalogManager.SchematicEntry> filtered = List.of();
    private final List<Button> downloadButtons = new ArrayList<>();

    BrowserScreen() {
        super(Component.literal("Schematic Browser"));
    }

    @Override
    protected void init() {
        downloadButtons.clear();
        int panelWidth = Math.min(520, width - 32);
        int left = (width - panelWidth) / 2;
        int contentLeft = left + 14;
        int contentWidth = panelWidth - 28;

        searchBox = new EditBox(font, contentLeft, 39, contentWidth - 76,
                22, Component.literal("Search schematics"));
        searchBox.setHint(Component.literal("Name, source, or description"));
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(Component.literal("Search"), button -> {
            firstVisible = 0;
            reloadFiltered();
            message = filtered.size() + " result(s) in loaded catalogs.";
        }).bounds(contentLeft + contentWidth - 70, 39, 70, 22).build());

        addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> {
            CatalogManager.refresh();
            message = "Refreshing configured catalogs...";
        }).bounds(contentLeft, 68, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Add catalog"), button ->
                minecraft.setScreen(new CatalogSetupScreen(this)))
                .bounds(contentLeft + 102, 68, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(contentLeft + contentWidth - 76, 68, 76, 20).build());

        int listTop = 100;
        int rowHeight = 48;
        for (int row = 0; row < PAGE_SIZE; row++) {
            int rowIndex = row;
            int y = listTop + row * rowHeight;
            Button download = Button.builder(Component.literal("Download"), button -> downloadVisible(rowIndex))
                    .bounds(contentLeft + contentWidth - 86, y + 10, 78, 24).build();
            download.active = false;
            downloadButtons.add(addRenderableWidget(download));
        }

        addRenderableWidget(Button.builder(Component.literal("‹ Previous"), button -> {
            firstVisible = Math.max(0, firstVisible - PAGE_SIZE);
            updateRows();
        }).bounds(contentLeft, height - 31, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Next ›"), button -> {
            firstVisible = Math.min(Math.max(0, filtered.size() - PAGE_SIZE), firstVisible + PAGE_SIZE);
            updateRows();
        }).bounds(contentLeft + contentWidth - 90, height - 31, 90, 20).build());

        reloadFiltered();
        updateRows();
    }

    private void downloadVisible(int row) {
        int index = firstVisible + row;
        if (index < 0 || index >= filtered.size()) return;
        CatalogManager.SchematicEntry entry = filtered.get(index);
        message = "Downloading " + entry.name() + "...";
        CatalogManager.download(entry, result -> SchematicBrowserClient.minecraft().execute(() -> message = result));
    }

    private void reloadFiltered() {
        String query = searchBox == null ? "" : searchBox.getValue().toLowerCase(Locale.ROOT).trim();
        filtered = CatalogManager.entries().stream().filter(entry ->
                (entry.name() + " " + entry.source() + " " + entry.description())
                        .toLowerCase(Locale.ROOT).contains(query)
        ).toList();
        firstVisible = Math.min(firstVisible, Math.max(0, filtered.size() - 1));
        updateRows();
    }

    private void updateRows() {
        for (int row = 0; row < downloadButtons.size(); row++) {
            int index = firstVisible + row;
            boolean hasEntry = index < filtered.size();
            downloadButtons.get(row).visible = hasEntry;
            downloadButtons.get(row).active = hasEntry;
        }
    }

    @Override
    public void tick() {
        super.tick();
        reloadFiltered();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        int panelWidth = Math.min(520, width - 32);
        int left = (width - panelWidth) / 2;
        int right = left + panelWidth;
        graphics.fill(left, 8, right, height - 7, 0xE8172527);
        graphics.fill(left, 8, right, 33, 0xFF24433E);
        graphics.drawCenteredString(font, "SCHEMATIC LIBRARY", width / 2, 17, 0xFFFFFFFF);
        graphics.drawString(font, "Search loaded catalog sources", left + 14, 30, 0xFFB7D7CA);

        int listTop = 100;
        int rowHeight = 48;
        for (int row = 0; row < PAGE_SIZE; row++) {
            int index = firstVisible + row;
            int y = listTop + row * rowHeight;
            graphics.fill(left + 12, y, right - 12, y + 42, 0xFF233532);
            if (index < filtered.size()) {
                CatalogManager.SchematicEntry entry = filtered.get(index);
                graphics.drawString(font, trim(entry.name(), 42), left + 21, y + 6, 0xFFFFFFFF);
                String details = entry.source();
                if (!entry.description().isBlank()) details += " · " + entry.description();
                graphics.drawString(font, trim(details, 58), left + 21, y + 22, 0xFFB6CDC4);
                if (!entry.thumbnailUrl().isBlank()) {
                    graphics.drawString(font, "Preview available", left + 21, y + 33, 0xFF87C7AD);
                }
            }
        }
        int page = filtered.isEmpty() ? 0 : firstVisible / PAGE_SIZE + 1;
        int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        graphics.drawCenteredString(font, trim(message, 90), width / 2, height - 69, 0xFFFFD98A);
        graphics.drawCenteredString(font, trim(CatalogManager.status(), 90), width / 2, height - 56, 0xFFE1E9E5);
        graphics.drawCenteredString(font, "Page " + page + " / " + pages + "  ·  " + filtered.size() + " results",
                width / 2, height - 43, 0xFFE1E9E5);
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
