package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/**
 * One class using a supported and an unsupported member of the same mapped owner must link and verify; only the
 * unsupported path throws, with a precise message, when executed.
 */
class UnsupportedMemberLinkingTest {
    private static final String B = "net/minecraftforge/common/ForgeConfigSpec$Builder";

    private static final class Loader extends ClassLoader {
        Loader() {
            super(UnsupportedMemberLinkingTest.class.getClassLoader());
        }

        Class<?> define(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

    private static byte[] probe() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "zz/link/Probe", null, "java/lang/Object", null);
        var m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "ok", "()Ljava/lang/Object;", null, null);
        m.visitCode();
        m.visitTypeInsn(Opcodes.NEW, B);
        m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, B, "<init>", "()V", false);
        m.visitLdcInsn("x");
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, B, "comment", "(Ljava/lang/String;)L" + B + ";", false);
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();

        // unsupported instance method with wide arguments and a wide return value
        m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "bad", "()J", null, null);
        m.visitCode();
        m.visitTypeInsn(Opcodes.NEW, B);
        m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, B, "<init>", "()V", false);
        m.visitLdcInsn(5L);
        m.visitLdcInsn(2.0D);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, B, "bogus", "(JD)J", false);
        m.visitInsn(Opcodes.LRETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();

        m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "badField", "()Ljava/lang/Object;", null, null);
        m.visitCode();
        m.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraftforge/registries/ForgeRegistries", "BIOMES",
                "Lnet/minecraftforge/registries/IForgeRegistry;");
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    void supportedAndUnsupportedMembersOfOneOwnerLinkAndOnlyTheUnsupportedPathFails() throws Exception {
        OwnerRemapReport report = new OwnerRemapReport();
        byte[] out = new ForgeOwnerRemapStep().apply("zz.link.Probe", probe(), report);
        assertEquals(java.util.Set.of(), ForgeOwnerMappings.defaults().remainingOwners(out));
        assertEquals(2, report.unsupportedCalls().size(), report.unsupportedCalls().toString());
        Class<?> c = new Loader().define("zz.link.Probe", out);

        Object builder = c.getMethod("ok").invoke(null);
        assertInstanceOf(Class.forName("net.neoforged.neoforge.common.ModConfigSpec$Builder", false, c.getClassLoader()),
                builder);

        for (String bad : new String[] {"bad", "badField"}) {
            InvocationTargetException e = assertThrows(InvocationTargetException.class, () -> c.getMethod(bad).invoke(null));
            UnsupportedOperationException cause = assertInstanceOf(UnsupportedOperationException.class, e.getCause());
            assertTrue(cause.getMessage().contains("no verified NeoForge 1.21.1 equivalent"), cause.getMessage());
            assertTrue(cause.getMessage().contains("zz.link.Probe." + bad), cause.getMessage());
        }
    }
}
