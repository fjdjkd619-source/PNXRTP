package org.powernukkitx.terra.crosschunk.bounds;

public final class Stage15AdapterGuardContractTest {
    public static void main(String[] args) {
        waterfallReadEnvelopeIncludesWritePreflightReads();
        implicitWriteInspectionUsesWriteEnvelopeAndDoesNotConsumeScriptReadBudget();
        unknownProfilesUseStrictRuntimeEnvelope();
        nestedDependenciesAreExplicit();
        System.out.println("Stage15AdapterGuardContractTest: PASS");
    }

    private static void waterfallReadEnvelopeIncludesWritePreflightReads() {
        ScriptBoundsProfile profile = OrigenBoundsCatalog.requireVerified("waterfall");
        check(profile.readBounds().maxY() >= profile.writeBounds().maxY(),
                "BlockFunction pre-write read must fit inside read envelope");
        PlacementRuntimeGuard guard = new PlacementRuntimeGuard(profile, 0, 64, 0, Bounds3i.QuarterTurn.NONE);
        guard.beforeRead(5, 66, 5, true);
        guard.complete();
    }

    private static void implicitWriteInspectionUsesWriteEnvelopeAndDoesNotConsumeScriptReadBudget() {
        ScriptBoundsProfile profile = OrigenBoundsCatalog.requireVerified("waterfall");
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

    private static void unknownProfilesUseStrictRuntimeEnvelope() {
        check(OrigenBoundsCatalog.find("castle").isEmpty(), "unknown structures are not misreported as statically certified");
        check(OrigenBoundsCatalog.resolveRuntimeProfile("origen:waterfall")
                        == OrigenBoundsCatalog.requireVerified("waterfall"),
                "namespaced Origen IDs resolve to the statically audited profile");
        ScriptBoundsProfile fallback = OrigenBoundsCatalog.resolveRuntimeProfile("castle");
        check(fallback == OrigenBoundsCatalog.boundedFallbackProfile(), "unknown structures resolve to bounded fallback");
        check(fallback.writeBounds().minX() == -32 && fallback.writeBounds().maxX() == 32,
                "fallback enforces a finite horizontal envelope");
        check(fallback.maxEntitySpawns() == 0, "fallback does not enable unjournaled entities");
        check(fallback.allowsNestedStructure("dynamic_nested_structure"),
                "nested structures share the same root guard under the hard envelope");
        check(OrigenBoundsCatalog.maxHorizontalReach() == 32,
                "scheduler prefetch horizon covers the fallback envelope");

        PlacementRuntimeGuard guard = new PlacementRuntimeGuard(fallback, 0, 64, 0, Bounds3i.QuarterTurn.NONE);
        guard.beforeWrite(32, 64, -32);
        expect(BoundsViolationException.class, () -> guard.beforeWrite(33, 64, 0));
        check(guard.snapshot().state() == PlacementRuntimeGuard.State.FAILED,
                "one out-of-envelope nested write poisons the entire root placement");

        check(OrigenBoundsCatalog.requireVerified("mangrove_tree_procedural").maxEntitySpawns() > 0,
                "mangrove remains blocked until entity intents are transactionally supported");
    }

    private static void nestedDependenciesAreExplicit() {
        ScriptBoundsProfile waterfall = OrigenBoundsCatalog.requireVerified("waterfall");
        check(waterfall.transitiveDependencies().isEmpty(), "waterfall has no nested dependencies");
        ScriptBoundsProfile mangrove = OrigenBoundsCatalog.requireVerified("mangrove_tree_procedural");
        check(mangrove.transitiveDependencies().contains("mangrove_leaves_clump"),
                "mangrove nested dependency is explicitly allow-listed");
        check(!mangrove.allowsNestedStructure("unlisted_structure"),
                "unlisted nested structure is not allowed by the audited root profile");
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
