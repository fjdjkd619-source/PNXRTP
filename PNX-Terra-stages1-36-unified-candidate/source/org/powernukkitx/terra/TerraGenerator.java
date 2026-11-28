package org.powernukkitx.terra;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.powernukkitx.level.DimensionData;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.format.ChunkState;
import org.powernukkitx.level.format.IChunk;
import org.powernukkitx.level.generator.ChunkGenerateContext;
import org.powernukkitx.level.generator.GenerateStage;
import org.powernukkitx.level.generator.PopulatedGenerator;
import org.powernukkitx.level.generator.populator.generic.PopulatorRuinedPortal;
import org.powernukkitx.level.generator.populator.nether.BastionRemnantPopulator;
import org.powernukkitx.level.generator.populator.nether.NetherFortressPopulator;
import org.powernukkitx.level.generator.populator.nether.soulsand_valley.NetherFossilPopulator;
import org.powernukkitx.level.generator.populator.the_end.EndCityPopulator;
import org.powernukkitx.level.generator.stages.GeneratedStage;
import org.powernukkitx.level.generator.stages.LightPopulationStage;
import org.powernukkitx.level.generator.stages.FinishedStage;
import org.powernukkitx.level.generator.stages.PopulatorStage;
import org.powernukkitx.level.generator.stages.normal.NormalPopulatorStage;
import org.powernukkitx.terra.crosschunk.bounds.OrigenBoundsCatalog;
import org.powernukkitx.terra.crosschunk.journal.ChunkPos;
import org.powernukkitx.terra.crosschunk.runtime.PnxGenerationJournalRuntime;
import org.powernukkitx.terra.crosschunk.finalize.PnxCrossChunkFinalizer;
import org.powernukkitx.terra.delegate.PNXProtoChunk;
import org.powernukkitx.terra.delegate.PNXProtoWorld;
import org.powernukkitx.terra.delegate.PNXServerWorld;
import org.powernukkitx.registry.Registries;
import com.dfsek.terra.api.config.ConfigPack;
import com.dfsek.terra.api.world.biome.generation.BiomeProvider;
import com.dfsek.terra.api.world.chunk.generation.ChunkGenerator;
import com.dfsek.terra.api.world.chunk.generation.util.GeneratorWrapper;
import com.dfsek.terra.api.world.info.WorldProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class TerraGenerator extends PopulatedGenerator implements GeneratorWrapper {
    private final BiomeProvider biomeProvider;
    private final ConfigPack configPack;
    private final WorldProperties worldProperties;
    private final ChunkGenerator chunkGenerator;
    private volatile PnxGenerationJournalRuntime journalRuntime;

    public TerraGenerator(DimensionData dimensionData, Map<String, Object> options) {
        super(dimensionData, options);
        String packName = options.getOrDefault("pack", "overworld").toString();
        this.configPack = createConfigPack(packName);
        this.chunkGenerator = createGenerator(this.configPack);
        this.biomeProvider = this.configPack.getBiomeProvider();
        this.worldProperties = new WorldProperties() {
            @Override public long getSeed() { return level.getSeed(); }
            @Override public int getMaxHeight() { return dimensionData.getMaxHeight(); }
            @Override public int getMinHeight() { return dimensionData.getMinHeight(); }
            @Override public Object getHandle() { return null; }
        };
    }

    @Override
    public void stages(GenerateStage.Builder builder) {
        builder.start(new TerrainStage());
        builder.next(Registries.GENERATE_STAGE.get(GeneratedStage.NAME));
        if ((boolean) options.getOrDefault("structures", true)) builder.next(new StructureStage());
        builder.next(new PopulateStage());
        builder.next(new CrossChunkApplyStage());
        builder.next(Registries.GENERATE_STAGE.get(LightPopulationStage.NAME));
        builder.next(new CrossChunkPersistStage());
        builder.next(Registries.GENERATE_STAGE.get(FinishedStage.NAME));
    }

    public ConfigPack getConfigPack() { return configPack; }

    private synchronized PnxGenerationJournalRuntime getJournalRuntime(Level currentLevel) {
        String expectedWorldKey = currentLevel.getName() + "|" + currentLevel.getSeed() + "|dimension=" + dimensionData.getDimensionId();
        if (journalRuntime != null) {
            if (!journalRuntime.worldKey().equals(expectedWorldKey))
                throw new IllegalStateException("One TerraGenerator instance cannot serve multiple journal worlds");
            return journalRuntime;
        }
        Path directory = Path.of(options.getOrDefault("cross-chunk-journal-dir", "plugins/PNX-Terra/journal").toString());
        try {
            journalRuntime = PnxGenerationJournalRuntime.open(directory, currentLevel, dimensionData.getDimensionId());
            return journalRuntime;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot open durable Terra cross-chunk journal at " + directory, e);
        }
    }

    private static ConfigPack createConfigPack(final String packName) {
        Optional<ConfigPack> byID = PNXPlatform.getInstance().getConfigRegistry().getByID(packName);
        return byID.orElseGet(() -> PNXPlatform.getInstance().getConfigRegistry()
                .getByID(packName.toUpperCase(Locale.ENGLISH))
                .orElseThrow(() -> new IllegalArgumentException("Can't find terra config pack " + packName)));
    }

    private static ChunkGenerator createGenerator(ConfigPack config) {
        return config.getGeneratorProvider().newInstance(config);
    }

    class TerrainStage extends GenerateStage {
        @Override public void apply(ChunkGenerateContext context) {
            final IChunk chunk=context.getChunk();final int chunkX=chunk.getX(),chunkZ=chunk.getZ();
            chunkGenerator.generateChunkData(new PNXProtoChunk(chunk),worldProperties,biomeProvider,chunkX,chunkZ);
            final int minHeight=level.getMinHeight(),maxHeight=level.getMaxHeight();
            for(int x=0;x<16;x++)for(int y=minHeight;y<maxHeight;y++)for(int z=0;z<16;z++)
                chunk.setBiomeId(x,y,z,(Integer)biomeProvider.getBiome(chunkX*16+x,y,chunkZ*16+z,level.getSeed()).getPlatformBiome().getHandle());
        }
        @Override public String name(){return "terra_terrain";}
    }

    class StructureStage extends PopulatorStage {
        private final ObjectArraySet<String> populators=new ObjectArraySet<>();
        public StructureStage(){populators.addAll(switch(dimensionData.getDimensionId()){
            case 1->List.of(BastionRemnantPopulator.NAME,NetherFortressPopulator.NAME,PopulatorRuinedPortal.NAME,NetherFossilPopulator.NAME);
            case 2->List.of(EndCityPopulator.NAME);
            default->NormalPopulatorStage.POPULATORS.clone();
        });}
        @Override public ObjectArraySet<String> populators(){return populators;}
        @Override public String name(){return "terra_structure";}
    }

    /** Async barrier + WAL projection. Runs before lighting so cross-chunk blocks are lit correctly. */
    class CrossChunkApplyStage extends GenerateStage {
        @Override public void apply(ChunkGenerateContext context) {
            throw new IllegalStateException("Cross-chunk finalization requires the async PNX generation pipeline");
        }
        @Override public java.util.concurrent.CompletableFuture<Void> applyAsync(ChunkGenerateContext context) {
            return PnxCrossChunkFinalizer.applyTarget(getJournalRuntime(context.getLevel()), context.getLevel(), context.getChunk());
        }
        @Override public String name() { return "terra_cross_chunk_apply"; }
    }

    /** Saves the fully lit target before acknowledging its projection digest in the WAL. */
    class CrossChunkPersistStage extends GenerateStage {
        @Override public void apply(ChunkGenerateContext context) {
            try {
                PnxCrossChunkFinalizer.persistTarget(getJournalRuntime(context.getLevel()), context.getLevel(), context.getChunk());
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Could not durably finalize Terra target chunk", failure);
            }
        }
        @Override public String name() { return "terra_cross_chunk_persist"; }
    }

    class PopulateStage extends GenerateStage {
        @Override public void apply(ChunkGenerateContext context) {
            final IChunk chunk=context.getChunk();chunk.setChunkState(ChunkState.POPULATED);
            final int chunkX=chunk.getX(),chunkZ=chunk.getZ();
            PnxGenerationJournalRuntime runtime=getJournalRuntime(context.getLevel());
            ChunkPos anchor=new ChunkPos(chunkX,chunkZ);
            // Source-anchor-only chunks do not recursively prefetch their own neighbours.
            boolean dependencyPrefetch = runtime.consumeDependencyPrefetch(anchor);
            // A prior attempt may have durably completed population and then crashed before target
            // projection/save acknowledgement. Reuse its WAL; do not execute the feature graph twice.
            if (runtime.journal().isAnchorCompleted(runtime.worldKey(), anchor)) {
                if (!dependencyPrefetch) {
                    runtime.registerTarget(anchor, OrigenBoundsCatalog.maxHorizontalReach(), true);
                }
                return;
            }
            // Open the session before registering dependencies. The target anchor is part of its own
            // overlap envelope; the scheduler must see it as in-flight and wait for this session,
            // not enqueue/reset the chunk whose PopulateStage is currently executing.
            var anchorSession=runtime.openAnchor(anchor);
            var serverWorld=new PNXServerWorld(TerraGenerator.this,context.getLevel(),anchorSession,runtime.worldKey());
            var protoWorld=new PNXProtoWorld(serverWorld,chunkX,chunkZ);
            try {
                if (!dependencyPrefetch) {
                    runtime.registerTarget(anchor, OrigenBoundsCatalog.maxHorizontalReach(), true);
                }
                for(var generationStage:configPack.getStages())generationStage.populate(protoWorld);
                // This BlockManager contains only this anchor's writes; cross-chunk writes are in the WAL.
                serverWorld.apply(chunk);
                // Completion marker is durable only after every root ticket has committed and anchor writes applied.
                anchorSession.finishPopulation();
            } catch(Exception e) {
                anchorSession.close();
                throw new IllegalStateException("Terra population failed for anchor "+anchor,e);
            }
        }
        @Override public String name(){return "terra_populate";}
    }

    public String getName(){return "terra";}
    @Override public ChunkGenerator getHandle(){return chunkGenerator;}
    public BiomeProvider getBiomeProvider(){return biomeProvider;}
    public DimensionData getDimensionData(){return dimensionData;}
    public Level getLevel(){return level;}
}
