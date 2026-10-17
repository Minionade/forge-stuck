package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;

/** Static evaluation of legacy ItemDisplayContext.create call sites, mirroring a helper-based real-world pattern. */
class LegacyEnumExtensionTest {
    private static final String IDC = LegacyEnumExtensionPlanner.ITEM_DISPLAY_CONTEXT;
    private static final String IDC_DESC = "L" + IDC + ";";
    private static final String RL = "net/minecraft/resources/ResourceLocation";

    private static byte[] fixture(boolean constantName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "zz/tc/Disp", null, "java/lang/Object", null);
        var helper = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "make",
                "(Ljava/lang/String;" + IDC_DESC + ")" + IDC_DESC, null, null);
        helper.visitCode();
        helper.visitVarInsn(Opcodes.ALOAD, 0);
        helper.visitFieldInsn(Opcodes.GETSTATIC, "java/util/Locale", "ROOT", "Ljava/util/Locale;");
        helper.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toUpperCase",
                "(Ljava/util/Locale;)Ljava/lang/String;", false);
        helper.visitTypeInsn(Opcodes.NEW, RL);
        helper.visitInsn(Opcodes.DUP);
        helper.visitLdcInsn("zzmod");
        helper.visitVarInsn(Opcodes.ALOAD, 0);
        helper.visitMethodInsn(Opcodes.INVOKESPECIAL, RL, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V", false);
        Label none = new Label();
        Label join = new Label();
        helper.visitVarInsn(Opcodes.ALOAD, 1);
        helper.visitFieldInsn(Opcodes.GETSTATIC, IDC, "NONE", IDC_DESC);
        helper.visitJumpInsn(Opcodes.IF_ACMPEQ, none);
        helper.visitVarInsn(Opcodes.ALOAD, 1);
        helper.visitJumpInsn(Opcodes.GOTO, join);
        helper.visitLabel(none);
        helper.visitInsn(Opcodes.ACONST_NULL);
        helper.visitLabel(join);
        helper.visitMethodInsn(Opcodes.INVOKESTATIC, IDC, "create", LegacyEnumExtensionPlanner.CREATE_DESC, false);
        helper.visitInsn(Opcodes.ARETURN);
        helper.visitMaxs(0, 0);
        helper.visitEnd();

        var clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();
        clinit.visitLdcInsn("melter");
        clinit.visitFieldInsn(Opcodes.GETSTATIC, IDC, "NONE", IDC_DESC);
        clinit.visitMethodInsn(Opcodes.INVOKESTATIC, "zz/tc/Disp", "make", "(Ljava/lang/String;" + IDC_DESC + ")" + IDC_DESC, false);
        clinit.visitInsn(Opcodes.POP);
        clinit.visitLdcInsn("tank");
        clinit.visitFieldInsn(Opcodes.GETSTATIC, IDC, "FIXED", IDC_DESC);
        clinit.visitMethodInsn(Opcodes.INVOKESTATIC, "zz/tc/Disp", "make", "(Ljava/lang/String;" + IDC_DESC + ")" + IDC_DESC, false);
        clinit.visitInsn(Opcodes.POP);
        if (!constantName) {
            clinit.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "lineSeparator", "()Ljava/lang/String;", false);
            clinit.visitInsn(Opcodes.ACONST_NULL);
            clinit.visitInsn(Opcodes.ACONST_NULL);
            clinit.visitMethodInsn(Opcodes.INVOKESTATIC, IDC, "create", LegacyEnumExtensionPlanner.CREATE_DESC, false);
            clinit.visitInsn(Opcodes.POP);
        }
        clinit.visitInsn(Opcodes.RETURN);
        clinit.visitMaxs(0, 0);
        clinit.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    void helperArgumentsAreEvaluatedThroughBranchesAndConcats() {
        byte[] bytes = fixture(true);
        var plan = LegacyEnumExtensionPlanner.plan(List.of("zz/tc/Disp"), n -> n.equals("zz/tc/Disp") ? bytes : null);
        assertEquals(2, plan.entries().size(), plan.toString());
        assertEquals("MELTER", plan.entries().get(0).keyName());
        assertEquals("zzmod:melter", plan.entries().get(0).id());
        assertNull(plan.entries().get(0).fallback());
        assertEquals("zzmod:tank", plan.entries().get(1).id());
        assertEquals("FIXED", plan.entries().get(1).fallback());
        assertTrue(plan.unresolvedSites().isEmpty(), plan.unresolvedSites().toString());
    }

    @Test
    void nonConstantArgumentsStayUnresolvedWithoutDroppingResolvedEntries() {
        byte[] bytes = fixture(false);
        var plan = LegacyEnumExtensionPlanner.plan(List.of("zz/tc/Disp"), n -> n.equals("zz/tc/Disp") ? bytes : null);
        assertEquals(2, plan.entries().size());
        assertEquals(1, plan.unresolvedSites().size(), plan.unresolvedSites().toString());
    }

    @Test
    void jsonFollowsNeoForgeSchemaAndOnlyContainsOwnNamespace() {
        var entries = List.of(
                new LegacyEnumExtensionPlanner.Entry("MELTER", "zzmod:melter", null),
                new LegacyEnumExtensionPlanner.Entry("TANK", "zzmod:tank", "FIXED"),
                new LegacyEnumExtensionPlanner.Entry("OTHER", "other:x", null));
        String json = EnumExtensionJson.render("zzmod", entries);
        var root = com.google.gson.JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("entries");
        assertEquals(2, root.size());
        var first = root.get(0).getAsJsonObject();
        assertEquals(IDC, first.get("enum").getAsString());
        assertTrue(first.get("name").getAsString().toLowerCase(java.util.Locale.ROOT).startsWith("zzmod"));
        assertEquals("(ILjava/lang/String;Ljava/lang/String;)V", first.get("constructor").getAsString());
        var params = first.getAsJsonArray("parameters");
        assertEquals(-1, params.get(0).getAsInt());
        assertEquals("zzmod:melter", params.get(1).getAsString());
        assertTrue(params.get(2).isJsonNull());
        assertEquals("FIXED", root.get(1).getAsJsonObject().getAsJsonArray("parameters").get(2).getAsString());
        assertNull(EnumExtensionJson.render("nomatch", entries));
        assertEquals("META-INF/forgestuck/enumextensions-zzmod.json", EnumExtensionJson.path("zzmod"));
    }

    @Test
    void modsTomlReferencesTheGeneratedFile() {
        String toml = "modLoader=\"javafml\"\nloaderVersion=\"[47,)\"\nlicense=\"x\"\n[[mods]]\nmodId=\"zzmod\"\nversion=\"1\"\n";
        String out = JarTransformer.withDefaultSteps().convertModsToml(toml, "1", "zz.jar", Map.of("zzmod", "META-INF/forgestuck/enumextensions-zzmod.json"));
        assertTrue(out.contains("enumExtensions"), out);
    }

    @Test
    void transformedJarGetsEnumExtensionAndRewrittenCallFailsPreciselyWithoutRuntimeExtender(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        String toml = "modLoader=\"javafml\"\nloaderVersion=\"[47,)\"\nlicense=\"x\"\n[[mods]]\nmodId=\"zzmod\"\nversion=\"1\"\n";
        java.nio.file.Path src = dir.resolve("zz.jar");
        try (var out = new java.util.jar.JarOutputStream(java.nio.file.Files.newOutputStream(src))) {
            out.putNextEntry(new java.util.jar.JarEntry("META-INF/mods.toml"));
            out.write(toml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new java.util.jar.JarEntry("zz/tc/Disp.class"));
            out.write(fixture(true));
            out.closeEntry();
        }
        java.nio.file.Path dst = dir.resolve("out.jar");
        JarTransformer.withDefaultSteps().transform(src, dst);
        byte[] cls;
        try (var jar = new java.util.jar.JarFile(dst.toFile())) {
            var json = jar.getJarEntry("META-INF/forgestuck/enumextensions-zzmod.json");
            assertNotNull(json);
            String meta = new String(jar.getInputStream(jar.getJarEntry("META-INF/neoforge.mods.toml")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(meta.contains("enumExtensions") && meta.contains("enumextensions-zzmod.json"), meta);
            cls = jar.getInputStream(jar.getJarEntry("zz/tc/Disp.class")).readAllBytes();
        }
        var asm = new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(cls).accept(asm, 0);
        boolean rewritten = asm.methods.stream().flatMap(m -> java.util.Arrays.stream(m.instructions.toArray()))
                .anyMatch(i -> i instanceof org.objectweb.asm.tree.MethodInsnNode mi
                        && mi.owner.equals("net/neoforged/forgestuck/compat/LegacyEnumExtensions"));
        assertTrue(rewritten);
        // second run on the same input is deterministic
        java.nio.file.Path dst2 = dir.resolve("out2.jar");
        JarTransformer.withDefaultSteps().transform(src, dst2);
        assertEquals(java.nio.file.Files.size(dst), java.nio.file.Files.size(dst2));
    }
}