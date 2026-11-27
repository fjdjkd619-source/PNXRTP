# LightDiagnostics 0.3.0

Read-only diagnostic plugin for PowerNukkitX 3.0.6.

## What changed in 0.3.0

- Records CreatureSpawnEvent reason when that event is emitted.
- Records creature EntitySpawnEvent and samples feet/below/head light values from already-loaded chunks only.
- Checks entity NBT one server tick after EntitySpawnEvent for SpawnReason. This delay matters: PowerNukkitX 3.0.6 natural chunk spawning assigns `SpawnReason=NATURAL` immediately after entity construction, while EntitySpawnEvent is emitted inside entity initialization.
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

- `nbtSpawnReason=NATURAL`: the entity was created by the natural chunk-spawn path in PowerNukkitX.
- A non-empty other reason: another caller tagged the entity with that reason.
- `MISSING`: no SpawnReason tag was present one tick after EntitySpawnEvent; this is not proof of a particular spawn cause.
- CreatureSpawnEvent is logged separately; it is not assumed to cover natural chunk spawning.

The plugin is diagnostic only. It does not prevent or alter spawning.
