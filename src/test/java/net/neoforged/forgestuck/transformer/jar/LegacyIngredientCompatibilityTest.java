package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class LegacyIngredientCompatibilityTest {
    private static final String INGREDIENT = "net/minecraft/world/item/crafting/Ingredient";
    private static final String LEGACY_SERIALIZER = "net/minecraftforge/common/crafting/IIngredientSerializer";
    private static final String COMPAT_SERIALIZER = "net/neoforged/forgestuck/compat/LegacyIngredientSerializer";

    private static ClassNode parse(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    @Test
    void mapsLegacyIngredientSuperclassAndSerializer() {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        assertNotNull(mappings.get("net/minecraftforge/common/crafting/AbstractIngredient"));
        assertNotNull(mappings.get(LEGACY_SERIALIZER));
    }

    @Test
    void reparentsLegacyIngredientSubclassToCustomIngredientAdapter() {
        String oldBase = "net/minecraftforge/common/crafting/AbstractIngredient";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/CustomIngredient", null, oldBase, null);
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, oldBase, "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        writer.visitEnd();

        ClassNode transformed = parse(new ForgeOwnerRemapStep()
                .apply("example.CustomIngredient", writer.toByteArray()));
        assertEquals("net/neoforged/forgestuck/compat/LegacyAbstractIngredient", transformed.superName);
        MethodInsnNode superCall = Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(transformed.superName, superCall.owner);
    }

    @Test
    void wrapsCustomIngredientsReturnedFromLegacySerializerMethods() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/Serializer", null, "java/lang/Object",
                new String[] {LEGACY_SERIALIZER});
        var parse = writer.visitMethod(Opcodes.ACC_PUBLIC, "parse",
                "(Lcom/google/gson/JsonObject;)L" + INGREDIENT + ";", null, null);
        parse.visitCode();
        parse.visitTypeInsn(Opcodes.NEW, "example/CustomIngredient");
        parse.visitInsn(Opcodes.DUP);
        parse.visitMethodInsn(Opcodes.INVOKESPECIAL, "example/CustomIngredient", "<init>", "()V", false);
        parse.visitInsn(Opcodes.ARETURN);
        parse.visitMaxs(0, 0);
        parse.visitEnd();
        writer.visitEnd();

        byte[] ownerRemapped = new ForgeOwnerRemapStep().apply("example.Serializer", writer.toByteArray());
        ClassNode transformed = parse(new LegacyIngredientRewriteStep()
                .apply("example.Serializer", ownerRemapped));
        assertTrue(transformed.interfaces.contains(COMPAT_SERIALIZER));
        MethodInsnNode bridge = Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(call -> call.owner.equals("net/neoforged/forgestuck/compat/LegacyIngredientBridge"))
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, bridge.getOpcode());
        assertEquals("asVanilla", bridge.name);
        assertEquals("(Ljava/lang/Object;)L" + INGREDIENT + ";", bridge.desc);
    }

    @Test
    void redirectsLegacyIngredientArrayAccessToWrapperAwareBridge() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/IngredientCaller", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "items",
                "(L" + INGREDIENT + ";)[Lnet/minecraft/world/item/ItemStack;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, INGREDIENT, "m_43908_",
                "()[Lnet/minecraft/world/item/ItemStack;", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = parse(new SrgMemberRemapStep().apply("example.IngredientCaller", writer.toByteArray()));
        MethodInsnNode bridge = Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, bridge.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyIngredientBridge", bridge.owner);
        assertEquals("getItems", bridge.name);
        assertTrue(bridge.desc.endsWith(")[Lnet/minecraft/world/item/ItemStack;"));
    }
}
