# LightDiagnostics 0.3.0

Read-only diagnostic plugin for PowerNukkitX 3.0.6.

## What changed in 0.3.0

- Records CreatureSpawnEvent reason when that event is emitted.
- Records creature EntitySpawnEvent and samples feet/below/head light values from already-loaded chunks only.
- Checks entity NBT one server tick after EntitySpawnEvent for SpawnReason.
- Captures the live PowerNukkitX call stack at EntitySpawnEvent and marks the exact `Chunk.doMobSpawning` caller. This distinguishes a fresh natural chunk-spawn call from entity initialization that may simply be loading a saved entity.
- Counts NATURAL, other tagged reasons, missing tags, and inspection errors separately.
- Does not change blocks, light, chunks, entities, gamerules, or spawn settings.

## Commands

- `/lightdiag help`
- `/lightdiag probe <world> <x> <y> <z>`
- `/lightdiag track on|off|status`
- `/lightdiag stats`
- `/lightdiag export`

## Test procedure

1. Back up and remove the old LightDiagnostics JAR from the plugins folder.
2. Install only LightDiagnostics-0.3.0.jar and fully restart the server.
3. Run `/lightdiag track on`.
4. Stay near the problem area for 3-5 minutes. Record whether a hostile mob visibly appears at that moment and note the approximate coordinates.
5. Run `/lightdiag track off`, then `/lightdiag stats`, then `/lightdiag export`.
6. Share the exported report.

## Interpretation

- `classification=NATURAL_CHUNK_SPAWN_CALLSITE`: the live call stack contains PowerNukkitX `Chunk.doMobSpawning`; this is the strongest evidence of a fresh natural chunk-spawn operation.
- `NATURAL_TAG_BUT_CALLSITE_NOT_CONFIRMED`: the entity has an NBT natural tag, but the current call stack did not confirm a fresh natural spawn (for example, a loaded saved entity may retain metadata).
- A non-empty other reason: another caller tagged the entity with that reason.
- `UNKNOWN_NO_NBT_REASON`: no SpawnReason tag was present one tick after EntitySpawnEvent and the natural chunk-spawn callsite was not confirmed; this is not proof of a particular spawn cause.
- CreatureSpawnEvent is logged separately; it is not assumed to cover natural chunk spawning.

The plugin is diagnostic only. It does not prevent or alter spawning.
