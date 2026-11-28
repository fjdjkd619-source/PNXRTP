package org.powernukkitx.terra.crosschunk.runtime;

import org.powernukkitx.level.Level;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Executor for mutations that must run on the owning PNX level scheduler context.
 * Level.doTick() drives this scheduler's main-thread heartbeat (including level-thread mode).
 */
public final class PnxLevelExecutor {
    private PnxLevelExecutor() { }

    public static Executor forLevel(Level level) {
        Objects.requireNonNull(level, "level");
        return command -> level.getScheduler().scheduleTask(Objects.requireNonNull(command, "command"));
    }
}
