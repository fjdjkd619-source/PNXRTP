#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="${BUILD_DIR:-$ROOT/validation/delegate-contract-build}"
STUB="$BUILD/stubs"
rm -rf "$BUILD"
mkdir -p "$STUB/com/dfsek/seismic/type/vector" "$STUB/com/dfsek/terra/api/block/entity" "$STUB/com/dfsek/terra/api/block/state/properties" "$STUB/com/dfsek/terra/api/block/state" "$STUB/com/dfsek/terra/api/block" "$STUB/com/dfsek/terra/api/world/chunk" "$STUB/com/dfsek/terra/api/world" "$STUB/org/powernukkitx/block/property/type" "$STUB/org/powernukkitx/block" "$STUB/org/powernukkitx/blockentity" "$STUB/org/powernukkitx/registry" "$STUB/org/powernukkitx/level/format" "$STUB/org/powernukkitx/terra/delegate" "$STUB/org/powernukkitx/terra" "$STUB/org/jetbrains/annotations"
cat > "$STUB/com/dfsek/seismic/type/vector/Vector3.java" <<'JAVA'
package com.dfsek.seismic.type.vector; public record Vector3(double x,double y,double z){public static Vector3 of(double x,double y,double z){return new Vector3(x,y,z);}}
JAVA
cat > "$STUB/com/dfsek/terra/api/block/entity/BlockEntity.java" <<'JAVA'
package com.dfsek.terra.api.block.entity; import com.dfsek.seismic.type.vector.Vector3; import com.dfsek.terra.api.block.state.BlockState; public interface BlockEntity{boolean update(boolean physics);default void applyState(String state){}Vector3 getPosition();int getX();int getY();int getZ();BlockState getBlockState();Object getHandle();}
JAVA
cat > "$STUB/com/dfsek/terra/api/block/BlockType.java" <<'JAVA'
package com.dfsek.terra.api.block; public interface BlockType {}
JAVA
cat > "$STUB/com/dfsek/terra/api/block/state/properties/Property.java" <<'JAVA'
package com.dfsek.terra.api.block.state.properties; import java.util.Collection; public interface Property<T>{String getID();Class<T> getType();Collection<T> values();}
JAVA
cat > "$STUB/com/dfsek/terra/api/block/state/BlockState.java" <<'JAVA'
package com.dfsek.terra.api.block.state; import com.dfsek.terra.api.block.BlockType; import com.dfsek.terra.api.block.state.properties.Property; public interface BlockState{boolean matches(BlockState s);<T extends Comparable<T>> boolean has(Property<T> p);<T extends Comparable<T>> T get(Property<T> p);<T extends Comparable<T>> BlockState set(Property<T> p,T v);BlockType getBlockType();String getAsString(boolean p);boolean isAir();Object getHandle();}
JAVA
cat > "$STUB/com/dfsek/terra/api/world/ServerWorld.java" <<'JAVA'
package com.dfsek.terra.api.world; public interface ServerWorld {}
JAVA
cat > "$STUB/com/dfsek/terra/api/world/chunk/Chunk.java" <<'JAVA'
package com.dfsek.terra.api.world.chunk; import com.dfsek.terra.api.block.state.BlockState; import com.dfsek.terra.api.world.ServerWorld; public interface Chunk{void setBlock(int x,int y,int z,BlockState s,boolean p);void setBlock(int x,int y,int z,BlockState s);BlockState getBlock(int x,int y,int z);int getX();int getZ();ServerWorld getWorld();Object getHandle();}
JAVA
cat > "$STUB/org/jetbrains/annotations/NotNull.java" <<'JAVA'
package org.jetbrains.annotations; import java.lang.annotation.*; @Target({ElementType.METHOD,ElementType.PARAMETER,ElementType.TYPE_USE}) @Retention(RetentionPolicy.CLASS) public @interface NotNull{}
JAVA
cat > "$STUB/org/powernukkitx/block/property/type/BlockPropertyType.java" <<'JAVA'
package org.powernukkitx.block.property.type; import java.util.List; public interface BlockPropertyType<T>{String getName();T getDefaultValue();List<T> getValidValues(); interface BlockPropertyValue<T>{BlockPropertyType<T> getPropertyType();T getValue();Object getSerializedValue();}}
JAVA
cat > "$STUB/org/powernukkitx/block/BlockProperties.java" <<'JAVA'
package org.powernukkitx.block; import java.util.Set; import org.powernukkitx.block.property.type.BlockPropertyType; public class BlockProperties{private final Set<BlockPropertyType<?>> p;public BlockProperties(Set<BlockPropertyType<?>> p){this.p=p;}public Set<BlockPropertyType<?>> getPropertyTypeSet(){return p;}}
JAVA
cat > "$STUB/org/powernukkitx/block/BlockState.java" <<'JAVA'
package org.powernukkitx.block; import java.util.List; import org.powernukkitx.block.property.type.BlockPropertyType; public interface BlockState{String getIdentifier();List<? extends BlockPropertyType.BlockPropertyValue<?>> getBlockPropertyValues();<T> T getPropertyValue(BlockPropertyType<T> p);<T> BlockState setPropertyValue(BlockProperties b,BlockPropertyType<T> p,T v);}
JAVA
cat > "$STUB/org/powernukkitx/block/BlockAir.java" <<'JAVA'
package org.powernukkitx.block; public final class BlockAir{public static final BlockState STATE=null;}
JAVA
cat > "$STUB/org/powernukkitx/registry/Registries.java" <<'JAVA'
package org.powernukkitx.registry; import java.util.*; import org.powernukkitx.block.BlockProperties; public final class Registries{public static final Registry BLOCK=new Registry();public static final class Registry{private final Map<String,BlockProperties> map=new HashMap<>();public BlockProperties getBlockProperties(String id){return map.get(id);}public void register(String id,BlockProperties p){map.put(id,p);}}}
JAVA
cat > "$STUB/org/powernukkitx/level/format/IChunk.java" <<'JAVA'
package org.powernukkitx.level.format; import org.powernukkitx.block.BlockState; public interface IChunk{int getX();int getZ();void setBlockState(int x,int y,int z,BlockState s);BlockState getBlockState(int x,int y,int z);}
JAVA
cat > "$STUB/org/powernukkitx/level/Level.java" <<'JAVA'
package org.powernukkitx.level; public class Level{public int physicsUpdates;public void updateAround(int x,int y,int z){physicsUpdates++;}}
JAVA
cat > "$STUB/org/powernukkitx/block/Block.java" <<'JAVA'
package org.powernukkitx.block; public class Block{private final BlockState state;public Block(BlockState s){state=s;}public BlockState getBlockState(){return state;}}
JAVA
cat > "$STUB/org/powernukkitx/blockentity/BlockEntity.java" <<'JAVA'
package org.powernukkitx.blockentity; import org.powernukkitx.block.Block; import org.powernukkitx.level.Level; public class BlockEntity{public boolean closed;public int x=4,y=70,z=9;public Level level=new Level();public Block block;public boolean valid=true;public boolean updateResult=true;public boolean isValid(){return valid;}public boolean onUpdate(){return updateResult;}public Level getLevel(){return level;}public double getX(){return x;}public double getY(){return y;}public double getZ(){return z;}public Block getBlock(){return block;}}
JAVA
cat > "$STUB/org/powernukkitx/terra/PNXAdapter.java" <<'JAVA'
package org.powernukkitx.terra; import org.powernukkitx.block.BlockState; import org.powernukkitx.terra.delegate.PNXBlockStateDelegate; public final class PNXAdapter{public static PNXBlockStateDelegate adapt(BlockState s){return new PNXBlockStateDelegate(s);}}
JAVA
cat > "$STUB/org/powernukkitx/terra/delegate/PNXBlockType.java" <<'JAVA'
package org.powernukkitx.terra.delegate; import com.dfsek.terra.api.block.BlockType; public final class PNXBlockType implements BlockType{public PNXBlockType(org.powernukkitx.block.BlockState s){}}
JAVA
cat > "$STUB/org/powernukkitx/terra/delegate/PNXDelegateContractTest.java" <<'JAVA'
package org.powernukkitx.terra.delegate;
import java.util.*;
import com.dfsek.terra.api.block.state.properties.Property;
import com.dfsek.terra.api.world.ServerWorld;
import org.powernukkitx.block.*;
import org.powernukkitx.block.property.type.BlockPropertyType;
import org.powernukkitx.level.format.IChunk;
import org.powernukkitx.registry.Registries;
final class PNXDelegateContractTest {
  record AxisProperty() implements BlockPropertyType<String> {
    public String getName(){return "axis";} public String getDefaultValue(){return "y";}
    public List<String> getValidValues(){return List.of("x","y","z");}
  }
  record AxisValue(BlockPropertyType<String> getPropertyType,String getValue) implements BlockPropertyType.BlockPropertyValue<String> {
    public Object getSerializedValue(){return getValue;}
  }
  static final class State implements org.powernukkitx.block.BlockState {
    final String axis; final AxisProperty property;
    State(String axis,AxisProperty property){this.axis=axis;this.property=property;}
    public String getIdentifier(){return "minecraft:oak_log";}
    public List<AxisValue> getBlockPropertyValues(){return List.of(new AxisValue(property,axis));}
    @SuppressWarnings("unchecked") public <T>T getPropertyValue(BlockPropertyType<T> p){return (T)axis;}
    public <T>org.powernukkitx.block.BlockState setPropertyValue(BlockProperties b,BlockPropertyType<T> p,T v){return new State((String)v,property);}
  }
  record TerraProperty(String getID,Class<String> getType,Collection<String> values) implements Property<String>{}
  static final class Chunk implements IChunk {
    int x=12,z=-4; org.powernukkitx.block.BlockState state;
    public int getX(){return x;} public int getZ(){return z;}
    public void setBlockState(int x,int y,int z,org.powernukkitx.block.BlockState s){state=s;}
    public org.powernukkitx.block.BlockState getBlockState(int x,int y,int z){return state;}
  }
  public static void main(String[] args){
    AxisProperty axis=new AxisProperty(); BlockProperties props=new BlockProperties(Set.of(axis));
    Registries.BLOCK.register("minecraft:oak_log",props);
    PNXBlockStateDelegate state=new PNXBlockStateDelegate(new State("y",axis));
    TerraProperty terraAxis=new TerraProperty("axis",String.class,List.of("x","y","z"));
    if(!state.has(terraAxis)||!"y".equals(state.get(terraAxis)))throw new AssertionError("exact property mapping failed");
    PNXBlockStateDelegate changed=(PNXBlockStateDelegate)state.set(terraAxis,"x");
    if(!"x".equals(changed.get(terraAxis)))throw new AssertionError("property set failed");
    if(!"minecraft:oak_log[axis=x]".equals(changed.getAsString(true)))throw new AssertionError("serialized property string failed");
    TerraProperty incompatible=new TerraProperty("axis",String.class,List.of("north","south"));
    if(changed.has(incompatible))throw new AssertionError("incompatible value schema was accepted");
    Chunk chunk=new Chunk(); chunk.state=new State("y",axis);
    ServerWorld world=new ServerWorld(){};
    PNXChunkDelegate delegate=new PNXChunkDelegate(world,chunk);
    if(delegate.getX()!=12||delegate.getZ()!=-4)throw new AssertionError("chunk coordinates are not delegated");
    org.powernukkitx.blockentity.BlockEntity rawEntity=new org.powernukkitx.blockentity.BlockEntity(); rawEntity.block=new org.powernukkitx.block.Block(chunk.state);
    PNXBlockEntityDelegate be=new PNXBlockEntityDelegate(rawEntity);
    if(be.getX()!=4||be.getY()!=70||be.getZ()!=9||be.getBlockState()==null)throw new AssertionError("block entity read adapter failed");
    boolean updateRejected=false;try{be.update(true);}catch(UnsupportedOperationException expected){updateRejected=true;}
    if(!updateRejected)throw new AssertionError("un-journaled block entity update was allowed");
    boolean rejected=false;try{be.applyState("unknown=value");}catch(UnsupportedOperationException expected){rejected=true;}
    if(!rejected)throw new AssertionError("unknown NBT state was silently accepted");
    System.out.println("PNXDelegateContractTest: PASS");
  }
}
JAVA
# Compile the actual delegate sources against narrow API contracts and run behavior checks.
mapfile -t SOURCES < <(find "$STUB" -name '*.java')
mkdir -p "$BUILD/classes"
javac --release 21 -Xlint:all -Werror -d "$BUILD/classes" "${SOURCES[@]}" \
  "$ROOT/source/org/powernukkitx/terra/delegate/PNXBlockStateDelegate.java" \
  "$ROOT/source/org/powernukkitx/terra/delegate/PNXChunkDelegate.java" \
  "$ROOT/source/org/powernukkitx/terra/delegate/PNXBlockEntityDelegate.java"
java -cp "$BUILD/classes" org.powernukkitx.terra.delegate.PNXDelegateContractTest
echo "PNX block-state/chunk/block-entity read delegates Java 21 contract compile: PASS"
