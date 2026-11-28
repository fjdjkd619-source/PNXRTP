package org.powernukkitx.terra.crosschunk.diagnostics;

@FunctionalInterface
public interface DiagnosticSink {
    void publish(GenerationDiagnostic diagnostic);
    static DiagnosticSink ignoring(){return diagnostic -> { };}
}
