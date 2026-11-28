# Stage 1–25 gate status

Status: BLOCKED — integration not yet complete.

Verified in the current workspace:
- Java 21 independent-core compile: PASS.
- 11 standalone test classes: PASS (one current run each).

Not yet verified:
- Complete patch application against clean pinned upstream checkouts.
- Full PowerNukkitX Gradle build.
- Full Terra Gradle build.
- Full PNX-Terra Gradle build.
- Full adapter compile against real upstream classpaths.
- Block Entity NBT and PNX object creation.
- Runtime physics executor and WaterLayerPolicy.
- Complete Terra↔PNX block/property registry.
- Global diagnostics.
- Full Origen safe-bound coverage.
- Runtime world generation, restart/recovery, durable persistence, load tests.
- Final integrated JAR.

A green standalone-core test is not acceptance of stages 1–25. The integration gate remains closed until all items above are verified.
