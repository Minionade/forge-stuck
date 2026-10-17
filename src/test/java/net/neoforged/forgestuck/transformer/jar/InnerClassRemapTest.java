package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

class InnerClassRemapTest {
    private static final String OLD_OUTER = "net/minecraftforge/client/event/TextureStitchEvent";
    private static final String OLD_INNER = OLD_OUTER + "$Post";
    private static final String NEW = "net/neoforged/neoforge/client/event/TextureAtlasStitchedEvent";

    /** A Scala-style class whose InnerClasses attribute lists a legacy nested class that maps to a top-level one. */
    private static byte[] fixture() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/Icons", null, "java/lang/Object", null);
        cw.visitInnerClass(OLD_INNER, OLD_OUTER, "Post", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        var m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "on", "(L" + OLD_INNER + ";)V", null, null);
        m.visitCode();
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    void nestedToTopLevelMappingDoesNotProduceSelfNestingInnerClasses() throws Exception {
        byte[] out = new ForgeOwnerRemapStep().apply("com.ex.Icons", fixture());
        ClassNode n = new ClassNode();
        new ClassReader(out).accept(n, 0);
        assertTrue(n.methods.stream().anyMatch(m -> m.desc.equals("(L" + NEW + ";)V")), "descriptor was remapped");
        assertTrue(n.innerClasses.stream().noneMatch(ic -> NEW.equals(ic.name) || NEW.equals(ic.outerName)),
                "no entry may name the mapped class as inner or outer");

        var loader = new ClassLoader(InnerClassRemapTest.class.getClassLoader()) {
            Class<?> def(byte[] b) {
                return defineClass("com.ex.Icons", b, 0, b.length);
            }
        };
        Class<?> c = assertDoesNotThrow(() -> loader.def(out));
        assertEquals("com.ex.Icons", c.getName());
        assertDoesNotThrow(() -> c.getDeclaredMethods());
    }

    @Test
    void unchangedNestedEntriesAreKept() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/Outer", null, "java/lang/Object", null);
        cw.visitInnerClass("com/ex/Outer$In", "com/ex/Outer", "In", Opcodes.ACC_STATIC);
        cw.visitEnd();
        ClassNode n = new ClassNode();
        new ClassReader(cw.toByteArray()).accept(n, 0);
        ForgeOwnerRemapStep.dropBrokenInnerClasses(n, new org.objectweb.asm.commons.Remapper() {});
        assertEquals(1, n.innerClasses.size());
    }
}
