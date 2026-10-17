package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/** Legacy {@code new LootXxxType(Serializer)} call sites become MapCodec-backed types that round-trip JSON. */
class LegacyLootSerializerTest {
    private static final String SER = LegacyLootTypeRewriteStep.SERIALIZER;
    private static final String FN = "net/minecraft/world/level/storage/loot/functions/LootItemFunctionType";
    private static final String COND = "net/minecraft/world/level/storage/loot/predicates/LootItemConditionType";
    private static final String JSON_OBJ = "Lcom/google/gson/JsonObject;";
    private static final String SER_CTX = "Lcom/google/gson/JsonSerializationContext;";
    private static final String DESER_CTX = "Lcom/google/gson/JsonDeserializationContext;";

    private static final class Loader extends ClassLoader {
        Loader() {
            super(LootItemFunctionType.class.getClassLoader());
        }

        Class<?> define(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

    private static byte[] serializer() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "zz/loot/Ser", null, "java/lang/Object", new String[] {SER});
        var init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        var ser = cw.visitMethod(Opcodes.ACC_PUBLIC, "serialize", "(" + JSON_OBJ + "Ljava/lang/Object;" + SER_CTX + ")V", null, null);
        ser.visitCode();
        ser.visitVarInsn(Opcodes.ALOAD, 1);
        ser.visitLdcInsn("k");
        ser.visitVarInsn(Opcodes.ALOAD, 2);
        ser.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);
        ser.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "com/google/gson/JsonObject", "addProperty", "(Ljava/lang/String;Ljava/lang/String;)V", false);
        ser.visitInsn(Opcodes.RETURN);
        ser.visitMaxs(0, 0);
        ser.visitEnd();
        var de = cw.visitMethod(Opcodes.ACC_PUBLIC, "deserialize", "(" + JSON_OBJ + DESER_CTX + ")Ljava/lang/Object;", null, null);
        de.visitCode();
        de.visitVarInsn(Opcodes.ALOAD, 1);
        de.visitLdcInsn("k");
        de.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "com/google/gson/JsonObject", "get", "(Ljava/lang/String;)Lcom/google/gson/JsonElement;", false);
        de.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "com/google/gson/JsonElement", "getAsString", "()Ljava/lang/String;", false);
        de.visitInsn(Opcodes.ARETURN);
        de.visitMaxs(0, 0);
        de.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] registry() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "zz/loot/Reg", null, "java/lang/Object", null);
        for (String[] type : new String[][] {{"fn", FN}, {"cond", COND}}) {
            var m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, type[0], "()L" + type[1] + ";", null, null);
            m.visitCode();
            m.visitTypeInsn(Opcodes.NEW, type[1]);
            m.visitInsn(Opcodes.DUP);
            m.visitTypeInsn(Opcodes.NEW, "zz/loot/Ser");
            m.visitInsn(Opcodes.DUP);
            m.visitMethodInsn(Opcodes.INVOKESPECIAL, "zz/loot/Ser", "<init>", "()V", false);
            m.visitMethodInsn(Opcodes.INVOKESPECIAL, type[1], "<init>", "(L" + SER + ";)V", false);
            m.visitInsn(Opcodes.ARETURN);
            m.visitMaxs(0, 0);
            m.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] transform(String name, byte[] in) {
        byte[] rewritten = new LegacyLootTypeRewriteStep().apply(name, in);
        return new ForgeOwnerRemapStep().apply(name, rewritten, new OwnerRemapReport());
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        for (var m : target.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                m.setAccessible(true);
                return m.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(name);
    }

    @Test
    void rewrittenCallSitesLinkAndCodecsRoundTripJson() throws Exception {
        byte[] ser = transform("zz.loot.Ser", serializer());
        byte[] reg = transform("zz.loot.Reg", registry());
        assertEquals(java.util.Set.of(), ForgeOwnerMappings.defaults().remainingOwners(reg));
        assertEquals(java.util.Set.of(), ForgeOwnerMappings.defaults().remainingOwners(ser));
        Loader loader = new Loader();
        loader.define("zz.loot.Ser", ser);
        Class<?> regClass = loader.define("zz.loot.Reg", reg);
        ClassLoader game = LootItemFunctionType.class.getClassLoader();
        Object ops = Class.forName("com.mojang.serialization.JsonOps", true, game).getField("INSTANCE").get(null);
        Object parser = Class.forName("com.google.gson.JsonParser", true, game).getMethod("parseString", String.class).invoke(null, "{\"k\":\"hello\"}");

        for (String factory : new String[] {"fn", "cond"}) {
            Object type = regClass.getMethod(factory).invoke(null);
            Object mapCodec = call(type, "codec");
            Object decoded = call(call(call(mapCodec, "decoder"), "parse", ops, parser), "getOrThrow");
            assertEquals("hello", decoded);
            Object encoded = call(call(call(mapCodec, "codec"), "encodeStart", ops, decoded), "getOrThrow");
            assertEquals(parser, encoded);
        }
    }
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void serializerFailuresBecomeDataResultErrors() throws Exception {
        var codec = net.neoforged.forgestuck.compat.LegacyLootBridge.codec(
                new net.neoforged.forgestuck.compat.LegacyLootSerializer<String>() {
                    @Override
                    public void serialize(JsonObject json, String value, com.google.gson.JsonSerializationContext c) {
                        throw new IllegalStateException("boom");
                    }

                    @Override
                    public String deserialize(JsonObject json, com.google.gson.JsonDeserializationContext c) {
                        return c.deserialize(json, net.minecraft.world.level.storage.loot.LootContext.class);
                    }
                });
        var decode = codec.decoder().parse(JsonOps.INSTANCE, new JsonObject());
        assertTrue(decode.error().isPresent());
        assertTrue(decode.error().get().message().contains("nested"), decode.error().get().message());
        var encode = codec.codec().encodeStart(JsonOps.INSTANCE, "x");
        assertTrue(encode.error().isPresent());
        assertTrue(encode.error().get().message().contains("boom"));
        assertNotNull(new Dynamic<>(JsonOps.INSTANCE));
    }
}
