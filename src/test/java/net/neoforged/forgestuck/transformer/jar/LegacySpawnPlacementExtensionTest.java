package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class LegacySpawnPlacementExtensionTest {
    private static final String TYPE = LegacyEnumExtensionPlanner.SPAWN_PLACEMENT_TYPE;

    private static byte[] fixture(String name) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "zz/tc/Placement", null, "java/lang/Object", null);
        var clinit = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();
        clinit.visitLdcInsn(name);
        clinit.visitMethodInsn(Opcodes.INVOKESTATIC, TYPE, "create", "(Ljava/lang/String;)L" + TYPE + ";", false);
        clinit.visitInsn(Opcodes.POP);
        clinit.visitInsn(Opcodes.RETURN);
        clinit.visitMaxs(0, 0);
        clinit.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    @Test
    void plansPlacementEnumExtensionsFromCreateCalls() {
        byte[] bytes = fixture("zzmod_CUSTOM");
        var plan = LegacyEnumExtensionPlanner.plan(List.of("zz/tc/Placement"),
                name -> name.equals("zz/tc/Placement") ? bytes : null);

        assertEquals(List.of(new LegacyEnumExtensionPlanner.PlacementEntry("zzmod_CUSTOM")),
                plan.placementEntries());
        assertTrue(plan.unresolvedPlacementSites().isEmpty());

        var extensions = com.google.gson.JsonParser.parseString(EnumExtensionJson.render("zzmod", List.of(),
                plan.placementEntries(), false)).getAsJsonObject().getAsJsonArray("entries");
        assertEquals(1, extensions.size());
        var entry = extensions.get(0).getAsJsonObject();
        assertEquals(LegacyEnumExtensionPlanner.LEGACY_SPAWN_PLACEMENT_TYPE, entry.get("enum").getAsString());
        assertEquals("zzmod_CUSTOM", entry.get("name").getAsString());
        assertEquals(EnumExtensionJson.SPAWN_PLACEMENT_TYPE_CTOR, entry.get("constructor").getAsString());
        assertEquals("zzmod_CUSTOM", entry.getAsJsonArray("parameters").get(0).getAsString());
    }

    @Test
    void customFactoryCallRemapsToCompatLookup() {
        byte[] bytes = fixture("zzmod_CUSTOM");
        var remapped = new ClassNode();
        new org.objectweb.asm.ClassReader(new ForgeOwnerRemapStep().apply("zz.tc.Placement", bytes)).accept(remapped, 0);

        assertTrue(remapped.methods.stream().flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.owner.equals("net/neoforged/forgestuck/compat/LegacySpawnPlacementType")
                        && call.name.equals("create")
                        && call.desc.equals("(Ljava/lang/String;)Lnet/neoforged/forgestuck/compat/LegacySpawnPlacementType;")));
    }

    @Test
    void transformerAddsPlacementExtensionToModMetadata(@TempDir java.nio.file.Path dir) throws Exception {
        var source = dir.resolve("mod.jar");
        String modsToml = "modLoader=\"javafml\"\nloaderVersion=\"[47,)\"\nlicense=\"x\"\n"
                + "[[mods]]\nmodId=\"zzmod\"\nversion=\"1\"\n";
        try (var out = new JarOutputStream(Files.newOutputStream(source))) {
            out.putNextEntry(new JarEntry("META-INF/mods.toml"));
            out.write(modsToml.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new JarEntry("zz/tc/Placement.class"));
            out.write(fixture("CUSTOM_PLACEMENT"));
            out.closeEntry();
        }

        var target = dir.resolve("transformed.jar");
        JarTransformer.withDefaultSteps().transform(source, target);
        try (var jar = new java.util.jar.JarFile(target.toFile())) {
            var json = jar.getJarEntry("META-INF/forgestuck/enumextensions-zzmod.json");
            assertNotNull(json);
            String content = new String(jar.getInputStream(json).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(content.contains(LegacyEnumExtensionPlanner.LEGACY_SPAWN_PLACEMENT_TYPE), content);
            assertTrue(content.contains("CUSTOM_PLACEMENT"), content);
            String metadata = new String(jar.getInputStream(jar.getJarEntry("META-INF/neoforge.mods.toml"))
                    .readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(metadata.contains("enumExtensions") && metadata.contains("enumextensions-zzmod.json"), metadata);
        }
    }
}
