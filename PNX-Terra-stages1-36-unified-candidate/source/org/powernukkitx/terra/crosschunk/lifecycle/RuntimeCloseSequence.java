package org.powernukkitx.terra.crosschunk.lifecycle;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Closes every owned resource in order even if an earlier close operation fails. */
public final class RuntimeCloseSequence {
    private RuntimeCloseSequence() { }
    public static void closeAll(List<? extends AutoCloseable> resources) throws IOException {
        Objects.requireNonNull(resources, "resources");
        IOException aggregate = null;
        for (AutoCloseable resource : resources) {
            if (resource == null) continue;
            try { resource.close(); }
            catch (Exception failure) {
                IOException wrapped = failure instanceof IOException io ? io : new IOException("Runtime resource close failed", failure);
                if (aggregate == null) aggregate = wrapped; else aggregate.addSuppressed(wrapped);
            }
        }
        if (aggregate != null) throw aggregate;
    }
}
