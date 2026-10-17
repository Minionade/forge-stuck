package net.neoforged.forgestuck.shim;

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
        List<String> emitted = new ArrayList<>();
        for (String name : List.of("getTag", "getOrCreateTag", "setTag")) {
            String desc = name.equals("setTag") ? "(L" + CT + ";)V" : "()L" + CT + ";";
            ClassWriter cw = new ClassWriter(0);
            cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/G" + name, null, "java/lang/Object", null);
            var mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "m", "(L" + IS + ";L" + CT + ";)V", null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, IS, name, desc, false);
            if (!name.equals("setTag")) mv.visitInsn(Opcodes.POP);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(3, 3);
            mv.visitEnd();
            cw.visitEnd();
            byte[] out = ForgeClassTransformer.transform("com.ex.G" + name, cw.toByteArray());
            ClassNode node = new ClassNode();
            new ClassReader(out).accept(node, 0);
            for (MethodNode mn : node.methods) {
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn instanceof MethodInsnNode call && call.owner.startsWith("net/neoforged/forgestuck/shim/")) {
                        emitted.add(call.owner + "." + call.name + call.desc);
                    }
                }
            }
        }
        assertEquals(3, emitted.size(), emitted.toString());
        for (String e : emitted) {
            int dot = e.indexOf('.');
            int paren = e.indexOf('(');
            Class<?> owner = Class.forName(e.substring(0, dot).replace('/', '.'));
            boolean found = false;
            for (Method method : owner.getMethods()) {
                found |= method.getName().equals(e.substring(dot + 1, paren))
                        && Type.getMethodDescriptor(method).equals(e.substring(paren))
                        && Modifier.isStatic(method.getModifiers());
            }
            assertTrue(found, "missing shim method " + e);
        }
    }
}
