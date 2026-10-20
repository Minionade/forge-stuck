package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class GameFieldRepairStepTest {
    private static byte[] fixture(Consumer<org.objectweb.asm.MethodVisitor> body, String retDesc) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/F", null, "java/lang/Object", null);
        var m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "go", "()" + retDesc, null, null);
        m.visitCode();
        body.accept(m);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static Class<?> define(byte[] b, boolean initialize) throws Exception {
        var loader = new ClassLoader(GameFieldRepairStepTest.class.getClassLoader()) {
            Class<?> def() {
                return defineClass("com.ex.F", b, 0, b.length);
            }
        };
        Class<?> c = loader.def();
        if (initialize) Class.forName("com.ex.F", true, c.getClassLoader());
        return c;
    }

    private static ClassNode read(byte[] b) {
        ClassNode n = new ClassNode();
        new ClassReader(b).accept(n, 0);
        return n;
    }

    @Test
    void holderFieldsAreUnwrappedAndTheClassVerifies() throws Exception {
        byte[] in = fixture(m -> {
            m.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/effect/MobEffects", "WITHER",
                    "Lnet/minecraft/world/effect/MobEffect;");
            m.visitInsn(Opcodes.ARETURN);
        }, "Lnet/minecraft/world/effect/MobEffect;");
        byte[] out = new GameFieldRepairStep().apply("com.ex.F", in);
        var insns = read(out).methods.get(0).instructions;
        boolean sawValue = false, sawDesc = false;
        for (var i : insns) {
            if (i instanceof MethodInsnNode c && c.owner.equals("net/minecraft/core/Holder") && c.name.equals("value")) sawValue = true;
            if (i instanceof org.objectweb.asm.tree.FieldInsnNode f && f.desc.equals("Lnet/minecraft/core/Holder;")) sawDesc = true;
        }
        assertTrue(sawValue && sawDesc);
        define(out, true);
        assertSame(out, new GameFieldRepairStep().apply("com.ex.F", out), "idempotent");
    }

    @Test
    void renamedEnumConstantsResolveAndExecute() throws Exception {
        byte[] in = fixture(m -> {
            m.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/entity/ai/attributes/AttributeModifier$Operation",
                    "MULTIPLY_TOTAL", "Lnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;");
            m.visitInsn(Opcodes.ARETURN);
        }, "Ljava/lang/Object;");
        Class<?> c = define(new GameFieldRepairStep().apply("com.ex.F", in), true);
        assertEquals(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, c.getMethod("go").invoke(null));
    }

    @Test
    void fishingLootTableKeyIsAdaptedToLegacyResourceLocation() throws Exception {
        byte[] in = fixture(m -> {
            m.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/level/storage/loot/BuiltInLootTables",
                    "FISHING_FISH", "Lnet/minecraft/resources/ResourceLocation;");
            m.visitInsn(Opcodes.ARETURN);
        }, "Lnet/minecraft/resources/ResourceLocation;");
        byte[] out = new GameFieldRepairStep().apply("com.ex.F", in);
        ClassNode transformed = read(out);
        var instructions = transformed.methods.get(0).instructions.toArray();
        assertTrue(java.util.Arrays.stream(instructions)
                .filter(org.objectweb.asm.tree.FieldInsnNode.class::isInstance)
                .map(org.objectweb.asm.tree.FieldInsnNode.class::cast)
                .anyMatch(field -> field.name.equals("FISHING_FISH")
                        && field.desc.equals("Lnet/minecraft/resources/ResourceKey;")));
        assertTrue(java.util.Arrays.stream(instructions)
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.owner.equals("net/minecraft/resources/ResourceKey")
                        && call.name.equals("location")));

        Class<?> c = define(out, true);
        assertEquals(net.minecraft.world.level.storage.loot.BuiltInLootTables.FISHING_FISH.location(),
                c.getMethod("go").invoke(null));
    }

    @Test
    void defaultedRegistryCallsAreReOwnedExceptGetDefaultKey() {
        byte[] in = fixture(m -> {
            m.visitInsn(Opcodes.ACONST_NULL);
            m.visitTypeInsn(Opcodes.CHECKCAST, "net/minecraft/core/DefaultedRegistry");
            m.visitInsn(Opcodes.ICONST_0);
            m.visitMethodInsn(Opcodes.INVOKEINTERFACE, "net/minecraft/core/DefaultedRegistry", "byId", "(I)Ljava/lang/Object;", true);
            m.visitInsn(Opcodes.POP);
            m.visitInsn(Opcodes.ACONST_NULL);
            m.visitTypeInsn(Opcodes.CHECKCAST, "net/minecraft/core/DefaultedRegistry");
            m.visitMethodInsn(Opcodes.INVOKEINTERFACE, "net/minecraft/core/DefaultedRegistry", "getDefaultKey",
                    "()Lnet/minecraft/resources/ResourceLocation;", true);
            m.visitInsn(Opcodes.ARETURN);
        }, "Ljava/lang/Object;");
        var owners = new java.util.ArrayList<String>();
        for (var i : read(new GameFieldRepairStep().apply("com.ex.F", in)).methods.get(0).instructions) {
            if (i instanceof MethodInsnNode c) owners.add(c.owner + "." + c.name);
        }
        assertEquals(java.util.List.of("net/minecraft/core/Registry.byId", "net/minecraft/core/DefaultedRegistry.getDefaultKey"), owners);
    }

    @Test
    void enchantmentKeysFailWhenExecutedNotAtLink() throws Exception {
        byte[] in = fixture(m -> {
            m.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/item/enchantment/Enchantments", "SHARPNESS",
                    "Lnet/minecraft/world/item/enchantment/Enchantment;");
            m.visitInsn(Opcodes.ARETURN);
        }, "Ljava/lang/Object;");
        Class<?> c = define(new GameFieldRepairStep().apply("com.ex.F", in), true);
        var e = assertThrows(InvocationTargetException.class, () -> c.getMethod("go").invoke(null));
        assertTrue(e.getCause() instanceof UnsupportedOperationException, String.valueOf(e.getCause()));
    }

    @Test
    void everyHolderTableEntryMatchesTheRealField() throws Exception {
        int n = 0;
        try (var r = new BufferedReader(new InputStreamReader(
                GameFieldRepairStep.class.getResourceAsStream("/forgestuck/game-field-holders.txt"), StandardCharsets.UTF_8))) {
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String lhs = line.substring(0, line.lastIndexOf('='));
                String target = line.substring(line.lastIndexOf('=') + 1);
                String ownerAndName = lhs.substring(0, lhs.indexOf('|'));
                String owner = ownerAndName.substring(0, ownerAndName.lastIndexOf('.'));
                String name = ownerAndName.substring(ownerAndName.lastIndexOf('.') + 1);
                Field f = Class.forName(owner.replace('/', '.'), false, getClass().getClassLoader()).getField(name);
                assertEquals(target, Type.getDescriptor(f.getType()), ownerAndName);
                n++;
            }
        }
        assertTrue(n > 300, "table unexpectedly small: " + n);
    }

    @Test
    void plantTypeLinksAndKeepsInterningAndTheLegacyConstants() throws Exception {
        byte[] in = fixture(m -> {
            m.visitLdcInsn("fungus");
            m.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraftforge/common/PlantType", "get",
                    "(Ljava/lang/String;)Lnet/minecraftforge/common/PlantType;", false);
            m.visitInsn(Opcodes.ARETURN);
        }, "Ljava/lang/Object;");
        byte[] out = new ForgeOwnerRemapStep().apply("com.ex.F", in);
        Class<?> c = define(out, true);
        Object a = c.getMethod("go").invoke(null);
        assertNotNull(a);
        assertSame(a, c.getMethod("go").invoke(null));
        assertEquals("fungus", ((net.neoforged.forgestuck.compat.PlantType) a).getName());
        assertEquals("crop", net.neoforged.forgestuck.compat.PlantType.CROP.getName());
    }
}
