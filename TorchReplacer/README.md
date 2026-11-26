# TorchReplacer 0.1.0

PowerNukkitX 3.0.6 / API 3.0.6 plugin. Requires Java 21.

## Commands

- `/torchreplace start` — scans all currently loaded worlds and replaces torches in every existing saved chunk.
- `/torchreplace start <world>` — processes one loaded world by folder/name.
- `/torchreplace status` — shows progress and counters.
- `/torchreplace cancel` — stops after the current server tick; completed changes are retained.

## What it replaces

Any registered block identifier ending in `torch`, including normal, wall, soul, and redstone torch variants. Torchflower blocks are not matched.

For every torch, the plugin:
1. Reads its immutable BlockState and identifier.
2. Requests a fresh Block instance from the PNX block registry using that state and the original coordinates.
3. Removes the existing block through Level.setBlock(..., true, true).
4. Places the new instance at the same coordinates through Level.setBlock(..., true, true).

The original BlockState is retained, so wall orientation and torch variant are preserved. The plugin does not edit LevelDB records directly and calls loadChunk(..., false), so it does not intentionally generate new chunks.

Chunks are indexed from existing LevelDB VERSION records and processed gradually. Chunks loaded by the plugin are saved and unloaded after processing where safe. Chunks that were already loaded are left loaded for the server to save normally.

## Build

The Maven project expects the PNX 3.0.6 server jar at:

```
${user.home}/storage/downloads/powernukkitx.jar
```

Run:

```
mvn -f TorchReplacer/pom.xml clean package
```

Output:

```
TorchReplacer/target/TorchReplacer-0.1.0.jar
```

A GitHub Actions workflow on this branch downloads the official PNX 3.0.6 release jar, builds the plugin, and uploads the JAR as a workflow artifact.
