package org.powernukkitx.terra.crosschunk.journal.pnx;

import org.powernukkitx.block.BlockProperties;
import org.powernukkitx.block.BlockState;
import org.powernukkitx.block.property.type.BlockPropertyType;
import org.powernukkitx.registry.Registries;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Canonical PNX 3.0.6 block-state codec. Stores namespaced identifier and named property values,
 * never the process-local/runtime network ID or blockStateHash as the durable payload.
 */
public final class PnxBlockStateCodec {
    private static final int VERSION = 1;
    private PnxBlockStateCodec() {}

    public static byte[] encode(BlockState state) throws IOException {
        Objects.requireNonNull(state,"state");
        if (state.getIdentifier().equals("minecraft:unknown"))
            throw new IOException("Unknown block states cannot be journaled safely");
        List<PropertyValue> values=new ArrayList<>();
        for (BlockPropertyType.BlockPropertyValue<?, ?, ?> value : state.getBlockPropertyValues()) {
            BlockPropertyType<?> type=value.getPropertyType();
            values.add(new PropertyValue(type.getName(),type.getType(),String.valueOf(value.getValue())));
        }
        values.sort(Comparator.comparing(PropertyValue::name));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)){
            out.writeByte(VERSION);writeString(out,state.getIdentifier());out.writeShort(values.size());
            for(PropertyValue value:values){writeString(out,value.name());out.writeByte(value.type().ordinal());writeString(out,value.value());}
        }
        return bytes.toByteArray();
    }

    public static BlockState decode(byte[] payload) throws IOException {
        Objects.requireNonNull(payload,"payload");
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(payload))){
            int version=in.readUnsignedByte();if(version!=VERSION)throw new IOException("Unsupported PNX block-state payload version: "+version);
            String identifier=readString(in);int count=in.readUnsignedShort();
            if(count>256)throw new IOException("Excessive block-state property count");
            BlockProperties properties=Registries.BLOCK.getBlockProperties(identifier);
            if(properties==null)throw new IOException("Block properties are not registered: "+identifier);
            Map<String,PropertyValue> encoded=new HashMap<>();
            for(int i=0;i<count;i++){
                String name=readString(in);int typeOrdinal=in.readUnsignedByte();String value=readString(in);
                BlockPropertyType.Type[] types=BlockPropertyType.Type.values();
                if(typeOrdinal>=types.length)throw new IOException("Invalid property type");
                if(encoded.put(name,new PropertyValue(name,types[typeOrdinal],value))!=null)
                    throw new IOException("Duplicate property: "+name);
            }
            if(in.available()!=0)throw new IOException("Trailing bytes in PNX block-state payload");
            if(encoded.size()!=properties.getPropertyTypeSet().size())
                throw new IOException("Property schema mismatch for "+identifier);
            List<BlockPropertyType.BlockPropertyValue<?, ?, ?>> decoded=new ArrayList<>();
            for(BlockPropertyType<?> type:properties.getPropertyTypeSet()){
                PropertyValue value=encoded.remove(type.getName());
                if(value==null||value.type()!=type.getType())throw new IOException("Missing or mismatched property "+type.getName()+" for "+identifier);
                decoded.add(createValue(type,value.value()));
            }
            if(!encoded.isEmpty())throw new IOException("Unexpected properties for "+identifier+": "+encoded.keySet());
            return properties.getBlockState(decoded.toArray(BlockPropertyType.BlockPropertyValue[]::new));
        } catch(IllegalArgumentException|ClassCastException malformed){
            throw new IOException("Invalid canonical PNX block state",malformed);
        }
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static BlockPropertyType.BlockPropertyValue<?, ?, ?> createValue(BlockPropertyType type,String value)throws IOException{
        Object parsed=switch(type.getType()){
            case BOOLEAN -> {if(!value.equals("true")&&!value.equals("false"))throw new IOException("Invalid boolean property value");yield Boolean.valueOf(value);}
            case INT -> {try{yield Integer.valueOf(value);}catch(NumberFormatException e){throw new IOException("Invalid integer property value",e);}}
            case ENUM -> value;
        };
        try{return type.tryCreateValue(parsed);}catch(RuntimeException invalid){throw new IOException("Property value is not registered: "+type.getName()+"="+value,invalid);}
    }
    private static void writeString(DataOutput out,String value)throws IOException{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);if(bytes.length>4096)throw new IOException("Property string too long");out.writeShort(bytes.length);out.write(bytes);}
    private static String readString(DataInput in)throws IOException{int length=in.readUnsignedShort();if(length>4096)throw new IOException("Property string too long");byte[] bytes=new byte[length];in.readFully(bytes);return new String(bytes,StandardCharsets.UTF_8);}
    private record PropertyValue(String name,BlockPropertyType.Type type,String value){}
}
