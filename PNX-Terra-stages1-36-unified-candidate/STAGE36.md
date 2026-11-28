# Stage 36 — deferred PNX entity lifecycle for durable generation

## Why this stage exists

The Stage 35 adapter created entities through the ordinary PNX factory. Source inspection showed that PNX `Entity.init()` immediately inserted each entity into both the chunk and `Level`, fired the spawn event, and scheduled its update before the chunk's synchronous durable save. Delaying only `spawnToAll()` delayed client packets, but did **not** prevent the entity from entering Level ticking. That was not a safe transaction boundary.

## Changes

- Adds `Entity.createEntityForGeneration(...)`, a scoped ThreadLocal construction mode used only around the PNX entity factory call.
- In this mode, `Entity.init()` initializes the object and runs the cancellable `EntitySpawnEvent`, but does not insert it into the chunk/Level or schedule updates.
- Adds `registerForGenerationSave()`: the applier inserts accepted entities into the chunk save set only. They are still absent from the ticking Level and are not published to clients.
- The finalization coordinator then performs the synchronous durable chunk write.
- Only after that write succeeds does `confirmEntitySpawnIntents()` add the entity to the ticking Level, schedule updates, and publish it to clients.
- If validation or durable saving fails, the existing rollback-only close seam removes only entities created by the current transaction.
- Updates the entity contract test to assert the pre-save entity is not in the chunk or Level, the save-staging phase inserts only into the chunk, and activation/client publication happens after the durable-save boundary.

## Verification

- Entity applier contract test: Java 21, `-Xlint:all -Werror`, PASS.
- Stage 36 adapter patch parse and synthetic apply: PASS.
- PNX API patch has been authored against the exact inspected PNX `Entity.java` method/field contexts at the pinned successor ref. A full checkout application and Gradle build are still required.

## Remaining limitations

- `PNXServerWorld.spawnEntity` still fails closed. Terra's entity spawn calls are not yet captured into durable intents, so Origen entity-bearing structures remain disabled.
- The complete Stage 1–36 chain has not been applied to clean exact-ref PNX/Terra/PNX-Terra checkouts and has not passed the full Gradle build.
- No live-server crash/restart/replay or client visibility test has been run.
- Physics execution, complete property aliases, full stage diagnostics wiring, and complete static Origen bounds catalogue remain outstanding.

This stage does not authorize production deployment.
