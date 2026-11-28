package org.powernukkitx.terra.crosschunk.physics;

import org.powernukkitx.terra.crosschunk.journal.ChunkPos;
import org.powernukkitx.terra.crosschunk.journal.PhysicsIntent;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Deterministic Level-thread dispatch contract for durable physics intents. */
public final class PhysicsIntentBatch {
    private PhysicsIntentBatch() { }

    public static CompletableFuture<Integer> dispatch(Collection<PhysicsIntent> intents, Executor levelExecutor,
                                                       ChunkPos target, PhysicsApplier applier) {
        Objects.requireNonNull(intents, "intents"); Objects.requireNonNull(levelExecutor, "levelExecutor");
        Objects.requireNonNull(target, "target"); Objects.requireNonNull(applier, "applier");
        TreeMap<Position, Boolean> deduplicated = new TreeMap<>();
        for (PhysicsIntent intent : intents) {
            if (!intent.chunk().equals(target)) throw new IllegalArgumentException("Physics intent belongs to another chunk");
            if (intent.applyPhysics()) deduplicated.merge(new Position(intent.x(), intent.y(), intent.z()), true, Boolean::logicalOr);
        }
        List<Position> ordered = List.copyOf(deduplicated.keySet());
        return CompletableFuture.supplyAsync(() -> {
            int applied = 0;
            try {
                for (Position position : ordered) { applier.apply(position.x, position.y, position.z); applied++; }
                return applied;
            } catch (Exception failure) {
                throw new PhysicsDispatchException("Physics batch failed after " + applied + " positions; caller must retry idempotently", failure);
            }
        }, levelExecutor);
    }

    @FunctionalInterface public interface PhysicsApplier { void apply(int x, int y, int z) throws Exception; }
    private record Position(int x, int y, int z) implements Comparable<Position> {
        @Override public int compareTo(Position p) { int c=Integer.compare(x,p.x); if(c==0)c=Integer.compare(y,p.y); return c==0?Integer.compare(z,p.z):c; }
    }
    public static final class PhysicsDispatchException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public PhysicsDispatchException(String message, Throwable cause) { super(message, cause); }
    }
}
