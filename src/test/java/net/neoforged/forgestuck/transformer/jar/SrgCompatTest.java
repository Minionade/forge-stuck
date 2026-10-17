package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

class SrgCompatTest {
    static final String LIVING = "net/minecraft/world/entity/LivingEntity";

    /** Mixin class with a @Shadow method named {@code shadowName} targeting {@code target}. */
    static byte[] mixinClass(String name, String target, String shadowName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, name, null, "java/lang/Object", null);
        AnnotationVisitor mixin = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
        AnnotationVisitor arr = mixin.visitArray("value");
        arr.visit(null, Type.getObjectType(target));
        arr.visitEnd();
        mixin.visitEnd();
        var m = cw.visitMethod(Opcodes.ACC_PROTECTED | Opcodes.ACC_ABSTRACT, shadowName,
                "()Lnet/minecraft/world/item/ItemStack;", null, null);
        m.visitAnnotation("Lorg/spongepowered/asm/mixin/Shadow;", true).visitEnd();
        m.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    static byte[] callerClass(String name, String owner, String method) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        var m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "go", "(L" + owner + ";)V", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, method, "()Lnet/minecraft/world/item/ItemStack;", false);
        m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    static Path jar(Path dir, String name, java.util.Map<String, byte[]> files) throws IOException {
        Path jar = dir.resolve(name);
        try (OutputStream os = Files.newOutputStream(jar); JarOutputStream out = new JarOutputStream(os)) {
            put(out, "META-INF/mods.toml", JarTransformerTest.MODS_TOML.getBytes(StandardCharsets.UTF_8));
            for (var e : files.entrySet()) put(out, e.getKey(), e.getValue());
        }
        return jar;
    }

    static void put(JarOutputStream out, String name, byte[] data) throws IOException {
        out.putNextEntry(new JarEntry(name));
        out.write(data);
        out.closeEntry();
    }

    static byte[] read(Path jar, String entry) throws IOException {
        try (JarFile f = new JarFile(jar.toFile())) {
            return f.getInputStream(f.getJarEntry(entry)).readAllBytes();
        }
    }

    static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static ClassNode node(byte[] bytes) {
        ClassNode n = new ClassNode();
        new ClassReader(bytes).accept(n, 0);
        return n;
    }

    @Test
    void remapsShadowMethodAndRefmapWhenMapped(@TempDir Path dir) throws Exception {
        String refmap = "{\"mappings\":{\"com/ex/LivingEntityMixin\":{\"m_21211_\":\"L" + LIVING
                + ";m_21211_()Lnet/minecraft/world/item/ItemStack;\"}}}";
        var files = new java.util.LinkedHashMap<String, byte[]>();
        files.put("com/ex/LivingEntityMixin.class", mixinClass("com/ex/LivingEntityMixin", LIVING, "m_21211_"));
        files.put("mixins.ex.json", b("{\"package\":\"com.ex\",\"mixins\":[\"LivingEntityMixin\"],\"refmap\":\"mixins.ex.refmap.json\"}"));
        files.put("mixins.ex.refmap.json", b(refmap));
        Path out = dir.resolve("out.jar");
        JarTransformer.withDefaultSteps().transform(jar(dir, "in.jar", files), out);

        ClassNode n = node(read(out, "com/ex/LivingEntityMixin.class"));
        assertEquals("getUseItem", ((MethodNode) n.methods.get(0)).name);
        String r = new String(read(out, "mixins.ex.refmap.json"), StandardCharsets.UTF_8);
        assertTrue(r.contains("getUseItem()"));
        assertFalse(r.contains("m_21211_"));
        assertTrue(new String(read(out, "mixins.ex.json"), StandardCharsets.UTF_8).contains("LivingEntityMixin"));
    }

    @Test
    void remapsBytecodeCallsToGameClassesOnly(@TempDir Path dir) throws Exception {
        var files = new java.util.LinkedHashMap<String, byte[]>();
        files.put("com/ex/A.class", callerClass("com/ex/A", LIVING, "m_21211_"));
        files.put("com/ex/B.class", callerClass("com/ex/B", "com/other/Thing", "m_21211_"));
        Path out = dir.resolve("out.jar");
        JarTransformer.withDefaultSteps().transform(jar(dir, "in.jar", files), out);
        assertEquals("getUseItem", firstCallName(read(out, "com/ex/A.class")));
        assertEquals("m_21211_", firstCallName(read(out, "com/ex/B.class")));
    }

    private static String firstCallName(byte[] bytes) {
        for (var insn : node(bytes).methods.stream().filter(m -> m.name.equals("go")).findFirst().get().instructions) {
            if (insn instanceof org.objectweb.asm.tree.MethodInsnNode c) return c.name;
        }
        return null;
    }

    @Test
    void dropsOnlyFailingMixinEntriesWhenMappingMissingOrTargetGone(@TempDir Path dir) throws Exception {
        var files = new java.util.LinkedHashMap<String, byte[]>();
        files.put("com/ex/Good.class", mixinClass("com/ex/Good", LIVING, "m_21211_"));
        files.put("com/ex/Unmapped.class", mixinClass("com/ex/Unmapped", LIVING, "m_99999999_"));
        files.put("com/ex/Horse.class", mixinClass("com/ex/Horse", "net/minecraft/world/item/HorseArmorItem", "getX"));
        files.put("mixins.ex.json", b("{\"package\":\"com.ex\",\"mixins\":[\"Good\",\"Unmapped\"],\"client\":[\"Horse\"]}"));
        Path out = dir.resolve("out.jar");
        JarTransformer.withDefaultSteps().transform(jar(dir, "in.jar", files), out);
        String cfg = new String(read(out, "mixins.ex.json"), StandardCharsets.UTF_8);
        assertTrue(cfg.contains("Good"));
        assertFalse(cfg.contains("Unmapped"));
        assertFalse(cfg.contains("Horse"));
    }

    @Test
    void refmapEntryOfDroppedMixinIsRemovedAndUnmappedNamesPreserved(@TempDir Path dir) throws Exception {
        var files = new java.util.LinkedHashMap<String, byte[]>();
        files.put("com/ex/Unmapped.class", mixinClass("com/ex/Unmapped", LIVING, "m_99999999_"));
        files.put("mixins.ex.json", b("{\"package\":\"com.ex\",\"mixins\":[\"Unmapped\"]}"));
        files.put("ex.refmap.json", b("{\"mappings\":{\"com/ex/Unmapped\":{\"m_99999999_\":\"L" + LIVING + ";m_99999999_()V\"},"
                + "\"com/ex/Other\":{\"m_99999999_\":\"L" + LIVING + ";m_99999999_()V\"}}}"));
        Path out = dir.resolve("out.jar");
        JarTransformer.withDefaultSteps().transform(jar(dir, "in.jar", files), out);
        String r = new String(read(out, "ex.refmap.json"), StandardCharsets.UTF_8);
        assertFalse(r.contains("com/ex/Unmapped"));
        assertTrue(r.contains("com/ex/Other"));
        assertTrue(r.contains("m_99999999_"));
    }

    @Test
    void nonMixinResourcesAreUnaffected(@TempDir Path dir) throws Exception {
        String lang = "{\"package\":\"com.ex\",\"mixins\":[\"Unmapped\"],\"k\":\"m_21211_\"}";
        String other = "{\"mappings\":{\"x\":{\"m_21211_\":\"m_21211_\"}}}";
        var files = new java.util.LinkedHashMap<String, byte[]>();
        files.put("assets/ex/lang/en_us.json", b(lang));
        files.put("data/ex/recipes/mixins.json", b(other));
        files.put("config.json", b(other));
        Path out = dir.resolve("out.jar");
        JarTransformer.withDefaultSteps().transform(jar(dir, "in.jar", files), out);
        assertEquals(lang, new String(read(out, "assets/ex/lang/en_us.json"), StandardCharsets.UTF_8));
        assertEquals(other, new String(read(out, "data/ex/recipe/mixins.json"), StandardCharsets.UTF_8));
        assertEquals(other, new String(read(out, "config.json"), StandardCharsets.UTF_8));
    }

    @Test
    void customMappingsAreHonoured() {
        LegacyNameMappings m = new LegacyNameMappings(java.util.Map.of("m_1_", "foo"), java.util.Set.of("a.b.Gone"));
        assertEquals("L" + LIVING + ";foo()V x_m_1_", m.remapTokens("L" + LIVING + ";m_1_()V x_m_1_", null));
        assertTrue(m.isMissingTarget("a/b/Gone"));
        assertFalse(m.isMissingTarget("a/b/Here"));
    }

    @Test
    void cacheStepVersionInvalidatesOldEntries(@TempDir Path dir) throws Exception {
        Path src = JarTransformerTest.buildJar(dir, "adv.jar", JarTransformerTest.MODS_TOML, "1");
        Path cache = Files.createDirectory(dir.resolve("cache"));
        String hash = JarTransformer.sha256(src);
        Path stale = cache.resolve(hash + "-t4.jar");
        Files.write(stale, new byte[] {1, 2, 3});
        JarTransformer.Result r = new JarTransformer(List.of()).transformCached(src, cache);
        assertFalse(r.cacheHit());
        assertNotEquals(stale, r.output());
        assertTrue(r.output().getFileName().toString().endsWith("-t" + JarTransformer.TRANSFORMER_VERSION + ".jar"));
    }
}
