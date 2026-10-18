package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import net.neoforged.neoforge.items.SlotItemHandler;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class SlotItemHandlerAndAddLayersTest {
    private static final String SLOT = "net/minecraftforge/items/SlotItemHandler";
    private static final String HANDLER = "net/minecraftforge/items/IItemHandler";
    private static final String LAYERS = "net/minecraftforge/client/event/EntityRenderersEvent$AddLayers";

    @Test
    void legacySlotSubclassExtendsNeoForgeSlot() throws Exception {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/MySlot", null, SLOT, null);
        var c = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(L" + HANDLER + ";III)V", null, null);
        c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD, 0);
        c.visitVarInsn(Opcodes.ALOAD, 1);
        c.visitVarInsn(Opcodes.ILOAD, 2);
        c.visitVarInsn(Opcodes.ILOAD, 3);
        c.visitVarInsn(Opcodes.ILOAD, 4);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL, SLOT, "<init>", "(L" + HANDLER + ";III)V", false);
        c.visitInsn(Opcodes.RETURN);
        c.visitMaxs(0, 0);
        c.visitEnd();
        var g = w.visitMethod(Opcodes.ACC_PUBLIC, "handler", "()L" + HANDLER + ";", null, null);
        g.visitCode();
        g.visitVarInsn(Opcodes.ALOAD, 0);
        g.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "com/ex/MySlot", "getItemHandler", "()L" + HANDLER + ";", false);
        g.visitInsn(Opcodes.ARETURN);
        g.visitMaxs(0, 0);
        g.visitEnd();
        w.visitEnd();
        byte[] out = new ForgeOwnerRemapStep().apply("com.ex.MySlot", w.toByteArray());
        Class<?> type = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() {
                return defineClass("com.ex.MySlot", out, 0, out.length);
            }
        }.define();
        assertTrue(SlotItemHandler.class.isAssignableFrom(type));
        assertNotNull(type.getMethod("handler"));
    }

    @Test
    void addLayersHandlerLinksToNeoForgeEvent() throws Exception {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/Layers", null, "java/lang/Object", null);
        var m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "on", "(L" + LAYERS + ";)Ljava/util/Set;", null,
                null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, LAYERS, "getEntityTypes", "()Ljava/util/Set;", false);
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        byte[] out = new ForgeOwnerRemapStep().apply("com.ex.Layers", w.toByteArray());
        Class<?> type = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() {
                return defineClass("com.ex.Layers", out, 0, out.length);
            }
        }.define();
        assertEquals(net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers.class,
                type.getMethods()[0].getParameterTypes()[0]);
    }
}
