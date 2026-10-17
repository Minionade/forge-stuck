package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/** Fails when a transformed class still references an owner that exists in the mapping table. */
class ResidualOwnerGuardTest {
    static final String EVENT = "net/minecraftforge/event/server/ServerStoppingEvent";

    static byte[] eventUser(String name) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        var m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "on", "(L" + EVENT + ";)V", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, EVENT, "getServer", "()Lnet/minecraft/server/MinecraftServer;", false);
        m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, EVENT, "noSuchMember", "()Z", false);
        m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    void lifecycleEventOwnerIsMappedAndNoMappedOwnerRemainsInSyntheticJar(@TempDir Path dir) throws Exception {
        var files = new LinkedHashMap<String, byte[]>();
        files.put("com/ex/Handler.class", eventUser("com/ex/Handler"));
        Path out = dir.resolve("out.jar");
        JarTransformer.withDefaultSteps().transform(SrgCompatTest.jar(dir, "in.jar", files), out);
        byte[] b = SrgCompatTest.read(out, "com/ex/Handler.class");
        assertEquals(java.util.Set.of(), ForgeOwnerMappings.defaults().remainingOwners(b));
        var node = ForgeOwnerRemapTest.parse(b);
        assertTrue(new String(b, StandardCharsets.ISO_8859_1).contains("net/neoforged/neoforge/event/server/ServerStoppingEvent"));
        assertTrue(node.methods.stream().anyMatch(m -> m.name.equals("on")));
    }

    /** Opt-in: set FORGESTUCK_SCAN_DIR to a directory of transformed jars (e.g. mods/.forgestuck). */
    @Test
    void transformedJarsLeaveNoMappedOwnerUnmapped() throws Exception {
        String dir = System.getenv("FORGESTUCK_SCAN_DIR");
        assumeTrue(dir != null, "FORGESTUCK_SCAN_DIR not set");
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
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
                        // Mixin classes with unsupported members are intentionally left and dropped from the config
                        if (new String(bytes, StandardCharsets.ISO_8859_1).contains("org/spongepowered/asm/mixin/Mixin")) continue;
                        var left = mappings.remainingOwners(bytes);
                        if (!left.isEmpty()) offenders.add(jar.getFileName() + "!" + e.getName() + " " + left);
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Mapped owners left unmapped (constructors/method handles):\n" + String.join("\n", offenders));
    }
}
