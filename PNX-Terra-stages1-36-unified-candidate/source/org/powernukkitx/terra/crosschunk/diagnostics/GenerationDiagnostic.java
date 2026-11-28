package org.powernukkitx.terra.crosschunk.diagnostics;

import org.powernukkitx.terra.crosschunk.journal.ChunkPos;
import java.time.Instant;
import java.util.Objects;

/** Structured, coordinate-rich generation failure/suppression record. */
public record GenerationDiagnostic(Instant time, Severity severity, String stage, String featureId,
                                   String structureId, ChunkPos anchor, int x, int y, int z,
                                   String message, String causeType) {
    public enum Severity { INFO, WARNING, ERROR }
    public GenerationDiagnostic {
        Objects.requireNonNull(time,"time"); Objects.requireNonNull(severity,"severity");
        if(stage==null||stage.isBlank()||message==null||message.isBlank())throw new IllegalArgumentException("Stage and message are required");
        featureId=featureId==null?"unknown":featureId; structureId=structureId==null?"unknown":structureId;
        anchor=Objects.requireNonNull(anchor,"anchor"); causeType=causeType==null?"none":causeType;
    }
    public static GenerationDiagnostic failure(String stage,String feature,String structure,ChunkPos anchor,
                                                int x,int y,int z,Throwable cause) {
        Objects.requireNonNull(cause,"cause");
        String message=cause.getMessage()==null?cause.getClass().getSimpleName():cause.getMessage();
        return new GenerationDiagnostic(Instant.now(),Severity.ERROR,stage,feature,structure,anchor,x,y,z,message,cause.getClass().getName());
    }
}
