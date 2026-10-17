package net.neoforged.forgestuck.shim;

import net.neoforged.forgestuck.compat.LegacyItemStackShim;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.forgestuck.transformer.ForgeClassTransformer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class ItemStackShimTest {
    private static final String IS = "net/minecraft/world/item/ItemStack";
    private static final String CT = "net/minecraft/nbt/CompoundTag";

    @BeforeAll
    static void bootstrap() {
        net.neoforged.forgestuck.testsupport.TestBootstrap.init();
    }

    @Test
    void getTagIsNullWithoutCustomData() {
        assertNull(LegacyItemStackShim.getTag(new ItemStack(Items.STICK)));
    }

    @Test
    void getOrCreateTagIsLiveAndVisibleToGetTag() {
        ItemStack stack = new ItemStack(Items.STICK);
        LegacyItemStackShim.getOrCreateTag(stack).putInt("n", 5);
        assertEquals(5, LegacyItemStackShim.getTag(stack).getInt("n"));
        assertEquals(5, stack.get(DataComponents.CUSTOM_DATA).copyTag().getInt("n"));
        LegacyItemStackShim.getTag(stack).putString("s", "x");
        assertEquals("x", stack.get(DataComponents.CUSTOM_DATA).copyTag().getString("s"));
    }

    @Test
    void setTagStoresCopyAndNullRemoves() {
        ItemStack stack = new ItemStack(Items.STICK);
        CompoundTag tag = new CompoundTag();
        tag.putInt("a", 1);
        LegacyItemStackShim.setTag(stack, tag);
        tag.putInt("a", 2);
        assertEquals(1, LegacyItemStackShim.getTag(stack).getInt("a"));
        LegacyItemStackShim.setTag(stack, null);
        assertNull(LegacyItemStackShim.getTag(stack));
        assertFalse(stack.has(DataComponents.CUSTOM_DATA));
    }

    @Test
    void mutationDoesNotLeakIntoCopiedStack() {
        ItemStack stack = new ItemStack(Items.STICK);
        LegacyItemStackShim.getOrCreateTag(stack).putInt("n", 1);
        ItemStack copy = stack.copy();
        LegacyItemStackShim.getTag(copy).putInt("n", 2);
        assertEquals(1, stack.get(DataComponents.CUSTOM_DATA).copyTag().getInt("n"));
        assertEquals(2, copy.get(DataComponents.CUSTOM_DATA).copyTag().getInt("n"));
    }

    private static final class Loader extends ClassLoader {
        Loader() {
            super(ItemStackShimTest.class.getClassLoader());
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    /** Legacy bytecode calling the three removed ItemStack methods, loaded and executed after transformation. */
    @Test
    void transformedLegacyCallsLinkAndExecute() throws Exception {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/Legacy", null, "java/lang/Object", null);
        var m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "(L" + IS + ";)L" + CT + ";", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, IS, "getOrCreateTag", "()L" + CT + ";", false);
        m.visitLdcInsn("k");
        m.visitLdcInsn(7);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CT, "putInt", "(Ljava/lang/String;I)V", false);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, IS, "getTag", "()L" + CT + ";", false);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, IS, "setTag", "(L" + CT + ";)V", false);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, IS, "getTag", "()L" + CT + ";", false);
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();

        byte[] out = ForgeClassTransformer.transform("com.ex.Legacy", cw.toByteArray());
        Class<?> c = new Loader().define("com.ex.Legacy", out);
        ItemStack stack = new ItemStack(Items.STICK);
        CompoundTag result = (CompoundTag) c.getMethod("run", ItemStack.class).invoke(null, stack);
        assertEquals(7, result.getInt("k"));
    }

    /** Every shim call the transformer can emit must resolve to a real public static method. */
    @Test
    void everyEmittedShimCallExists() throws Exception {
        assertEquals(4, ForgeClassTransformer.emittedShimCalls().size());
        for (String[] call : ForgeClassTransformer.emittedShimCalls()) {
            boolean found = false;
            for (Method method : LegacyItemStackShim.class.getMethods()) {
                found |= method.getName().equals(call[0]) && Type.getMethodDescriptor(method).equals(call[1])
                        && Modifier.isStatic(method.getModifiers());
            }
            assertTrue(found, "missing shim method " + call[0] + call[1]);
        }
    }

    /** Production 1.20.1 jars reference SRG names; they must reach the shim too. */
    @Test
    void srgNamedCallsAreRedirectedLinkAndExecute() throws Exception {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/Srg", null, "java/lang/Object", null);
        var m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "(L" + IS + ";)Z", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, IS, "m_41784_", "()L" + CT + ";", false);
        m.visitLdcInsn("k");
        m.visitLdcInsn(3);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CT, "putInt", "(Ljava/lang/String;I)V", false);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, IS, "m_41783_", "()L" + CT + ";", false);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, IS, "m_41751_", "(L" + CT + ";)V", false);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, IS, "m_41782_", "()Z", false);
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();

        byte[] out = ForgeClassTransformer.transform("com.ex.Srg", cw.toByteArray());
        Class<?> c = new Loader().define("com.ex.Srg", out);
        ItemStack stack = new ItemStack(Items.STICK);
        assertTrue((Boolean) c.getMethod("run", ItemStack.class).invoke(null, stack));
        assertEquals(3, LegacyItemStackShim.getTag(stack).getInt("k"));
    }
}
