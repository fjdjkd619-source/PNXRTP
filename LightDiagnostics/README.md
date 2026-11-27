# LightDiagnostics 0.2.0

Read-only diagnostic plugin for PowerNukkitX 3.0.6 / API 3.0.6. Requires Java 21.

The plugin does not modify blocks, light values, spawn rules, gamerules, chunks, entities, or world data. It samples only chunks that are already loaded.

## Commands

- `/lightdiag probe <world> <x> <y> <z>` — reads stored block light, sky light, block emission, light-populated flag and chunk state at an exact position. It refuses to load or generate an unloaded chunk.
- `/lightdiag track on` — clears counters and starts recording creature `CreatureSpawnEvent` and creature `EntitySpawnEvent` events.
- `/lightdiag track off` — stops tracking.
- `/lightdiag track status` — shows tracking state and counters.
- `/lightdiag stats` — displays both event counters and cancellation counters.
- `/lightdiag export` — writes a UTF-8 report in the plugin data folder.

The rolling event buffer is capped at 5,000 records. Events are also written to the server log while tracking is enabled.

## What 0.2.0 records

### CreatureSpawnEvent

When this event reaches the plugin event pipeline, the report includes the PNX `SpawnReason`, entity network ID, cancellation state, coordinates, and block samples at feet, below-feet, and head positions.

### EntitySpawnEvent

For creature entities, the report includes entity class, network ID, cancellation state, coordinates, and the same three block samples.

**Important:** `EntitySpawnEvent` does not contain a spawn reason. Its records are explicitly marked `source=UNCLASSIFIED`; they must not be interpreted as proof of natural spawning.

The previous 0.1.0 report contained creature `ENTITY_SPAWN` records but no `CREATURE_SPAWN` records. This version makes the distinction visible in counters and exports. If the creature event counter remains zero while entity events increase, the plugin has not received a reason-bearing creature event during that capture window. That does not, by itself, prove why the server created the entity.

## Interpreting results

1. Compare the light samples at feet, below-feet and head. A single sample at the entity's floored position may not describe the full spawn space.
2. Compare `CREATURE_SPAWN reason=...` and `ENTITY_SPAWN source=UNCLASSIFIED` entries by world and coordinates. They are separate event observations; do not assume they always pair one-to-one.
3. If `CreatureSpawnEvent` is absent, the spawn reason is unknown from this plugin's event data. Further diagnosis will require tracing the exact PNX spawn code path or collecting a server-side reproduction.
4. `FINISHED` or `POPULATED` chunk state is metadata, not proof that every lighting or spawn predicate is correct.

## Build

The Maven project expects the PNX server JAR at `${user.home}/storage/downloads/powernukkitx.jar`.

```bash
mvn --batch-mode -f LightDiagnostics/pom.xml clean package
```

Output: `LightDiagnostics/target/LightDiagnostics-0.2.0.jar`
