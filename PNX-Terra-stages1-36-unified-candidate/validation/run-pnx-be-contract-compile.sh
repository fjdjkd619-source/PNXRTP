#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="${BUILD_DIR:-$ROOT/validation/pnx-be-contract-build}"
STUB="$BUILD/stubs"
rm -rf "$BUILD"
mkdir -p "$STUB/org/cloudburstmc/nbt" "$STUB/org/powernukkitx/nbt/tag" "$STUB/org/powernukkitx/level" "$STUB/org/powernukkitx/level/format" "$STUB/org/powernukkitx/scheduler" "$STUB/org/powernukkitx/terra/crosschunk/journal/pnx"
cat > "$STUB/org/cloudburstmc/nbt/NbtMap.java" <<'JAVA'
package org.cloudburstmc.nbt;
import java.util.LinkedHashMap;
public class NbtMap extends LinkedHashMap<String,Object> { private static final long serialVersionUID=1L; }
JAVA
cat > "$STUB/org/cloudburstmc/nbt/NbtList.java" <<'JAVA'
package org.cloudburstmc.nbt;
import java.util.ArrayList;
public final class NbtList extends ArrayList<Object> {private static final long serialVersionUID=1L;private final int tagType;public NbtList(int tagType){this.tagType=tagType;}public int getTagType(){return tagType;}}
JAVA
cat > "$STUB/org/cloudburstmc/nbt/NBTOutputStream.java" <<'JAVA'
package org.cloudburstmc.nbt;
import java.io.*;
public final class NBTOutputStream implements AutoCloseable {
 private final DataOutputStream out; public NBTOutputStream(OutputStream out){this.out=new DataOutputStream(out);}
 public void writeTag(NbtMap map)throws IOException{writeMap(map);}
 private void writeMap(NbtMap map)throws IOException{out.writeInt(map.size());for(var e:map.entrySet()){out.writeUTF(e.getKey());Object v=e.getValue();if(v instanceof String x){out.writeByte(1);out.writeUTF(x);}else if(v instanceof Integer x){out.writeByte(2);out.writeInt(x);}else if(v instanceof Byte x){out.writeByte(3);out.writeByte(x);}else if(v instanceof Boolean x){out.writeByte(4);out.writeBoolean(x);}else if(v instanceof Long x){out.writeByte(5);out.writeLong(x);}else if(v instanceof NbtMap x){out.writeByte(6);writeMap(x);}else if(v instanceof Short x){out.writeByte(7);out.writeShort(x);}else if(v instanceof NbtList x){out.writeByte(8);out.writeInt(x.getTagType());out.writeInt(x.size());for(Object item:x)writeMap((NbtMap)item);}else throw new IOException("unsupported stub NBT value: "+v);}}
 public void close()throws IOException{out.flush();}
}
JAVA
cat > "$STUB/org/cloudburstmc/nbt/NBTInputStream.java" <<'JAVA'
package org.cloudburstmc.nbt;
import java.io.*;
public final class NBTInputStream implements AutoCloseable {
 private final DataInputStream in; public NBTInputStream(InputStream in){this.in=new DataInputStream(in);}
 public Object readTag()throws IOException{return readMap();}
 private NbtMap readMap()throws IOException{NbtMap map=new NbtMap();int size=in.readInt();for(int i=0;i<size;i++){String key=in.readUTF();int type=in.readUnsignedByte();Object v=switch(type){case 1->in.readUTF();case 2->in.readInt();case 3->in.readByte();case 4->in.readBoolean();case 5->in.readLong();case 6->readMap();case 7->in.readShort();case 8->{int tagType=in.readInt();int count=in.readInt();NbtList list=new NbtList(tagType);for(int j=0;j<count;j++)list.add(readMap());yield list;}default->throw new IOException("unsupported stub NBT type: "+type);};map.put(key,v);}return map;}
 public void close()throws IOException{in.close();}
}
JAVA
cat > "$STUB/org/cloudburstmc/nbt/NbtUtils.java" <<'JAVA'
package org.cloudburstmc.nbt;
import java.io.*;
public final class NbtUtils { public static NBTOutputStream createWriterLE(OutputStream out){return new NBTOutputStream(out);} public static NBTInputStream createReaderLE(InputStream in){return new NBTInputStream(in);} }
JAVA
cat > "$STUB/org/powernukkitx/nbt/tag/CompoundTag.java" <<'JAVA'
package org.powernukkitx.nbt.tag;
import org.cloudburstmc.nbt.NbtMap;
public final class CompoundTag {
 private final NbtMap map=new NbtMap();
 public CompoundTag putString(String k,String v){map.put(k,v);return this;} public CompoundTag putInt(String k,int v){map.put(k,v);return this;} public CompoundTag putByte(String k,byte v){map.put(k,v);return this;} public CompoundTag putShort(String k,short v){map.put(k,v);return this;} public CompoundTag putBoolean(String k,boolean v){map.put(k,v);return this;} public CompoundTag putLong(String k,long v){map.put(k,v);return this;} public CompoundTag putCompound(String k,CompoundTag v){map.put(k,v.toNetwork());return this;} public CompoundTag putList(String k,ListTag<CompoundTag> v){map.put(k,v);return this;} public void remove(String k){map.remove(k);}
 public String getString(String k){return (String)map.get(k);} public int getInt(String k){return (Integer)map.get(k);} public short getShort(String k){return (Short)map.get(k);} @SuppressWarnings("unchecked") public ListTag<CompoundTag> getList(String k){return (ListTag<CompoundTag>)map.get(k);} public boolean contains(String k){return map.containsKey(k);} public boolean containsCompound(String k){return map.get(k) instanceof NbtMap || map.get(k) instanceof CompoundTag;} public CompoundTag getCompound(String k){Object v=map.get(k);return v instanceof CompoundTag c?c.copy():fromNetwork((NbtMap)v);}
 public CompoundTag copy(){return fromNetwork(toNetwork());}
 public NbtMap toNetwork(){NbtMap n=new NbtMap();for(var e:map.entrySet()){Object v=e.getValue();if(v instanceof CompoundTag c)v=c.toNetwork();else if(v instanceof ListTag<?> list){org.cloudburstmc.nbt.NbtList nl=new org.cloudburstmc.nbt.NbtList(list.getTagType());for(Object item:list)nl.add(((CompoundTag)item).toNetwork());v=nl;}n.put(e.getKey(),v);}return n;}
 public static CompoundTag fromNetwork(NbtMap n){CompoundTag c=new CompoundTag();for(var e:n.entrySet()){Object v=e.getValue();if(v instanceof NbtMap m)v=fromNetwork(m);else if(v instanceof org.cloudburstmc.nbt.NbtList nl){ListTag<CompoundTag> list=new ListTag<>(nl.getTagType());for(Object item:nl)list.add(fromNetwork((NbtMap)item));v=list;}c.map.put(e.getKey(),v);}return c;}
}
JAVA
cat > "$STUB/org/powernukkitx/scheduler/ServerScheduler.java" <<'JAVA'
package org.powernukkitx.scheduler;
import java.util.ArrayDeque;
public final class ServerScheduler {private final ArrayDeque<Runnable> tasks=new ArrayDeque<>();public void scheduleTask(Runnable r){tasks.add(r);}public void drain(){while(!tasks.isEmpty())tasks.remove().run();}}
JAVA
cat > "$STUB/org/powernukkitx/level/Level.java" <<'JAVA'
package org.powernukkitx.level;
import org.powernukkitx.scheduler.ServerScheduler;
public final class Level {private final ServerScheduler scheduler=new ServerScheduler();private int blockEntities;public ServerScheduler getScheduler(){return scheduler;}public void tick(){scheduler.drain();}public void addBlockEntity(){blockEntities++;}public void removeBlockEntity(){blockEntities--;}public int blockEntityCount(){return blockEntities;}}
JAVA
cat > "$STUB/org/powernukkitx/level/DimensionData.java" <<'JAVA'
package org.powernukkitx.level;
public final class DimensionData {public int getMinHeight(){return 0;}public int getMaxHeight(){return 256;}}
JAVA
cat > "$STUB/org/powernukkitx/level/format/LevelProvider.java" <<'JAVA'
package org.powernukkitx.level.format;
import org.powernukkitx.level.Level;
public final class LevelProvider {private final Level level;public LevelProvider(Level level){this.level=level;}public Level getLevel(){return level;}}
JAVA
cat > "$STUB/org/powernukkitx/level/format/IChunk.java" <<'JAVA'
package org.powernukkitx.level.format;
import org.powernukkitx.blockentity.BlockEntity;
import org.powernukkitx.level.DimensionData;
public interface IChunk {int getX();int getZ();boolean isLoaded();boolean isGenerated();boolean isInitiated();LevelProvider getProvider();DimensionData getDimensionData();BlockEntity getTile(int x,int y,int z);java.util.Map<Long,BlockEntity> getBlockEntities();void addBlockEntity(BlockEntity entity);void removeBlockEntity(BlockEntity entity);}
JAVA
mkdir -p "$STUB/org/powernukkitx/registry" "$STUB/org/powernukkitx/blockentity" "$STUB/org/powernukkitx/terra/crosschunk/journal"
cat > "$STUB/org/powernukkitx/registry/Registries.java" <<'JAVA'
package org.powernukkitx.registry;
import java.util.*;
import org.powernukkitx.blockentity.BlockEntity;
public final class Registries {public static final BlockEntityRegistry BLOCKENTITY=new BlockEntityRegistry();public static final EntityRegistry ENTITY=new EntityRegistry();public static final class EntityRegistry {private final Map<String,Integer> ids=new HashMap<>();private final Map<Integer,String> names=new HashMap<>();public void register(String name,int id){ids.put(name,id);names.put(id,name);}public int getEntityNetworkId(String name){return ids.getOrDefault(name,0);}public String getEntityIdentifier(int id){return names.get(id);}}public static final class BlockEntityRegistry {private final Map<String,Class<? extends BlockEntity>> types=new HashMap<>();public void register(String id,Class<? extends BlockEntity> type){types.put(id,type);}public Class<? extends BlockEntity> get(String id){return types.get(id);}public String getSaveId(Class<? extends BlockEntity> type){return types.entrySet().stream().filter(e->e.getValue().equals(type)).map(Map.Entry::getKey).findFirst().orElse(null);}}}
JAVA
cat > "$STUB/org/powernukkitx/blockentity/BlockEntity.java" <<'JAVA'
package org.powernukkitx.blockentity;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.format.IChunk;
import org.powernukkitx.nbt.tag.CompoundTag;
import org.powernukkitx.registry.Registries;
public class BlockEntity {private static long nextId=1;public final long id=nextId++;public final IChunk chunk;private final Level level;private final CompoundTag nbt;private final int x,y,z;private boolean closed;
 @SuppressWarnings("this-escape") public BlockEntity(IChunk chunk,CompoundTag nbt){this.chunk=chunk;this.level=chunk.getProvider().getLevel();this.nbt=nbt;this.x=nbt.getInt("x");this.y=nbt.getInt("y");this.z=nbt.getInt("z");chunk.addBlockEntity(this);level.addBlockEntity();}
 public static BlockEntity createBlockEntity(String type,IChunk chunk,CompoundTag nbt){Class<? extends BlockEntity> c=Registries.BLOCKENTITY.get(type);if(c==null)return null;try{return c.getConstructor(IChunk.class,CompoundTag.class).newInstance(chunk,nbt);}catch(ReflectiveOperationException e){return null;}}
 public long getId(){return id;}public void saveNBT(){nbt.putString("id",Registries.BLOCKENTITY.getSaveId(getClass())).putInt("x",x).putInt("y",y).putInt("z",z);}
 public CompoundTag getNbt(){return nbt;}public Level getLevel(){return level;}public double getX(){return x;}public double getY(){return y;}public double getZ(){return z;}
 public void close(){if(!closed){closed=true;chunk.removeBlockEntity(this);level.removeBlockEntity();}}
}
JAVA
cat > "$STUB/org/powernukkitx/terra/crosschunk/journal/PlacementProjection.java" <<'JAVA'
package org.powernukkitx.terra.crosschunk.journal;
public final class PlacementProjection {public record Position(int x,int y,int z,int layer) implements Comparable<Position>{public int compareTo(Position o){int c=Integer.compare(x,o.x);if(c==0)c=Integer.compare(y,o.y);if(c==0)c=Integer.compare(z,o.z);return c==0?Integer.compare(layer,o.layer):c;}}public record EntityWinner(String placementId,BlockEntityIntent intent){}}
JAVA
cat > "$STUB/org/powernukkitx/terra/crosschunk/journal/pnx/PnxBlockEntityNbtContractTest.java" <<'JAVA'
package org.powernukkitx.terra.crosschunk.journal.pnx;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.powernukkitx.level.Level;
import org.powernukkitx.nbt.tag.CompoundTag;
import org.powernukkitx.terra.crosschunk.journal.BlockEntityIntent;
import org.powernukkitx.terra.crosschunk.runtime.PnxLevelExecutor;
public final class PnxBlockEntityNbtContractTest {
 public static void main(String[] args)throws Exception{
  CompoundTag original=new CompoundTag().putString("CustomName","Archive chest");
  BlockEntityIntent intent=PnxBlockEntityNbtCodec.toIntent(-17,72,33,"Chest",original);
  CompoundTag decoded=PnxBlockEntityNbtCodec.fromIntent(intent);
  check("Chest".equals(decoded.getString("id")),"type persisted");
  check(decoded.getInt("x")==-17&&decoded.getInt("y")==72&&decoded.getInt("z")==33,"coordinates persisted");
  check(original.getString("id")==null,"source tag was not mutated");
  CompoundTag tampered=decoded.copy().putString("CustomName","tampered after hash");
  boolean rejected=false;try{PnxBlockEntityNbtCodec.fromIntent(new BlockEntityIntent(-17,72,33,"Chest",PnxBlockEntityNbtCodec.encode(tampered)));}catch(java.io.IOException expected){rejected=true;}
  check(rejected,"intent payload hash rejects tampering after capture");
  byte[] wrong= PnxBlockEntityNbtCodec.encode(new CompoundTag().putString("id","Chest").putInt("x",0).putInt("y",72).putInt("z",33));
  rejected=false;try{PnxBlockEntityNbtCodec.fromIntent(new BlockEntityIntent(-17,72,33,"Chest",wrong));}catch(java.io.IOException expected){rejected=true;}
  check(rejected,"coordinate drift rejected");
  byte[] trailing=Arrays.copyOf(intent.payload(),intent.payload().length+1);
  rejected=false;try{PnxBlockEntityNbtCodec.decode(trailing);}catch(java.io.IOException expected){rejected=true;}
  check(rejected,"trailing bytes rejected");
  Level level=new Level();AtomicBoolean ran=new AtomicBoolean();PnxLevelExecutor.forLevel(level).execute(()->ran.set(true));
  check(!ran.get(),"level executor queues on owning scheduler");level.tick();check(ran.get(),"level executor task runs on scheduler heartbeat");
  System.out.println("PnxBlockEntityNbtContractTest: PASS");
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
JAVA
cat > "$STUB/org/powernukkitx/terra/crosschunk/journal/pnx/PnxBlockEntityIntentApplierContractTest.java" <<'JAVA'
package org.powernukkitx.terra.crosschunk.journal.pnx;
import java.util.*;
import org.powernukkitx.blockentity.BlockEntity;
import org.powernukkitx.level.*;
import org.powernukkitx.level.format.*;
import org.powernukkitx.nbt.tag.CompoundTag;
import org.powernukkitx.registry.Registries;
import org.powernukkitx.terra.crosschunk.journal.*;
import org.powernukkitx.terra.crosschunk.journal.PlacementProjection.*;
public final class PnxBlockEntityIntentApplierContractTest {
 public static final class Chest extends BlockEntity {public Chest(IChunk c,CompoundTag n){super(c,n);}}
 public static final class Sign extends BlockEntity {public Sign(IChunk c,CompoundTag n){super(c,n);}}
 public static final class Failing extends BlockEntity {public Failing(IChunk c,CompoundTag n){super(c,n);throw new IllegalStateException("simulated constructor failure");}}
 static final class Chunk implements IChunk {final int x=-1,z=2;final Level level=new Level();final LevelProvider provider=new LevelProvider(level);final DimensionData dimension=new DimensionData();final Map<Long,BlockEntity> tiles=new HashMap<>();public int getX(){return x;}public int getZ(){return z;}public boolean isLoaded(){return true;}public boolean isGenerated(){return true;}public boolean isInitiated(){return true;}public LevelProvider getProvider(){return provider;}public DimensionData getDimensionData(){return dimension;}String key(int x,int y,int z){return x+":"+y+":"+z;}public BlockEntity getTile(int x,int y,int z){return tiles.values().stream().filter(e->(((int)e.getX())&15)==x&&(int)e.getY()==y&&(((int)e.getZ())&15)==z).findFirst().orElse(null);}public Map<Long,BlockEntity> getBlockEntities(){return tiles;}public void addBlockEntity(BlockEntity e){tiles.put(e.getId(),e);}public void removeBlockEntity(BlockEntity e){tiles.remove(e.getId(),e);}}
 public static void main(String[] args)throws Exception{
  Registries.BLOCKENTITY.register("Chest",Chest.class);Registries.BLOCKENTITY.register("Sign",Sign.class);Registries.BLOCKENTITY.register("Fail",Failing.class);
  Chunk chunk=new Chunk();BlockEntityIntent chest=PnxBlockEntityNbtCodec.toIntent(-1,64,33,"Chest",new CompoundTag());
  TreeMap<Position,EntityWinner> one=new TreeMap<>();one.put(new Position(-1,64,33,0),new EntityWinner("p1",chest));
  PnxBlockEntityIntentApplier.apply(chunk,one);check(chunk.level.blockEntityCount()==1&&chunk.getTile(15,64,1)!=null,"new entity registered in chunk and level");
  PnxBlockEntityIntentApplier.apply(chunk,one);check(chunk.level.blockEntityCount()==1,"identical replay is idempotent");
  BlockEntityIntent conflict=PnxBlockEntityNbtCodec.toIntent(-1,64,33,"Sign",new CompoundTag());TreeMap<Position,EntityWinner> bad=new TreeMap<>();bad.put(new Position(-1,64,33,0),new EntityWinner("p2",conflict));boolean rejected=false;try{PnxBlockEntityIntentApplier.apply(chunk,bad);}catch(java.io.IOException expected){rejected=true;}check(rejected&&chunk.level.blockEntityCount()==1,"type collision rejected without replacing existing entity");
  BlockEntityIntent first=PnxBlockEntityNbtCodec.toIntent(-1,65,33,"Chest",new CompoundTag());BlockEntityIntent fails=PnxBlockEntityNbtCodec.toIntent(-1,66,33,"Fail",new CompoundTag());TreeMap<Position,EntityWinner> batch=new TreeMap<>();batch.put(new Position(-1,65,33,0),new EntityWinner("p3",first));batch.put(new Position(-1,66,33,0),new EntityWinner("p3",fails));rejected=false;try{PnxBlockEntityIntentApplier.apply(chunk,batch);}catch(java.io.IOException expected){rejected=true;}check(rejected&&chunk.level.blockEntityCount()==1&&chunk.getTile(15,65,1)==null,"failed batch rolls back entities already created");
  System.out.println("PnxBlockEntityIntentApplierContractTest: PASS");
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
JAVA

mkdir -p "$STUB/com/dfsek/seismic/type/vector" "$STUB/com/dfsek/terra/api/block/entity" "$STUB/com/dfsek/terra/api/block/state" "$STUB/org/powernukkitx/utils"
cat > "$STUB/com/dfsek/seismic/type/vector/Vector3.java" <<'JAVA'
package com.dfsek.seismic.type.vector;
public record Vector3(double x,double y,double z){public static Vector3 of(double x,double y,double z){return new Vector3(x,y,z);}}
JAVA
cat > "$STUB/com/dfsek/terra/api/block/state/BlockState.java" <<'JAVA'
package com.dfsek.terra.api.block.state;
public interface BlockState { }
JAVA
cat > "$STUB/com/dfsek/terra/api/block/entity/BlockEntity.java" <<'JAVA'
package com.dfsek.terra.api.block.entity;
import com.dfsek.seismic.type.vector.Vector3;
import com.dfsek.terra.api.block.state.BlockState;
public interface BlockEntity {boolean update(boolean physics);void applyState(String state);Vector3 getPosition();int getX();int getY();int getZ();BlockState getBlockState();Object getHandle();}
JAVA
cat > "$STUB/com/dfsek/terra/api/block/entity/Sign.java" <<'JAVA'
package com.dfsek.terra.api.block.entity;
public interface Sign extends BlockEntity {void setLine(int index,String line);String[] getLines();String getLine(int index);}
JAVA
cat > "$STUB/com/dfsek/terra/api/block/entity/SerialState.java" <<'JAVA'
package com.dfsek.terra.api.block.entity;
import java.util.*;
public final class SerialState {public static Map<String,String> parse(String state){Map<String,String> out=new LinkedHashMap<>();for(String p:state.split(",")){String[] kv=p.split("=",2);if(kv.length!=2)throw new IllegalArgumentException(p);out.put(kv[0],kv[1]);}return out;}}
JAVA
cat > "$STUB/org/powernukkitx/utils/DyeColor.java" <<'JAVA'
package org.powernukkitx.utils;
public enum DyeColor {BLACK;public int getSignColor(){return 0xff000000;}}
JAVA
cat > "$STUB/org/powernukkitx/terra/crosschunk/journal/pnx/PnxStagedSignContractTest.java" <<'JAVA'
package org.powernukkitx.terra.crosschunk.journal.pnx;
import java.util.concurrent.atomic.AtomicReference;
import com.dfsek.terra.api.block.state.BlockState;
import org.powernukkitx.nbt.tag.CompoundTag;
import org.powernukkitx.terra.crosschunk.journal.BlockEntityIntent;
import org.powernukkitx.terra.delegate.PNXStagedSignDelegate;
public final class PnxStagedSignContractTest {
 public static void main(String[] args)throws Exception{
  AtomicReference<BlockEntityIntent> committed=new AtomicReference<>();
  PNXStagedSignDelegate sign=new PNXStagedSignDelegate(12,70,-3,"Sign",new BlockState(){},new CompoundTag(),committed::set);
  sign.setLine(0,"Castle gate");sign.applyState("text1=Keep,text3=Welcome");
  check(sign.getLines().length==4&&"Castle gate".equals(sign.getLine(0))&&"Keep".equals(sign.getLine(1))&&"Welcome".equals(sign.getLine(3)),"Terra sign state mapped to front text lines");
  check(sign.update(false),"staged sign update accepted");
  CompoundTag nbt=PnxBlockEntityNbtCodec.fromIntent(committed.get());
  check("Castle gate\nKeep\n\nWelcome".equals(nbt.getCompound("FrontText").getString("Text")),"sign text encoded into canonical PNX NBT");
  boolean rejected=false;try{sign.setLine(4,"bad");}catch(IndexOutOfBoundsException expected){rejected=true;}check(rejected,"invalid sign line rejected");
  rejected=false;try{sign.update(true);}catch(UnsupportedOperationException expected){rejected=true;}check(rejected,"physics is not silently folded into BlockEntity intent");
  System.out.println("PnxStagedSignContractTest: PASS");
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
JAVA

mkdir -p "$STUB/com/dfsek/terra/api/inventory" "$STUB/com/dfsek/terra/api/entity" "$STUB/org/powernukkitx/inventory" "$STUB/org/powernukkitx/item" "$STUB/org/powernukkitx/terra/delegate" "$STUB/org/powernukkitx/nbt/tag"
cat > "$STUB/com/dfsek/terra/api/entity/EntityType.java" <<'JAVA'
package com.dfsek.terra.api.entity;
public interface EntityType {Object getHandle();}
JAVA
cat > "$STUB/com/dfsek/terra/api/inventory/ItemStack.java" <<'JAVA'
package com.dfsek.terra.api.inventory;
public interface ItemStack {Object getHandle();}
JAVA
cat > "$STUB/com/dfsek/terra/api/inventory/Inventory.java" <<'JAVA'
package com.dfsek.terra.api.inventory;
public interface Inventory {void setItem(int slot,ItemStack stack);int getSize();ItemStack getItem(int slot);Object getHandle();}
JAVA
cat > "$STUB/com/dfsek/terra/api/inventory/BlockInventoryHolder.java" <<'JAVA'
package com.dfsek.terra.api.inventory;
import com.dfsek.seismic.type.vector.Vector3;
public interface BlockInventoryHolder {Inventory getInventory();Vector3 getPosition();}
JAVA
cat > "$STUB/com/dfsek/terra/api/block/entity/Container.java" <<'JAVA'
package com.dfsek.terra.api.block.entity;
import com.dfsek.terra.api.inventory.BlockInventoryHolder;
public interface Container extends BlockEntity,BlockInventoryHolder { }
JAVA
cat > "$STUB/com/dfsek/terra/api/block/entity/MobSpawner.java" <<'JAVA'
package com.dfsek.terra.api.block.entity;
import com.dfsek.terra.api.entity.EntityType;
public interface MobSpawner extends BlockEntity {EntityType getSpawnedType();void setSpawnedType(EntityType t);int getDelay();void setDelay(int d);int getMinSpawnDelay();void setMinSpawnDelay(int d);int getMaxSpawnDelay();void setMaxSpawnDelay(int d);int getSpawnCount();void setSpawnCount(int c);int getMaxNearbyEntities();void setMaxNearbyEntities(int c);int getRequiredPlayerRange();void setRequiredPlayerRange(int r);int getSpawnRange();void setSpawnRange(int r);}
JAVA
cat > "$STUB/org/powernukkitx/item/Item.java" <<'JAVA'
package org.powernukkitx.item;
public class Item implements Cloneable {private final String id;private int count;public Item(String id,int count){this.id=id;this.count=count;}public boolean isNull(){return count<=0;}public int getCount(){return count;}public void setCount(int c){count=c;}public String getId(){return id;}public Item clone(){return new Item(id,count);}}
JAVA
cat > "$STUB/org/powernukkitx/inventory/Inventory.java" <<'JAVA'
package org.powernukkitx.inventory;
import org.powernukkitx.item.Item;
public class Inventory {private final Item[] items;public Inventory(int n){items=new Item[n];}public int getSize(){return items.length;}public Item getItem(int i){return items[i];}public void setItem(int i,Item item){items[i]=item;}}
JAVA
cat > "$STUB/org/powernukkitx/blockentity/BlockEntityInventoryHolder.java" <<'JAVA'
package org.powernukkitx.blockentity;
import org.powernukkitx.inventory.Inventory;
public interface BlockEntityInventoryHolder {Inventory getInventory();}
JAVA
cat > "$STUB/org/powernukkitx/blockentity/BlockEntitySpawnableContainer.java" <<'JAVA'
package org.powernukkitx.blockentity;
import org.powernukkitx.inventory.Inventory;
public class BlockEntitySpawnableContainer extends BlockEntity implements BlockEntityInventoryHolder {private final Inventory inventory=new Inventory(27);public BlockEntitySpawnableContainer(org.powernukkitx.level.format.IChunk c,org.powernukkitx.nbt.tag.CompoundTag n){super(c,n);}public Inventory getInventory(){return inventory;}}
JAVA
cat > "$STUB/org/powernukkitx/nbt/tag/Tag.java" <<'JAVA'
package org.powernukkitx.nbt.tag;
public final class Tag {public static final int TAG_Compound=10;}
JAVA
cat > "$STUB/org/powernukkitx/nbt/tag/ListTag.java" <<'JAVA'
package org.powernukkitx.nbt.tag;
import java.util.ArrayList;
public final class ListTag<T> extends ArrayList<T> {private static final long serialVersionUID=1L;private final int type;public ListTag(int type){this.type=type;}public int getTagType(){return type;}}
JAVA
cat > "$STUB/org/powernukkitx/utils/ItemHelper.java" <<'JAVA'
package org.powernukkitx.utils;
import org.powernukkitx.item.Item;import org.powernukkitx.nbt.tag.CompoundTag;
public final class ItemHelper {public static CompoundTag write(Item item,Integer slot){CompoundTag n=new CompoundTag().putString("Name",item.getId()).putInt("Count",item.getCount());if(slot!=null)n.putInt("Slot",slot);return n;}}
JAVA
cat > "$STUB/org/powernukkitx/terra/delegate/PNXItemStack.java" <<'JAVA'
package org.powernukkitx.terra.delegate;
import com.dfsek.terra.api.inventory.ItemStack;import org.powernukkitx.item.Item;
public record PNXItemStack(Item innerItem) implements ItemStack {public Object getHandle(){return innerItem;}}
JAVA
cat > "$STUB/org/powernukkitx/terra/delegate/PNXEntityType.java" <<'JAVA'
package org.powernukkitx.terra.delegate;
import com.dfsek.terra.api.entity.EntityType;
public record PNXEntityType(String identifier) implements EntityType {public Object getHandle(){return identifier;}}
JAVA

cat > "$STUB/org/powernukkitx/terra/crosschunk/journal/pnx/PnxStagedSpawnerContainerContractTest.java" <<'JAVA'
package org.powernukkitx.terra.crosschunk.journal.pnx;
import java.util.concurrent.atomic.AtomicReference;
import com.dfsek.terra.api.block.state.BlockState;
import com.dfsek.terra.api.inventory.ItemStack;
import org.powernukkitx.item.Item;
import org.powernukkitx.nbt.tag.CompoundTag;
import org.powernukkitx.registry.Registries;
import org.powernukkitx.terra.crosschunk.journal.BlockEntityIntent;
import org.powernukkitx.terra.delegate.*;
public final class PnxStagedSpawnerContainerContractTest {
 public static void main(String[] args)throws Exception{
  Registries.ENTITY.register("minecraft:zombie",32);
  AtomicReference<BlockEntityIntent> spawnerIntent=new AtomicReference<>();
  PNXStagedMobSpawnerDelegate spawner=new PNXStagedMobSpawnerDelegate(8,50,9,"MobSpawner",new BlockState(){},new CompoundTag(),spawnerIntent::set);
  spawner.applyState("type=minecraft:zombie,delay=35,min_delay=100,max_delay=300,spawn_count=3,spawn_range=5,max_nearby=8,required_player_range=24");
  spawner.update(false);CompoundTag spawnerNbt=PnxBlockEntityNbtCodec.fromIntent(spawnerIntent.get());
  check("minecraft:zombie".equals(spawnerNbt.getString("EntityIdentifier"))&&spawnerNbt.getInt("EntityId")==32,"spawner entity identifier resolved through PNX registry");
  check(spawnerNbt.getShort("Delay")==35&&spawnerNbt.getShort("MinSpawnDelay")==100&&spawnerNbt.getShort("MaxSpawnDelay")==300,"spawner timing persisted");
  check(spawnerNbt.getShort("MinimumSpawnerCount")==3&&spawnerNbt.getShort("MaximumSpawnerCount")==3&&spawnerNbt.getShort("SpawnRange")==5,"spawner count and range persisted");
  boolean rejected=false;try{spawner.applyState("type=minecraft:unknown");}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"unknown PNX entity identifiers fail closed");
  AtomicReference<BlockEntityIntent> chestIntent=new AtomicReference<>();
  PNXStagedContainerDelegate chest=new PNXStagedContainerDelegate(9,50,9,"Chest",new BlockState(){},new CompoundTag(),27,null,chestIntent::set);
  chest.getInventory().setItem(4,new PNXItemStack(new Item("minecraft:diamond",2)));
  ItemStack read=chest.getInventory().getItem(4);check(read!=null&&((Item)read.getHandle()).getCount()==2,"staged inventory round-trip");
  chest.update(false);CompoundTag chestNbt=PnxBlockEntityNbtCodec.fromIntent(chestIntent.get());
  check(chestNbt.getList("Items").size()==1,"container loot encoded to PNX Items list");
  System.out.println("PnxStagedSpawnerContainerContractTest: PASS");
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
JAVA

mapfile -t SOURCES < <(find "$STUB" -name '*.java')
mkdir -p "$BUILD/classes"
javac --release 21 -Xlint:all -Werror -d "$BUILD/classes" "${SOURCES[@]}" \
  "$ROOT/source/org/powernukkitx/terra/crosschunk/journal/ChunkPos.java" \
  "$ROOT/source/org/powernukkitx/terra/crosschunk/journal/BlockEntityIntent.java" \
  "$ROOT/source/org/powernukkitx/terra/crosschunk/journal/pnx/PnxBlockEntityNbtCodec.java" \
  "$ROOT/source/org/powernukkitx/terra/crosschunk/runtime/PnxLevelExecutor.java" \
  "$ROOT/source/org/powernukkitx/terra/crosschunk/journal/pnx/PnxBlockEntityIntentApplier.java" \
  "$ROOT/source/org/powernukkitx/terra/delegate/PNXStagedBlockEntityDelegate.java" \
  "$ROOT/source/org/powernukkitx/terra/delegate/PNXStagedSignDelegate.java" \
  "$ROOT/source/org/powernukkitx/terra/delegate/PNXStagedMobSpawnerDelegate.java" \
  "$ROOT/source/org/powernukkitx/terra/delegate/PNXStagedInventory.java" \
  "$ROOT/source/org/powernukkitx/terra/delegate/PNXStagedContainerDelegate.java"
java -cp "$BUILD/classes" org.powernukkitx.terra.crosschunk.journal.pnx.PnxBlockEntityNbtContractTest
java -cp "$BUILD/classes" org.powernukkitx.terra.crosschunk.journal.pnx.PnxBlockEntityIntentApplierContractTest
java -cp "$BUILD/classes" org.powernukkitx.terra.crosschunk.journal.pnx.PnxStagedSignContractTest
java -cp "$BUILD/classes" org.powernukkitx.terra.crosschunk.journal.pnx.PnxStagedSpawnerContainerContractTest
