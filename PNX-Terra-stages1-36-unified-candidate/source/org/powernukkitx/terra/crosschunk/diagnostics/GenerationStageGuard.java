package org.powernukkitx.terra.crosschunk.diagnostics;

import org.powernukkitx.terra.crosschunk.journal.ChunkPos;
import java.util.Objects;
import java.util.concurrent.Callable;

/** Uniform stage boundary: report failures once, preserve the original throwable and fail the stage. */
public final class GenerationStageGuard {
    private GenerationStageGuard() { }

    public static void run(String stage, String feature, String structure, ChunkPos anchor,
                           int x, int y, int z, DiagnosticSink sink, CheckedRunnable action) {
        call(stage, feature, structure, anchor, x, y, z, sink, () -> { action.run(); return null; });
    }

    public static <T> T call(String stage, String feature, String structure, ChunkPos anchor,
                             int x, int y, int z, DiagnosticSink sink, Callable<T> action) {
        Objects.requireNonNull(sink, "sink"); Objects.requireNonNull(action, "action");
        try { return action.call(); }
        catch (Throwable failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            try { sink.publish(GenerationDiagnostic.failure(stage, feature, structure, anchor, x, y, z, failure)); }
            catch (Throwable diagnosticFailure) { failure.addSuppressed(diagnosticFailure); }
            if (failure instanceof Error error) throw error;
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new StageExecutionException("Generation stage failed: " + stage, failure);
        }
    }

    @FunctionalInterface public interface CheckedRunnable { void run() throws Exception; }
    public static final class StageExecutionException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public StageExecutionException(String message, Throwable cause) { super(message, cause); }
    }
}
