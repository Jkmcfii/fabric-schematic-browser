package dev.jkmcfii.schematicbrowser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

final class CatalogManager {
    private static final long MAX_CATALOG_BYTES = 2L * 1024 * 1024;
    private static final long MAX_SCHEMATIC_BYTES = 50L * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static Path configPath;
    private static final List<SchematicEntry> entries = new ArrayList<>();
    private static String status = "Add trusted catalog feeds to config/schematicbrowser/catalogs.json.";

    private CatalogManager() {}

    static void initialize() {
        Path configDir = SchematicBrowserClient.minecraft().gameDirectory.toPath()
                .resolve("config").resolve("schematicbrowser");
        configPath = configDir.resolve("catalogs.json");
        try {
            Files.createDirectories(configDir);
            if (!Files.exists(configPath)) {
                Files.writeString(configPath, "[\n  {\n    \"name\": \"Example catalog\",\n    \"url\": \"https://example.org/schematics.json\",\n    \"allowedHosts\": [\"example.org\"]\n  }\n]\n");
                status = "Edit catalogs.json to add catalog feeds, then press Refresh.";
            }
        } catch (IOException e) {
            status = "Couldn't create config: " + e.getMessage();
        }
    }

    static synchronized List<SchematicEntry> entries() {
        return List.copyOf(entries);
    }

    static synchronized String status() {
        return status;
    }

