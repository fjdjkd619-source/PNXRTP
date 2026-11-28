#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="${BUILD_DIR:-$ROOT/validation/entity-spawn-contract-build}"
STUB="$BUILD/stubs"
rm -rf "$BUILD"
mkdir -p "$STUB/org/powernukkitx/entity" "$STUB/org/powernukkitx/level/format" "$STUB/org/powernukkitx/level" "$STUB/org/powernukkitx/math" "$STUB/org/powernukkitx/nbt/tag"
cat > "$STUB/org/powernukkitx/math/Vector3.java" <<'JAVA'
package org.powernukkitx.math;
public record Vector3(double x,double y,double z) { }
JAVA
cat > "$STUB/org/powernukkitx/nbt/tag/CompoundTag.java" <<'JAVA'
package org.powernukkitx.nbt.tag;
public final class CompoundTag { private String uuid; private double x,y,z; private float yaw,pitch; public CompoundTag putString(String k,String v){if(k.equals("uuid"))uuid=v;return this;} public CompoundTag pos(double x,double y,double z,float yaw,float pitch){this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.pitch=pitch;return this;} public String uuid(){return uuid;} public double x(){return x;} public double y(){return y;} public double z(){return z;} public float yaw(){return yaw;} public float pitch(){return pitch;} }
JAVA
cat > "$STUB/org/powernukkitx/level/DimensionData.java" <<'JAVA'
package org.powernukkitx.level;
public final class DimensionData { public int getMinHeight(){return -64;} public int getMaxHeight(){return 320;} }
JAVA
cat > "$STUB/org/powernukkitx/level/format/LevelProvider.java" <<'JAVA'
package org.powernukkitx.level.format;
public final class LevelProvider { public Object getLevel(){return this;} }
JAVA
cat > "$STUB/org/powernukkitx/level/format/IChunk.java" <<'JAVA'
package org.powernukkitx.level.format;
import java.util.Map;
import org.powernukkitx.entity.Entity;
import org.powernukkitx.level.DimensionData;
public interface IChunk { int getX(); int getZ(); boolean isLoaded(); boolean isGenerated(); boolean isInitiated(); LevelProvider getProvider(); DimensionData getDimensionData(); Map<Long,Entity> getEntities(); }
JAVA
cat > "$STUB/org/powernukkitx/entity/Entity.java" <<'JAVA'
package org.powernukkitx.entity;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.powernukkitx.level.format.IChunk;
import org.powernukkitx.level.format.TestChunk;
import org.powernukkitx.math.Vector3;
import org.powernukkitx.nbt.tag.CompoundTag;
public final class Entity {
 private static final AtomicLong IDS=new AtomicLong();
 public static boolean cancelNext;
 private final long id=IDS.incrementAndGet(); private final IChunk chunk; private final UUID uuid; private final String type; private final double x,y,z; private final float yaw,pitch; private boolean closed, pending=true, registered, active; public int spawned;
 private Entity(IChunk c,String t,CompoundTag n,double x,double y,double z,float yaw,float pitch){chunk=c;type=t;uuid=UUID.fromString(n.uuid());this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.pitch=pitch;}
 public static CompoundTag getDefaultNBT(Vector3 p,Vector3 motion,float yaw,float pitch){return new CompoundTag().pos(p.x(),p.y(),p.z(),yaw,pitch);}
 public static Entity createEntityForGeneration(String type,IChunk chunk,CompoundTag nbt){Entity e=new Entity(chunk,type,nbt,nbt.x(),nbt.y(),nbt.z(),nbt.yaw(),nbt.pitch());if(cancelNext){cancelNext=false;e.closed=true;e.pending=false;}return e;}
 public boolean registerForGenerationSave(){if(closed||!pending||registered)return false;registered=true;((TestChunk)chunk).add(this);return true;}
 public boolean activateAfterGenerationCommit(){if(closed||!pending||!registered)return false;pending=false;active=true;return true;}
 public UUID getUniqueId(){return uuid;} public String getIdentifier(){return type;} public double getX(){return x;} public double getY(){return y;} public double getZ(){return z;} public float getYaw(){return yaw;} public float getPitch(){return pitch;} public boolean isClosed(){return closed;} public void spawnToAll(){if(!closed&&active)spawned++;} public void closeForGenerationRollback(){if(!closed){closed=true;if(registered)((TestChunk)chunk).remove(this);}}
}
JAVA
cat > "$STUB/org/powernukkitx/level/format/TestChunk.java" <<'JAVA'
package org.powernukkitx.level.format;
import java.util.*;
import org.powernukkitx.entity.Entity;
import org.powernukkitx.level.DimensionData;
public final class TestChunk implements IChunk {private final Map<Long,Entity> entities=new LinkedHashMap<>();private final LevelProvider provider=new LevelProvider();public int getX(){return -1;}public int getZ(){return 2;}public boolean isLoaded(){return true;}public boolean isGenerated(){return true;}public boolean isInitiated(){return true;}public LevelProvider getProvider(){return provider;}public DimensionData getDimensionData(){return new DimensionData();}public Map<Long,Entity> getEntities(){return entities;}public void add(Entity e){entities.put((long)entities.size()+1,e);}public void remove(Entity e){entities.values().remove(e);}}
JAVA
cat > "$STUB/org/powernukkitx/entity/EntitySpawnContractTest.java" <<'JAVA'
package org.powernukkitx.entity;
import java.util.*;
import org.powernukkitx.level.format.TestChunk;
import org.powernukkitx.terra.crosschunk.journal.EntitySpawnIntent;
import org.powernukkitx.terra.crosschunk.journal.pnx.PnxEntitySpawnIntentApplier;
public final class EntitySpawnContractTest {
 public static void main(String[] args)throws Exception{
  TestChunk c=new TestChunk(); UUID id=UUID.randomUUID(); EntitySpawnIntent i=new EntitySpawnIntent(id,"minecraft:sheep",-1.5,64,33.5,90,0,new byte[0]);
  var batch=PnxEntitySpawnIntentApplier.apply(c,List.of(i));check(c.getEntities().isEmpty(),"entity is not registered or ticking before save staging");check(batch.created().size()==1&&batch.all().size()==1,"batch tracks created and total");PnxEntitySpawnIntentApplier.prepareForDurableSave(batch);check(c.getEntities().size()==1&&batch.created().get(0).spawned==0,"save staging registers only in chunk, not ticking Level or clients");PnxEntitySpawnIntentApplier.confirm(batch);check(batch.created().get(0).spawned==1,"confirm activates and publishes entity after durable save");
  var replay=PnxEntitySpawnIntentApplier.apply(c,List.of(i));check(c.getEntities().size()==1&&replay.created().isEmpty()&&replay.all().size()==1,"replay is UUID-idempotent");
  PnxEntitySpawnIntentApplier.rollback(replay);check(c.getEntities().size()==1,"rollback never removes pre-existing entity");
  UUID rollbackId=UUID.randomUUID();var pending=PnxEntitySpawnIntentApplier.apply(c,List.of(new EntitySpawnIntent(rollbackId,"minecraft:cow",-1.5,64,33.5,0,0,new byte[0])));PnxEntitySpawnIntentApplier.prepareForDurableSave(pending);PnxEntitySpawnIntentApplier.rollback(pending);check(c.getEntities().size()==1,"rollback removes only entities created by this batch after activation");
  boolean rejected=false;try{PnxEntitySpawnIntentApplier.apply(c,List.of(new EntitySpawnIntent(UUID.randomUUID(),"minecraft:sheep",-1.5,64,33.5,0,0,new byte[]{1})));}catch(java.io.IOException expected){rejected=true;}check(rejected&&c.getEntities().size()==1,"opaque payload fails closed");
  rejected=false;try{PnxEntitySpawnIntentApplier.apply(c,List.of(new EntitySpawnIntent(UUID.randomUUID(),"minecraft:sheep",0.5,64,33.5,0,0,new byte[0])));}catch(java.io.IOException expected){rejected=true;}check(rejected&&c.getEntities().size()==1,"cross-chunk intent rejected");
  rejected=false;try{PnxEntitySpawnIntentApplier.apply(c,List.of(new EntitySpawnIntent(id,"minecraft:cow",-1.5,64,33.5,90,0,new byte[0])));}catch(java.io.IOException expected){rejected=true;}check(rejected&&c.getEntities().size()==1,"UUID collision with a different entity type rejected");
  Entity.cancelNext=true;rejected=false;try{PnxEntitySpawnIntentApplier.apply(c,List.of(new EntitySpawnIntent(UUID.randomUUID(),"minecraft:horse",-1.5,64,33.5,0,0,new byte[0])));}catch(java.io.IOException expected){rejected=true;}check(rejected&&c.getEntities().size()==1,"cancelled entity spawn fails closed without leaking a registry entry");
  UUID rollbackOnBatchFailure=UUID.randomUUID();rejected=false;try{PnxEntitySpawnIntentApplier.apply(c,List.of(new EntitySpawnIntent(rollbackOnBatchFailure,"minecraft:pig",-1.5,64,33.5,0,0,new byte[0]),new EntitySpawnIntent(UUID.randomUUID(),"minecraft:pig",-1.5,64,33.5,0,0,new byte[]{9})));}catch(java.io.IOException expected){rejected=true;}check(rejected&&c.getEntities().size()==1,"later invalid intent rolls back earlier creations in the same batch");
  System.out.println("PnxEntitySpawnIntentApplierContractTest: PASS");
 }
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
JAVA
mkdir -p "$BUILD/classes"
javac --release 21 -Xlint:all -Werror -d "$BUILD/classes" \
  $(find "$STUB" -name '*.java') \
  "$ROOT/source/org/powernukkitx/terra/crosschunk/journal/ChunkPos.java" \
  "$ROOT/source/org/powernukkitx/terra/crosschunk/journal/EntitySpawnIntent.java" \
  "$ROOT/source/org/powernukkitx/terra/crosschunk/journal/pnx/PnxEntitySpawnIntentApplier.java"
java -cp "$BUILD/classes" org.powernukkitx.entity.EntitySpawnContractTest
