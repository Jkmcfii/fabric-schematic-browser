# Schematic Browser

A client-side Fabric mod project targeting Minecraft Java 1.21.11. Press **O** to open the browser. It loads schematic listings from user-configured JSON catalog feeds and saves accepted files to `<minecraft game directory>/schematics/`.

## Build

- JDK 21
- Gradle 8.14 or newer
- Run `gradle build`; the mod jar will be in `build/libs/`.
- Install Fabric Loader for Minecraft 1.21.11 and Fabric API.

## Add catalog sources

Launch once to create `config/schematicbrowser/catalogs.json`, then edit it and add feeds. Each configured catalog is a JSON array with a catalog object:

```json
[
  {
    "name": "My schematic catalog",
    "url": "https://your-site.example/api/schematics.json",
    "allowedHosts": ["your-site.example", "cdn.your-site.example"]
  }
]
```

A feed is a JSON array of entries:

```json
[
  {
    "name": "Small castle",
    "description": "A compact survival castle",
    "downloadUrl": "https://your-site.example/files/castle.schem",
    "thumbnailUrl": "https://your-site.example/images/castle.png",
    "sha256": "optional-64-character-hex-sha256"
  }
]
```

Every feed URL, download URL, and thumbnail URL must use HTTPS and a hostname listed in that feed's `allowedHosts`. Redirects are disabled. The catalog UI currently shows that a thumbnail URL exists, but does not yet render the remote image. Sites without a documented public feed/API need a permitted adapter or their operator's cooperation; this mod does not bypass login, anti-bot controls, or access restrictions.

## File safety

This is **not antivirus** and cannot guarantee that a schematic is safe. The downloader does apply basic defensive checks: HTTPS and allowlisted hosts, redirects disabled, 50 MiB maximum, `.schem`/`.schematic` naming, an NBT/gzip-like header check, filename sanitization, and optional SHA-256 verification when the feed provides a checksum. Files are saved as data and never executed by this mod. Only download schematics from sources you trust, and keep Minecraft and schematic-reading mods updated.

The checks are deliberately conservative and may reject valid schematic variants. No third-party site's live API is bundled; users configure feeds for sites that explicitly permit machine-readable access.
