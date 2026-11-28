package org.powernukkitx.terra.crosschunk;

import org.powernukkitx.terra.crosschunk.diagnostics.*;
import org.powernukkitx.terra.crosschunk.journal.ChunkPos;

public final class Stage24DiagnosticsTest {
    public static void main(String[] args) {
        IllegalStateException cause=new IllegalStateException("read would force-load chunk");
        GenerationDiagnostic d=GenerationDiagnostic.failure("terra_populate","feature-a","waterfall",
                new ChunkPos(-2,3),-17,72,49,cause);
        check(d.severity()==GenerationDiagnostic.Severity.ERROR,"failure severity");
        check(d.anchor().equals(new ChunkPos(-2,3))&&d.x()==-17&&d.y()==72&&d.z()==49,"negative coordinates retained");
        check(d.causeType().equals(IllegalStateException.class.getName()),"exception class retained");
        final int[] count={0}; DiagnosticSink sink=diagnostic->count[0]++;
        sink.publish(d); check(count[0]==1,"diagnostic sink publishes event");
        System.out.println("Stage24DiagnosticsTest: PASS");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
