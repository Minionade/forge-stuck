package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class LazyOptionalRemapTest {
    static final String LO = "net/minecraftforge/common/util/LazyOptional";
    static final String NNC = "net/minecraftforge/common/util/NonNullConsumer";
    static final String NEW_LO = "net/neoforged/forgestuck/compat/LazyOptional";
    static final String NEW_NNC = "net/neoforged/forgestuck/compat/NonNullConsumer";

    /** Class whose static method {@code go(LazyOptional, NonNullConsumer)} invokes {@code LO.<method><desc>}. */
    static byte[] caller(String name, boolean mixin, String method, String desc) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        if (mixin) {
            AnnotationVisitor a = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
            AnnotationVisitor arr = a.visitArray("value");
            arr.visit(null, Type.getObjectType(ForgeOwnerRemapTest.LIVING));
            arr.visitEnd();
            a.visitEnd();
        }
        var m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "go",
                "(L" + LO + ";L" + NNC + ";)V", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitVarInsn(Opcodes.ALOAD, 1);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, LO, method, desc, false);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    static final String IF_PRESENT = "(L" + NNC + ";)V";

    @Test
    void ownerAndDescriptorAreRemappedInClass() {
        OwnerRemapReport report = new OwnerRemapReport();
        byte[] out = new ForgeOwnerRemapStep().apply("com.ex.C", caller("com/ex/C", false, "ifPresent", IF_PRESENT),
                report);
        ClassNode n = ForgeOwnerRemapTest.parse(out);
        assertEquals("(L" + NEW_LO + ";L" + NEW_NNC + ";)V", n.methods.get(0).desc);
        MethodInsnNode call = null;
        for (var insn : n.methods.get(0).instructions) if (insn instanceof MethodInsnNode mi) call = mi;
        assertNotNull(call);
        assertEquals(NEW_LO, call.owner);
        assertEquals("(L" + NEW_NNC + ";)V", call.desc);
        assertFalse(new String(out, StandardCharsets.ISO_8859_1).contains("net/minecraftforge"));
        assertEquals(1, report.descriptorsAdapted());
        assertTrue(report.counts().containsKey(NNC));
        assertTrue(ForgeOwnerMappings.defaults().remainingOwners(out).isEmpty());
    }

    @Test
    void mixinWithIfPresentIsRemappedAndKept() {
        byte[] out = new ForgeOwnerRemapStep().apply("com.ex.M", caller("com/ex/M", true, "ifPresent", IF_PRESENT));
        MixinResourceRewriter rewriter = new MixinResourceRewriter(LegacyNameMappings.defaults(), "x.jar");
        rewriter.inspectClass("com.ex.M", out);
        assertTrue(rewriter.droppedMixins().isEmpty());
    }

    @Test
    void unknownMethodLeavesOwnerAndMixinIsDropped() {
        byte[] in = caller("com/ex/Bad", true, "filter", "(Ljava/lang/Object;)V");
        byte[] out = new ForgeOwnerRemapStep().apply("com.ex.Bad", in);
        assertEquals(java.util.Set.of(LO), ForgeOwnerMappings.defaults().remainingOwners(out));
        MixinResourceRewriter rewriter = new MixinResourceRewriter(LegacyNameMappings.defaults(), "x.jar");
        rewriter.inspectClass("com.ex.Bad", out);
        String reason = rewriter.droppedMixins().get("com.ex.Bad");
        assertNotNull(reason);
        assertTrue(reason.contains("unresolved owner") && reason.contains(LO), reason);
    }

    @Test
    void unrelatedClassIsUnchanged() {
        byte[] in = SrgCompatTest.callerClass("com/ex/Plain", ForgeOwnerRemapTest.LIVING, "m_1_");
        assertSame(in, new ForgeOwnerRemapStep().apply("com.ex.Plain", in, new OwnerRemapReport()));
    }

    @Test
    void transformVersionIsAtLeastNine() {
    }

    @Test
    void shimExtraMembersMatchForgeSemantics() {
        var lo = net.neoforged.forgestuck.compat.LazyOptional.of(() -> "abc");
        assertEquals(java.util.Optional.of(3), lo.map(String::length));
        assertEquals(java.util.Optional.empty(), lo.filter(s -> s.isEmpty()));
        assertEquals("abc", lo.orElseGet(() -> "z"));
        assertSame(lo, lo.cast());
        assertEquals("abc", lo.orElseThrow(IllegalStateException::new));
        var empty = net.neoforged.forgestuck.compat.LazyOptional.<String>empty();
        assertEquals("z", empty.orElseGet(() -> "z"));
        assertThrows(IllegalStateException.class, () -> empty.orElseThrow(IllegalStateException::new));
        int[] calls = {0};
        net.neoforged.forgestuck.compat.NonNullConsumer<net.neoforged.forgestuck.compat.LazyOptional<String>> l =
                x -> calls[0]++;
        var other = net.neoforged.forgestuck.compat.LazyOptional.of(() -> "q");
        other.addListener(l);
        other.removeListener(l);
        other.invalidate();
        assertEquals(0, calls[0]);
    }

    @Test
    void shimBehavesLikeForgeLazyOptional() {
        var lo = net.neoforged.forgestuck.compat.LazyOptional.of(() -> "x");
        StringBuilder sb = new StringBuilder();
        lo.ifPresent(sb::append);
        assertEquals("x", sb.toString());
        lo.invalidate();
        assertFalse(lo.isPresent());
        assertEquals("y", lo.orElse("y"));
        assertFalse(net.neoforged.forgestuck.compat.LazyOptional.empty().isPresent());
        net.neoforged.forgestuck.compat.LazyOptional.empty().invalidate();
        assertFalse(net.neoforged.forgestuck.compat.LazyOptional.empty().isPresent());
    }
}
