package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class ForgeOwnerRemapTest {
    static final String OLD = "net/minecraftforge/registries/RegistryObject";
    static final String NEW = "net/neoforged/neoforge/registries/DeferredHolder";
    static final String LIVING = "net/minecraft/world/entity/LivingEntity";

    /** Class (optionally a Mixin) with a method invoking {@code OLD.<method><desc>} and a field of type OLD. */
    static byte[] caller(String name, boolean mixin, String method, String desc) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        if (mixin) {
            AnnotationVisitor a = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
            AnnotationVisitor arr = a.visitArray("value");
            arr.visit(null, Type.getObjectType(LIVING));
            arr.visitEnd();
            a.visitEnd();
        }
        cw.visitField(Opcodes.ACC_PUBLIC, "holder", "L" + OLD + ";", "L" + OLD + "<Ljava/lang/Object;>;", null).visitEnd();
        var m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "go", "(L" + OLD + ";)V", null, null);
        var at = m.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/At;", true);
        at.visit("target", "L" + OLD + ";get()Ljava/lang/Object;");
        at.visitEnd();
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, OLD, method, desc, false);
        m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    static ClassNode parse(byte[] b) {
        ClassNode n = new ClassNode();
        new ClassReader(b).accept(n, 0);
        return n;
    }

    @Test
    void registryObjectOwnerIsRemapped() {
        byte[] in = caller("com/ex/M", true, "get", "()Ljava/lang/Object;");
        OwnerRemapReport report = new OwnerRemapReport();
        byte[] out = new ForgeOwnerRemapStep().apply("com.ex.M", in, report);
        ClassNode n = parse(out);
        MethodInsnNode call = (MethodInsnNode) n.methods.get(0).instructions.toArray()[1];
        assertEquals(NEW, call.owner);
        assertEquals("(L" + NEW + ";)V", n.methods.get(0).desc);
        assertEquals("L" + NEW + ";", n.fields.get(0).desc);
        assertEquals("L" + NEW + "<Ljava/lang/Object;>;", n.fields.get(0).signature);
        assertEquals("L" + NEW + ";get()Ljava/lang/Object;",
                n.methods.get(0).visibleAnnotations.get(0).values.get(1));
        assertFalse(new String(out, StandardCharsets.ISO_8859_1).contains(OLD));
        assertTrue(report.total() >= 4);
        assertEquals(Set.of(), ForgeOwnerMappings.defaults().remainingOwners(out));
    }

    @Test
    void classWithoutForgeOwnersIsUnchanged() {
        byte[] in = SrgCompatTest.callerClass("com/ex/Plain", LIVING, "m_1_");
        OwnerRemapReport report = new OwnerRemapReport();
        assertSame(in, new ForgeOwnerRemapStep().apply("com.ex.Plain", in, report));
        assertEquals(0, report.total());
    }

    @Test
    void incompatibleMemberLeavesOwnerUnmappedAndMixinIsDropped() {
        byte[] in = caller("com/ex/Bad", true, "isPresent", "()Z");
        byte[] out = new ForgeOwnerRemapStep().apply("com.ex.Bad", in);
        assertSame(in, out);
        MixinResourceRewriter rewriter = new MixinResourceRewriter(LegacyNameMappings.defaults(), "x.jar");
        rewriter.inspectClass("com.ex.Bad", out);
        String reason = rewriter.droppedMixins().get("com.ex.Bad");
        assertNotNull(reason);
        assertTrue(reason.contains("unresolved owner") && reason.contains(OLD), reason);
        String cfg = "{\"package\":\"com.ex\",\"mixins\":[\"Bad\",\"Good\"]}";
        String rewritten = new String(rewriter.rewrite("x.mixins.json", cfg.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
        assertFalse(rewritten.contains("Bad"));
        assertTrue(rewritten.contains("Good"));
    }

    @Test
    void remappedMixinIsNotDropped() {
        byte[] out = new ForgeOwnerRemapStep().apply("com.ex.Ok", caller("com/ex/Ok", true, "get", "()Ljava/lang/Object;"));
        MixinResourceRewriter rewriter = new MixinResourceRewriter(LegacyNameMappings.defaults(), "x.jar");
        rewriter.inspectClass("com.ex.Ok", out);
        assertTrue(rewriter.droppedMixins().isEmpty());
    }

    @Test
    void refmapDescriptorsAreRemapped() {
        MixinResourceRewriter rewriter = new MixinResourceRewriter(LegacyNameMappings.defaults(), "x.jar");
        String refmap = "{\"mappings\":{\"com/ex/Ok\":{\"a\":\"L" + OLD + ";get()Ljava/lang/Object;\"}}}";
        String out = new String(rewriter.rewrite("x.refmap.json", refmap.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
        assertTrue(out.contains(NEW));
        assertFalse(out.contains(OLD));
    }

    @Test
    void jarWithoutForgeOwnersIsUntouchedAndCustomTableWorks() {
        ForgeOwnerMappings custom = new ForgeOwnerMappings().add("a/Old", "b/New");
        byte[] in = caller("com/ex/Plain", false, "get", "()Ljava/lang/Object;");
        assertSame(in, new ForgeOwnerRemapStep(custom).apply("com.ex.Plain", in));
    }

    @Test
    void transformVersionInvalidatesOldCache(@TempDir Path dir) throws Exception {
        assertTrue(Integer.parseInt(JarTransformer.TRANSFORMER_VERSION) > 5);
        Path src = JarTransformerTest.buildJar(dir, "adv.jar", JarTransformerTest.MODS_TOML, "1");
        Path cache = Files.createDirectory(dir.resolve("cache"));
        Path stale = cache.resolve(JarTransformer.sha256(src) + "-t5.jar");
        Files.write(stale, new byte[] {1, 2, 3});
        JarTransformer.Result r = new JarTransformer(List.of()).transformCached(src, cache);
        assertFalse(r.cacheHit());
        assertNotEquals(stale, r.output());
    }

    @Test
    void jarTransformerRemapsMixinClassInJar(@TempDir Path dir) throws Exception {
        var files = new java.util.LinkedHashMap<String, byte[]>();
        files.put("com/ex/Ok.class", caller("com/ex/Ok", true, "get", "()Ljava/lang/Object;"));
        files.put("com/ex/Bad.class", caller("com/ex/Bad", true, "isPresent", "()Z"));
        files.put("ex.mixins.json", "{\"package\":\"com.ex\",\"mixins\":[\"Ok\",\"Bad\"]}".getBytes(StandardCharsets.UTF_8));
        Path out = dir.resolve("out.jar");
        JarTransformer.withDefaultSteps().transform(SrgCompatTest.jar(dir, "in.jar", files), out);
        assertEquals(NEW, ((MethodInsnNode) parse(SrgCompatTest.read(out, "com/ex/Ok.class")).methods.get(0)
                .instructions.toArray()[1]).owner);
        String cfg = new String(SrgCompatTest.read(out, "ex.mixins.json"), StandardCharsets.UTF_8);
        assertTrue(cfg.contains("Ok"));
        assertFalse(cfg.contains("Bad"));
    }
}
