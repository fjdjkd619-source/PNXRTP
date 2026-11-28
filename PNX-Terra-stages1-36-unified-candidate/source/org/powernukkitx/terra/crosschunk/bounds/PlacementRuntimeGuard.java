package org.powernukkitx.terra.crosschunk.bounds;

import java.util.Objects;

/**
 * Fail-closed runtime monitor. Install at the common Terra WritableWorld adapter boundary,
 * inside the existing placement transaction and before any BlockManager publication.
 * All nested TerraScript structures must share the root guard.
 */
public final class PlacementRuntimeGuard {
    private final ScriptBoundsProfile profile;
    private final int originX, originY, originZ;
    private State state = State.OPEN;
    private int writes, reads, entities;
    private final MutableBounds observedWrites = new MutableBounds();
    private final MutableBounds observedReads = new MutableBounds();

    public PlacementRuntimeGuard(ScriptBoundsProfile profile, int originX, int originY, int originZ,
                                 Bounds3i.QuarterTurn rootRotation) {
        this.profile = Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(rootRotation, "rootRotation");
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        // Profiles are authored in local coordinates; transform once at the root.
        this.writeWorldBounds = profile.writeBounds().rotate(rootRotation).translate(originX, originY, originZ);
        this.readWorldBounds = profile.readBounds().rotate(rootRotation).translate(originX, originY, originZ);
    }

    private final Bounds3i writeWorldBounds;
    private final Bounds3i readWorldBounds;

    public void beforeWrite(int x, int y, int z) {
        ensureOpen();
        if (!writeWorldBounds.contains(x, y, z)) fail("write", x, y, z, writeWorldBounds);
        if (++writes > profile.maxBlockWrites()) fail("write budget", x, y, z, writeWorldBounds);
        observedWrites.include(x, y, z);
    }

    /**
     * Checks the adapter's implicit old-block inspection used by its water-preservation
     * heuristic. It is constrained by the write envelope and is accounted for by the write
     * budget, not the TerraScript explicit-read budget.
     */
    public void beforeImplicitWriteRead(int x, int y, int z, boolean availableWithoutLoading) {
        ensureOpen();
        if (!writeWorldBounds.contains(x, y, z)) fail("write inspection", x, y, z, writeWorldBounds);
        if (!availableWithoutLoading) {
            state = State.FAILED;
            throw new BoundsViolationException(profile.structureId()
                    + " write inspection would force-load an unavailable chunk at " + x + "," + y + "," + z);
        }
        observedReads.include(x, y, z);
    }

    /**
     * `availableWithoutLoading` must be true only when the position is in the transaction
     * overlay/cache or its chunk is already loaded. This prevents TerraScript getBlock()
     * from triggering cascading chunk generation through the platform adapter.
     */
    public void beforeRead(int x, int y, int z, boolean availableWithoutLoading) {
        ensureOpen();
        if (!readWorldBounds.contains(x, y, z)) fail("read", x, y, z, readWorldBounds);
        if (!availableWithoutLoading) {
            state = State.FAILED;
            throw new BoundsViolationException(profile.structureId()
                    + " read would force-load an unavailable chunk at " + x + "," + y + "," + z);
        }
        if (++reads > profile.maxBlockReads()) fail("read budget", x, y, z, readWorldBounds);
        observedReads.include(x, y, z);
    }

    public void beforeEntitySpawn(int x, int y, int z) {
        ensureOpen();
        if (!writeWorldBounds.contains(x, y, z)) fail("entity spawn", x, y, z, writeWorldBounds);
        if (++entities > profile.maxEntitySpawns()) fail("entity budget", x, y, z, writeWorldBounds);
    }

    /** Position changes on a staged entity must remain inside the same root write envelope. */
    public void beforeEntityMove(int x, int y, int z) {
        ensureOpen();
        if (!writeWorldBounds.contains(x, y, z)) fail("entity move", x, y, z, writeWorldBounds);
    }

    /** Call only after the backing in-memory placement transaction has accepted its writes. Durable journal acknowledgement is a separate step. */
    public Snapshot complete() {
        ensureOpen();
        state = State.COMPLETED;
        return snapshot();
    }

    /** Call when Terra reports generation failure or any transaction is aborted. */
    public void abort() {
        if (state == State.COMPLETED) throw new IllegalStateException("Completed guard cannot be aborted");
        state = State.ABORTED;
    }

    public Snapshot snapshot() {
        return new Snapshot(state, writes, reads, entities, observedWrites.freeze(), observedReads.freeze(),
                originX, originY, originZ);
    }

    public ScriptBoundsProfile profile() { return profile; }

    private void fail(String operation, int x, int y, int z, Bounds3i bounds) {
        state = State.FAILED;
        throw new BoundsViolationException(profile.structureId() + " " + operation + " outside declared bounds: "
                + x + "," + y + "," + z + " not in " + bounds);
    }

    private void ensureOpen() {
        if (state != State.OPEN) throw new IllegalStateException("Placement guard is " + state);
    }

    public enum State { OPEN, FAILED, COMPLETED, ABORTED }

    public record Snapshot(State state, int writes, int reads, int entitySpawns,
                           Bounds3i observedWrites, Bounds3i observedReads,
                           int originX, int originY, int originZ) {}

    private static final class MutableBounds {
        private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        void include(int x, int y, int z) {
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }
        Bounds3i freeze() { return minX == Integer.MAX_VALUE ? null : new Bounds3i(minX,minY,minZ,maxX,maxY,maxZ); }
    }
}
