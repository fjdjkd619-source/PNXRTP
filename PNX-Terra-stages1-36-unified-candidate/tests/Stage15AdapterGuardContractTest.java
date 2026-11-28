package org.powernukkitx.terra.crosschunk.bounds;

public final class Stage15AdapterGuardContractTest {
    public static void main(String[] args) {
        verifiedProfileReadEnvelopeIncludesWritePreflightReads();
        implicitWriteInspectionUsesWriteEnvelopeAndDoesNotConsumeScriptReadBudget();
        unknownProfilesAreStrictDenyAll();
        nestedDependenciesAreExplicit();
        System.out.println("Stage15AdapterGuardContractTest: PASS");
    }

    private static void verifiedProfileReadEnvelopeIncludesWritePreflightReads() {
        ScriptBoundsProfile profile = OrigenBoundsCatalog.requireVerified("fantasy_floating_island");
        check(profile.readBounds().maxY() >= profile.writeBounds().maxY(),
                "BlockFunction pre-write read must fit inside read envelope");
        PlacementRuntimeGuard guard = new PlacementRuntimeGuard(profile, 0, 64, 0, Bounds3i.QuarterTurn.NONE);
        guard.beforeRead(5, 66, 5, true);
        guard.complete();
    }

    private static void implicitWriteInspectionUsesWriteEnvelopeAndDoesNotConsumeScriptReadBudget() {
        ScriptBoundsProfile profile = OrigenBoundsCatalog.requireVerified("fantasy_floating_island");
        PlacementRuntimeGuard guard = new PlacementRuntimeGuard(profile, 0, 64, 0, Bounds3i.QuarterTurn.NONE);
        for (int i = 0; i < profile.maxBlockWrites(); i++) {
            guard.beforeWrite(0, 64, 0);
            guard.beforeImplicitWriteRead(0, 64, 0, true);
        }
        check(guard.snapshot().reads() == 0, "adapter inspection is accounted by write budget");
        expect(BoundsViolationException.class,
                () -> guard.beforeImplicitWriteRead(0, 64, 0, false));
        check(guard.snapshot().state() == PlacementRuntimeGuard.State.FAILED,
                "unloaded implicit read poisons the root placement");
    }

    private static void unknownProfilesAreStrictDenyAll() {
        check(OrigenBoundsCatalog.find("castle").isEmpty(),
                "unknown structures are not misreported as statically certified");
        check(OrigenBoundsCatalog.resolveRuntimeProfile("terra:fantasy_floating_island")
                        == OrigenBoundsCatalog.requireVerified("fantasy_floating_island"),
                "namespaced IDs resolve to their statically audited profile");

        ScriptBoundsProfile fallback = OrigenBoundsCatalog.resolveRuntimeProfile("castle");
        check(fallback == OrigenBoundsCatalog.boundedFallbackProfile(),
                "unknown structures resolve to the deny-all fallback");
        check(fallback.writeBounds().minX() == 0 && fallback.writeBounds().maxX() == 0
                        && fallback.writeBounds().minY() == 0 && fallback.writeBounds().maxY() == 0
                        && fallback.writeBounds().minZ() == 0 && fallback.writeBounds().maxZ() == 0,
                "unverified fallback has a zero-sized write envelope");
        check(fallback.readBounds().minX() == 0 && fallback.readBounds().maxX() == 0
                        && fallback.readBounds().minY() == 0 && fallback.readBounds().maxY() == 0
                        && fallback.readBounds().minZ() == 0 && fallback.readBounds().maxZ() == 0,
                "unverified fallback has a zero-sized read envelope");
        check(fallback.maxBlockWrites() == 0 && fallback.maxBlockReads() == 0
                        && fallback.maxEntitySpawns() == 0,
                "unverified fallback has no operation budget");
        check(!fallback.allowsNestedStructure("dynamic_nested_structure"),
                "unverified fallback cannot admit nested structures");
        check(OrigenBoundsCatalog.maxHorizontalReach() >= 5,
                "scheduler reach covers the largest statically certified write envelope");

        PlacementRuntimeGuard guard = new PlacementRuntimeGuard(fallback, 0, 64, 0, Bounds3i.QuarterTurn.NONE);
        expect(BoundsViolationException.class, () -> guard.beforeWrite(0, 64, 0));
        check(guard.snapshot().state() == PlacementRuntimeGuard.State.FAILED,
                "the first write under an unverified root fails closed");
        expect(IllegalStateException.class,
                () -> OrigenBoundsCatalog.requireVerified("mangrove_tree_procedural"));
    }

    private static void nestedDependenciesAreExplicit() {
        ScriptBoundsProfile profile = OrigenBoundsCatalog.requireVerified("fantasy_crystal_cluster");
        check(profile.transitiveDependencies().contains("fantasy_crystal_cluster"),
                "generated profile records its audited root reference");
        check(profile.allowsNestedStructure("fantasy_crystal_cluster"),
                "only the recorded root dependency is allow-listed");
        check(!profile.allowsNestedStructure("unlisted_structure"),
                "unlisted nested structures are rejected");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try { action.run(); }
        catch (Throwable t) { if (type.isInstance(t)) return; throw new AssertionError(t); }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
