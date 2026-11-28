package org.powernukkitx.terra.crosschunk.diagnostics;

import org.powernukkitx.terra.crosschunk.journal.ChunkPos;
import java.util.concurrent.atomic.AtomicInteger;

public final class Stage30StageGuardTest {
    public static void main(String[] args) {
        var calls=new AtomicInteger(); var sink=(DiagnosticSink)d->{ if(d.severity()!=GenerationDiagnostic.Severity.ERROR || !d.stage().equals("features")) throw new AssertionError(); calls.incrementAndGet(); };
        try { GenerationStageGuard.run("features","f","s",new ChunkPos(1,2),3,4,5,sink,()->{throw new IllegalStateException("boom");}); throw new AssertionError("stage failure swallowed"); }
        catch(IllegalStateException expected) { if(!expected.getMessage().equals("boom")) throw expected; }
        if(calls.get()!=1) throw new AssertionError("diagnostic not published exactly once");
        System.out.println("Stage30StageGuardTest: PASS");
    }
}
