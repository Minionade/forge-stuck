package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Guards against SRG (f_/m_) member names surviving in references to Minecraft/NeoForge owners. */
class SrgResidualTest {
    static List<String> residualSrgRefs(byte[] bytes) {
        ClassNode n = new ClassNode();
        new ClassReader(bytes).accept(n, 0);
        List<String> out = new ArrayList<>();
        for (var m : n.methods) {
            for (var insn : m.instructions) {
                if (insn instanceof FieldInsnNode f && LegacyNameMappings.looksLikeSrg(f.name)
                        && SrgMemberRemapStep.ownerIsGame(f.owner)) out.add(f.owner + "." + f.name);
                if (insn instanceof MethodInsnNode c && LegacyNameMappings.looksLikeSrg(c.name)
                        && SrgMemberRemapStep.ownerIsGame(c.owner)) out.add(c.owner + "." + c.name + c.desc);
            }
        }
        return out;
    }

    static byte[] user() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/Types", null, "java/lang/Object", null);
        var m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "go", "(Lnet/minecraft/world/entity/LivingEntity;)V", null, null);
        m.visitCode();
        m.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/core/registries/Registries", "f_279569_",
                "Lnet/minecraft/resources/ResourceKey;");
        m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/LivingEntity", "m_21205_",
                "()Lnet/minecraft/world/item/ItemStack;", false);
        m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    void generatedTableCoversVanillaRegistryKeysAndIsLarge() {
        LegacyNameMappings m = LegacyNameMappings.defaults();
        assertEquals("CREATIVE_MODE_TAB", m.map("net/minecraft/core/registries/Registries", "f_279569_",
                "Lnet/minecraft/resources/ResourceKey;"));
        assertEquals("getMainHandItem", m.map("m_21205_"));
        assertTrue(m.map("f_279569_") != null);
    }

    @Test
    void srgReferencesToGameOwnersAreTranslated(@TempDir Path dir) throws Exception {
        var files = new LinkedHashMap<String, byte[]>();
        files.put("com/ex/Types.class", user());
        assertEquals(2, residualSrgRefs(user()).size());
        Path out = dir.resolve("out.jar");
        JarTransformer.withDefaultSteps().transform(SrgCompatTest.jar(dir, "in.jar", files), out);
        byte[] b = SrgCompatTest.read(out, "com/ex/Types.class");
        assertEquals(List.of(), residualSrgRefs(b));
        var text = new String(b, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(text.contains("CREATIVE_MODE_TAB") && text.contains("getMainHandItem"));
    }

    @Test
    void unmappedSrgMemberOnGameOwnerLinksAndFailsOnlyWhenExecuted(@TempDir Path dir) throws Exception {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/Gone", null, "java/lang/Object", null);
        var ok = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "ok", "()I", null, null);
        ok.visitCode();
        ok.visitInsn(Opcodes.ICONST_5);
        ok.visitInsn(Opcodes.IRETURN);
        ok.visitMaxs(0, 0);
        ok.visitEnd();
        var m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "go", "()Ljava/lang/Object;", null, null);
        m.visitCode();
        m.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/core/registries/BuiltInRegistries", "f_999999_",
                "Lnet/minecraft/core/Registry;");
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();
        var files = new LinkedHashMap<String, byte[]>();
        files.put("com/ex/Gone.class", cw.toByteArray());
        Path out = dir.resolve("out.jar");
        JarTransformer.withDefaultSteps().transform(SrgCompatTest.jar(dir, "in.jar", files), out);
        byte[] b = SrgCompatTest.read(out, "com/ex/Gone.class");
        assertEquals(List.of(), residualSrgRefs(b));
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() {
                return defineClass("com.ex.Gone", b, 0, b.length);
            }
        };
        Class<?> c = (Class<?>) loader.getClass().getDeclaredMethod("define").invoke(loader);
        assertEquals(5, c.getMethod("ok").invoke(null));
        var ex = org.junit.jupiter.api.Assertions.assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> c.getMethod("go").invoke(null));
        assertTrue(ex.getCause() instanceof UnsupportedOperationException
                && ex.getCause().getMessage().contains("f_999999_"), String.valueOf(ex.getCause()));
    }

    /** Opt-in: set FORGESTUCK_SCAN_DIR to a directory of transformed jars (e.g. mods/.forgestuck). */    @Test
    void transformedJarsLeaveNoSrgReferencesToGameOwners() throws Exception {
        String dir = System.getenv("FORGESTUCK_SCAN_DIR");
        assumeTrue(dir != null, "FORGESTUCK_SCAN_DIR not set");
        List<String> offenders = new ArrayList<>();
        try (var jars = Files.list(Path.of(dir))) {
            for (Path jar : jars.filter(p -> p.toString().endsWith(".jar")).toList()) {
                try (JarFile jf = new JarFile(jar.toFile())) {
                    for (JarEntry e : java.util.Collections.list(jf.entries())) {
                        if (!e.getName().endsWith(".class") || e.getName().startsWith("META-INF/")) continue;
                        byte[] bytes;
                        try (InputStream in = jf.getInputStream(e)) {
                            bytes = in.readAllBytes();
                        }
                        var left = residualSrgRefs(bytes);
                        if (!left.isEmpty()) offenders.add(jar.getFileName() + "!" + e.getName() + " " + left);
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "SRG names left in game references:\n" + String.join("\n", offenders));
    }
}
