package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import net.neoforged.forgestuck.compat.LegacyResourceLocations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Legacy subclasses of the (1.20) non-final ResourceLocation, e.g. an id helper class. */
class ResourceLocationSubclassTest {
    private static final String RL = "net/minecraft/resources/ResourceLocation";
    private static final String S = "Ljava/lang/String;";

    private static byte[] subclass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "zz/id/Id", null, RL, null);
        String[][] ctors = {{"(" + S + S + "L" + RL + "$Dummy;)V", "3"}, {"(" + S + S + ")V", "2"}, {"(" + S + ")V", "1"}};
        for (String[] c : ctors) {
            var m = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", c[0], null, null);
            m.visitCode();
            m.visitVarInsn(Opcodes.ALOAD, 0);
            for (int i = 1; i <= Integer.parseInt(c[1]); i++) m.visitVarInsn(Opcodes.ALOAD, i);
            m.visitMethodInsn(Opcodes.INVOKESPECIAL, RL, "<init>", c[0], false);
            m.visitInsn(Opcodes.RETURN);
            m.visitMaxs(0, 0);
            m.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    void superConstructorCallsTargetThe121Constructor() {
        byte[] out = new LegacyResourceLocationSubclassStep().apply("zz.id.Id", subclass());
        ClassNode node = new ClassNode();
        new ClassReader(out).accept(node, 0);
        for (var m : node.methods) {
            var supers = java.util.Arrays.stream(m.instructions.toArray())
                    .filter(i -> i instanceof MethodInsnNode c && c.owner.equals(RL) && c.name.equals("<init>"))
                    .map(i -> ((MethodInsnNode) i).desc).toList();
            assertEquals(java.util.List.of("(" + S + S + ")V"), supers, m.desc);
        }
        assertFalse(java.util.Arrays.stream(node.methods.get(0).instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode c && c.desc.contains("Dummy")));
    }

    @Test
    void helpersParseAndValidateLikeTheLegacyConstructors() {
        assertEquals("zz", LegacyResourceLocations.namespace("zz:a/b"));
        assertEquals("a/b", LegacyResourceLocations.path("zz:a/b"));
        assertEquals("minecraft", LegacyResourceLocations.namespace("stone"));
        assertThrows(net.minecraft.ResourceLocationException.class, () -> LegacyResourceLocations.validate("Bad NS", "x"));
        assertThrows(net.minecraft.ResourceLocationException.class, () -> LegacyResourceLocations.path("a:B ad"));
    }

    @Test
    void jarGetsAccessTransformerOnlyWhenSubclassPresentAndKeepsExistingOne(@TempDir Path dir) throws Exception {
        String toml = "modLoader=\"javafml\"\nloaderVersion=\"[47,)\"\nlicense=\"x\"\n[[mods]]\nmodId=\"zzmod\"\nversion=\"1\"\n";
        Path src = dir.resolve("a.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(src))) {
            out.putNextEntry(new JarEntry("META-INF/mods.toml"));
            out.write(toml.getBytes(StandardCharsets.UTF_8));
            out.putNextEntry(new JarEntry("META-INF/accesstransformer.cfg"));
            out.write("public a.B c\n".getBytes(StandardCharsets.UTF_8));
            out.putNextEntry(new JarEntry("zz/id/Id.class"));
            out.write(subclass());
            out.closeEntry();
        }
        Path dst = dir.resolve("b.jar");
        JarTransformer.withDefaultSteps().transform(src, dst);
        try (JarFile jar = new JarFile(dst.toFile())) {
            String at = new String(jar.getInputStream(jar.getJarEntry("META-INF/accesstransformer.cfg")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(at.startsWith("public a.B c"), at);
            assertTrue(at.contains("public-f net.minecraft.resources.ResourceLocation\n"), at);
            assertTrue(at.contains("<init>(Ljava/lang/String;Ljava/lang/String;)V"), at);
        }
    }
}