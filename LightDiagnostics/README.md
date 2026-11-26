# LightDiagnostics 0.1.0

Diagnostic plugin for PowerNukkitX 3.0.6 / API 3.0.6. Requires Java 21.

It is read-only: it does not change blocks, light values, spawn rules, gamerules, or chunks.

## Commands

- `/lightdiag probe <world> <x> <y> <z>` — reads the already-loaded chunk's stored block light, sky light, block emission, light-populated flag and finalization state at an exact position. It refuses to load or generate an unloaded chunk.
- `/lightdiag track on` — clears the event buffer and starts recording CreatureSpawnEvent (including NATURAL reason and cancellation state) and actual creature EntitySpawnEvent events, with server-side light sampled at the spawn position.
- `/lightdiag track off` — stops tracking.
- `/lightdiag track status` — shows tracking state and buffered event count.
- `/lightdiag export` — writes the buffered events to a UTF-8 log in the plugin data folder.

The event buffer is capped at 2,000 entries. Spawn event details are also written to the server log.

## Interpreting the results

1. **Light storage / propagation path:** compare the block-light value at a torch's own coordinate and at nearby air-block coordinates (for example, one block away and at the exact location where a hostile mob appears). A torch should report non-zero emission; nearby block-light should reflect propagation. If the torch's emission is non-zero but stored block light remains zero nearby, this points to a lighting propagation/storage problem. Compare the same coordinates after the chunk is reloaded only if the server operator chooses to do so.
2. **Spawn path:** a `CREATURE_SPAWN` event with `reason=NATURAL` records a natural-spawn attempt that reached the event pipeline, along with the server's light value at that position. An `ENTITY_SPAWN` entry confirms creature entity creation. Non-natural reasons (such as SPAWNER, CUSTOM, SPAWN_EGG) distinguish other sources.
3. This plugin observes the event pipeline; it does not independently prove every internal spawn predicate or cancel any spawn. A spawn event may be raised after some checks have already occurred. Use its reason, cancellation state, coordinates and sampled light values together with the probe results.

## Build

The Maven project expects the PNX server JAR at `${user.home}/storage/downloads/powernukkitx.jar`.

```bash
mvn --batch-mode -f LightDiagnostics/pom.xml clean package
```

Output: `LightDiagnostics/target/LightDiagnostics-0.1.0.jar`
