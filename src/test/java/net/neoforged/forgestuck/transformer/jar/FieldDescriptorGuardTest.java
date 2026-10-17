package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;

/**
 * Opt-in guard (FORGESTUCK_SCAN_DIR = directory of transformed jars): every field reference to a Minecraft/NeoForge
 * class that exists in the target must match the real field's name <em>and</em> descriptor. Missing or retyped
 * fields are reported so a name-only match can never hide a {@code NoSuchFieldError}. References that
 * {@code SrgMemberRemapStep}/{@code ForgeOwnerRemapStep} already turned into call-time failures no longer appear.
 */
class FieldDescriptorGuardTest {
    static Field find(Class<?> c, String name) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                return k.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
            for (Class<?> i : k.getInterfaces()) {
                Field f = find(i, name);
                if (f != null) return f;
            }
        }
        return null;
    }

    static List<String> mismatches(byte[] bytes) {
        ClassNode n = new ClassNode();
        new ClassReader(bytes).accept(n, 0);
        List<String> out = new ArrayList<>();
        for (var m : n.methods) {
            for (var insn : m.instructions) {
                if (!(insn instanceof FieldInsnNode f) || !SrgMemberRemapStep.ownerIsGame(f.owner)
                        || f.owner.equals(n.name)) continue;
                Class<?> owner;
                try {
                    owner = Class.forName(f.owner.replace('/', '.'), false, FieldDescriptorGuardTest.class.getClassLoader());
                } catch (ClassNotFoundException | LinkageError e) {
                    continue;
                }
                Field real = find(owner, f.name);
                if (real == null) {
                    out.add(f.owner + "." + f.name + " (missing in target)");
                } else if (!Type.getDescriptor(real.getType()).equals(f.desc)) {
                    out.add(f.owner + "." + f.name + " expected " + Type.getDescriptor(real.getType()) + " but ref has "
                            + f.desc);
                }
            }
        }
        return out;
    }

    @Test
    void transformedJarsReferenceExistingFieldsWithMatchingDescriptors() throws Exception {
        String dir = System.getenv("FORGESTUCK_SCAN_DIR");
        assumeTrue(dir != null, "FORGESTUCK_SCAN_DIR not set");
        List<String> offenders = new ArrayList<>();
        try (var jars = Files.list(Path.of(dir))) {
            for (Path jar : jars.filter(p -> p.toString().endsWith(".jar")).toList()) {
                try (JarFile jf = new JarFile(jar.toFile())) {
                    for (JarEntry e : Collections.list(jf.entries())) {
                        if (!e.getName().endsWith(".class") || e.getName().startsWith("META-INF/")) continue;
                        byte[] bytes;
                        try (InputStream in = jf.getInputStream(e)) {
                            bytes = in.readAllBytes();
                        }
                        var bad = mismatches(bytes);
                        if (!bad.isEmpty()) offenders.add(jar.getFileName() + "!" + e.getName() + " " + bad);
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Field refs not matching the target:\n" + String.join("\n", offenders));
    }

    @Test
    void detectsMissingAndRetypedFields() {
        var cw = new org.objectweb.asm.ClassWriter(0);
        cw.visit(org.objectweb.asm.Opcodes.V21, org.objectweb.asm.Opcodes.ACC_PUBLIC, "com/ex/G", null,
                "java/lang/Object", null);
        var m = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_STATIC, "go", "()V", null, null);
        m.visitCode();
        m.visitFieldInsn(org.objectweb.asm.Opcodes.GETSTATIC, "net/minecraft/core/registries/BuiltInRegistries",
                "ENCHANTMENT", "Lnet/minecraft/core/Registry;");
        m.visitInsn(org.objectweb.asm.Opcodes.POP);
        m.visitFieldInsn(org.objectweb.asm.Opcodes.GETSTATIC, "net/minecraft/core/registries/Registries",
                "CREATIVE_MODE_TAB", "Lnet/minecraft/core/Registry;");
        m.visitInsn(org.objectweb.asm.Opcodes.POP);
        m.visitInsn(org.objectweb.asm.Opcodes.RETURN);
        m.visitMaxs(1, 0);
        m.visitEnd();
        cw.visitEnd();
        List<String> bad = mismatches(cw.toByteArray());
        assertTrue(bad.size() == 2 && bad.get(0).contains("missing") && bad.get(1).contains("expected"), bad.toString());
    }
}