    static void refresh() {
        final JsonArray feeds;
        try {
            feeds = JsonParser.parseString(Files.readString(configPath)).getAsJsonArray();
        } catch (Exception e) {
            setStatus("Invalid catalogs.json: " + e.getMessage());
            return;
        }

        setStatus("Loading catalogs...");
        List<CompletableFuture<List<SchematicEntry>>> jobs = new ArrayList<>();
        for (JsonElement element : feeds) {
            if (!element.isJsonObject()) continue;
            JsonObject feed = element.getAsJsonObject();
            String feedName = string(feed, "name", "Catalog");
            String feedUrl = string(feed, "url", "");
            List<String> allowedHosts = new ArrayList<>();
            if (feed.has("allowedHosts") && feed.get("allowedHosts").isJsonArray()) {
                for (JsonElement host : feed.getAsJsonArray("allowedHosts")) {
                    if (host.isJsonPrimitive()) allowedHosts.add(host.getAsString().toLowerCase(Locale.ROOT));
                }
            }
            jobs.add(fetchFeed(feedName, feedUrl, allowedHosts));
        }
        CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) -> {
            List<SchematicEntry> loaded = new ArrayList<>();
            for (CompletableFuture<List<SchematicEntry>> job : jobs) {
                try { loaded.addAll(job.join()); } catch (Exception ignoredError) { }
            }
            synchronized (CatalogManager.class) {
                entries.clear();
                entries.addAll(loaded);
                status = "Loaded " + loaded.size() + " schematic(s).";
            }
        });
    }

    private static CompletableFuture<List<SchematicEntry>> fetchFeed(String feedName, String url, List<String> allowedHosts) {
        try {
            URI uri = checkedHttpsUri(url, allowedHosts);
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "SchematicBrowser/1.0 (Minecraft mod)").GET().build();
            return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
                if (response.statusCode() != 200 || response.body().length > MAX_CATALOG_BYTES) return List.of();
                try {
                    JsonArray array = JsonParser.parseString(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
                    List<SchematicEntry> result = new ArrayList<>();
                    for (JsonElement element : array) {
                        if (!element.isJsonObject()) continue;
                        JsonObject item = element.getAsJsonObject();
                        String title = string(item, "name", "Untitled schematic");
                        String download = string(item, "downloadUrl", "");
                        String thumbnail = string(item, "thumbnailUrl", "");
                        String description = string(item, "description", "");
                        String sha256 = string(item, "sha256", "");
                        try {
                            checkedHttpsUri(download, allowedHosts);
                            if (!thumbnail.isBlank()) checkedHttpsUri(thumbnail, allowedHosts);
                            if (!sha256.isBlank() && !sha256.matches("(?i)[0-9a-f]{64}")) continue;
                            result.add(new SchematicEntry(title, feedName, description, download, thumbnail, sha256, allowedHosts));
                        } catch (IllegalArgumentException ignored) { }
                    }
                    return result;
                } catch (Exception ignored) { return List.of(); }
            }).exceptionally(error -> List.of());
        } catch (Exception e) {
            return CompletableFuture.completedFuture(List.of());
        }
    }

    static void download(SchematicEntry entry, java.util.function.Consumer<String> completion) {
        final URI uri;
        try {
            uri = checkedHttpsUri(entry.downloadUrl(), entry.allowedHosts());
        } catch (IllegalArgumentException e) {
            completion.accept("Blocked unsafe download URL.");
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(45))
                .header("User-Agent", "SchematicBrowser/1.0 (Minecraft mod)").GET().build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, error) -> {
            if (error != null) { completion.accept("Download failed: " + error.getMessage()); return; }
            if (response.statusCode() != 200) { completion.accept("Download failed (HTTP " + response.statusCode() + ")."); return; }
            byte[] data = response.body();
            if (data.length == 0 || data.length > MAX_SCHEMATIC_BYTES) {
                completion.accept("Rejected file: empty or larger than 50 MiB."); return;
            }
            String filename = safeFilename(entry.name());
            if (!filename.toLowerCase(Locale.ROOT).endsWith(".schem") && !filename.toLowerCase(Locale.ROOT).endsWith(".schematic")) {
                filename += ".schem";
            }
            if (!hasSchematicLikeHeader(data)) {
                completion.accept("Rejected file: it doesn't look like a gzip-compressed or raw NBT schematic."); return;
            }
            if (!entry.sha256().isBlank() && !sha256(data).equalsIgnoreCase(entry.sha256())) {
                completion.accept("Rejected file: SHA-256 checksum did not match the catalog."); return;
            }
            try {
                Path folder = SchematicBrowserClient.minecraft().gameDirectory.toPath().resolve("schematics");
                Files.createDirectories(folder);
                Path target = folder.resolve(filename).normalize();
                if (!target.getParent().equals(folder.normalize())) throw new IOException("Invalid filename");
                Files.write(target, data);
                completion.accept("Saved " + filename + " to " + folder.toAbsolutePath());
            } catch (IOException e) {
                completion.accept("Couldn't save schematic: " + e.getMessage());
            }
        });
    }

    private static URI checkedHttpsUri(String value, List<String> allowedHosts) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                    || uri.getPort() != -1 && uri.getPort() != 443) throw new IllegalArgumentException("HTTPS URL required");
            boolean allowed = allowedHosts.stream().anyMatch(candidate -> host.equalsIgnoreCase(candidate)
                    || host.toLowerCase(Locale.ROOT).endsWith("." + candidate.toLowerCase(Locale.ROOT)));
            if (!allowed) throw new IllegalArgumentException("Host not allowlisted");
            return uri;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unsafe URL", e);
        }
    }

    private static boolean hasSchematicLikeHeader(byte[] bytes) {
        // Schematic formats commonly use gzip-compressed or uncompressed NBT.
        return bytes.length >= 2 && ((bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b
                || (bytes[0] & 0xff) == 0x0a);
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String safeFilename(String name) {
        String safe = name.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        safe = safe.replaceAll("[. ]+$", "");
        if (safe.isBlank() || safe.equals(".") || safe.equals("..")) safe = "schematic";
        return safe.length() > 80 ? safe.substring(0, 80) : safe;
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    private static void setStatus(String value) {
        synchronized (CatalogManager.class) { status = value; }
    }

    record SchematicEntry(String name, String source, String description, String downloadUrl,
                          String thumbnailUrl, String sha256, List<String> allowedHosts) {}
}
