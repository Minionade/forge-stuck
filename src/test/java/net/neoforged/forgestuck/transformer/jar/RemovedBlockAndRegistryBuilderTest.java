package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.level.block.ColoredFallingBlock;
import net.neoforged.forgestuck.compat.LegacyStemGrownBlock;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class RemovedBlockAndRegistryBuilderTest {
    private static final String PROPS = "Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;";

    private static Class<?> define(String name, byte[] input) {
        byte[] out = new SrgMemberRemapStep().apply(name, new ForgeOwnerRemapStep().apply(name, input));
        return new ClassLoader(RemovedBlockAndRegistryBuilderTest.class.getClassLoader()) {
            Class<?> define() {
                return defineClass(name, out, 0, out.length);
            }
        }.define();
    }

    private static ClassWriter subclass(String name, String sup, String ctorDesc, boolean intArg) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name.replace('.', '/'), null, sup, null);
        var c = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", ctorDesc, null, null);
        c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD, 0);
        if (intArg) {
            c.visitVarInsn(Opcodes.ILOAD, 1);
            c.visitVarInsn(Opcodes.ALOAD, 2);
        } else {
            c.visitVarInsn(Opcodes.ALOAD, 1);
        }
        c.visitMethodInsn(Opcodes.INVOKESPECIAL, sup, "<init>", ctorDesc, false);
        c.visitInsn(Opcodes.RETURN);
        c.visitMaxs(0, 0);
        c.visitEnd();
        return w;
    }

    @Test
    void sandBlockSubclassExtendsColoredFallingBlock() {
        ClassWriter w = subclass("com.ex.MySand", "net/minecraft/world/level/block/SandBlock", "(I" + PROPS + ")V",
                true);
        w.visitEnd();
        assertTrue(ColoredFallingBlock.class.isAssignableFrom(define("com.ex.MySand", w.toByteArray())));
    }

    @Test
    void stemGrownBlockSubclassOverridesSrgAbstractMethods() throws Exception {
        ClassWriter w = subclass("com.ex.MyGourd", "net/minecraft/world/level/block/StemGrownBlock", "(" + PROPS + ")V",
                false);
        var getter = w.visitMethod(Opcodes.ACC_PUBLIC, "m_7161_", "()Lnet/minecraft/world/level/block/StemBlock;", null,
                null);
        getter.visitCode();
        getter.visitInsn(Opcodes.ACONST_NULL);
        getter.visitInsn(Opcodes.ARETURN);
        getter.visitMaxs(0, 0);
        getter.visitEnd();
        w.visitEnd();
        // Linking and override naming only; the abstract codec() is left unimplemented.
        Class<?> type = define("com.ex.MyGourd", w.toByteArray());
        assertTrue(LegacyStemGrownBlock.class.isAssignableFrom(type));
        assertNotNull(type.getDeclaredMethod("getStem"));
    }

    @Test
    void registryBuilderCreateFailsExplicitlyAndFluentCallsLink() throws Exception {
        String rb = "net/minecraftforge/registries/RegistryBuilder";
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/Reg", null, "java/lang/Object", null);
        var m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "build", "()Ljava/lang/Object;", null, null);
        m.visitCode();
        m.visitTypeInsn(Opcodes.NEW, rb);
        m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, rb, "<init>", "()V", false);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, rb, "disableSaving", "()L" + rb + ";", false);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, rb, "create", "()Lnet/minecraftforge/registries/IForgeRegistry;",
                false);
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        Class<?> type = define("com.ex.Reg", w.toByteArray());
        var ex = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> type.getMethod("build").invoke(null));
        assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
    }

    @Test
    void lootingLevelEventLinks() throws Exception {
        String ev = "net/minecraftforge/event/entity/living/LootingLevelEvent";
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/Loot", null, "java/lang/Object", null);
        var m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "on", "(L" + ev + ";)V", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ev, "getLootingLevel", "()I", false);
        m.visitInsn(Opcodes.ICONST_1);
        m.visitInsn(Opcodes.IADD);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ev, "setLootingLevel", "(I)V", false);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        Class<?> type = define("com.ex.Loot", w.toByteArray());
        var event = new net.neoforged.forgestuck.compat.LegacyLootingLevelEvent(null, null, 2);
        type.getMethod("on", event.getClass()).invoke(null, event);
        assertEquals(3, event.getLootingLevel());
    }
}
