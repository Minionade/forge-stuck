package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import net.neoforged.forgestuck.locator.StartupDisplay;
import net.neoforged.forgestuck.locator.StartupProgress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompatRelocationTest {
    @Test
    void classListCoversEveryCompatSourceAndResource() throws Exception {
        List<String> listed = CompatRelocation.compatClassNames(getClass().getClassLoader());
        for (String n : listed) {
            assertTrue(getClass().getClassLoader().getResource(CompatRelocation.FROM + n + ".class") != null, n);
        }
        // anonymous/nested classes of listed top-level classes must be listed too
        Path compatClasses = Path.of(net.neoforged.forgestuck.compat.UnsupportedForgeApi.class
                .getProtectionDomain().getCodeSource().getLocation().toURI())
                .resolve(CompatRelocation.FROM + "UnsupportedForgeApi.class")
                .getParent();
        try (Stream<Path> cls = Files.list(compatClasses)) {
            for (Path p : cls.toList()) {
                String n = p.getFileName().toString().replace(".class", "");
                if (n.endsWith("Test")) continue;
                assertTrue(listed.contains(n), "compat-classes.txt misses " + n);
            }
        }
    }

    @Test
    void runtimeJarIsRelocatedDeterministicAndLinks(@TempDir Path dir) throws Exception {
        ClassLoader app = getClass().getClassLoader();
        Path jar = CompatRelocation.writeRuntimeJar(dir, app);
        assertEquals(jar, CompatRelocation.writeRuntimeJar(dir, app));
        byte[] first = Files.readAllBytes(jar);
        Files.delete(jar);
        assertEquals(jar, CompatRelocation.writeRuntimeJar(dir, app));
        assertTrue(java.util.Arrays.equals(first, Files.readAllBytes(jar)), "jar bytes are deterministic");

        try (JarFile jf = new JarFile(jar.toFile())) {
            assertEquals("GAMELIBRARY", jf.getManifest().getMainAttributes().getValue("FMLModType"));
            assertEquals(CompatRelocation.MODULE_NAME, jf.getManifest().getMainAttributes().getValue("Automatic-Module-Name"));
            for (var e : java.util.Collections.list(jf.entries())) {
                if (!e.getName().endsWith(".class")) continue;
                assertTrue(e.getName().startsWith(CompatRelocation.TO), e.getName());
                String body = new String(jf.getInputStream(e).readAllBytes(), StandardCharsets.ISO_8859_1);
                assertFalse(body.contains(CompatRelocation.FROM), e.getName());
            }
            assertTrue(jf.getEntry(CompatRelocation.TO + "LegacyContainerFactory.class") != null);
            assertTrue(jf.getEntry(CompatRelocation.TO + "LegacyForgeMenuType.class") != null);
            assertTrue(jf.getEntry(CompatRelocation.TO + "LegacyIngredientSerializer.class") != null);
            assertTrue(jf.getEntry(CompatRelocation.TO + "LegacyAbstractIngredient.class") != null);
            assertTrue(jf.getEntry(CompatRelocation.TO + "LegacyIngredientTypes$1.class") != null);
            assertTrue(jf.getEntry(CompatRelocation.TO + "LegacyCriteriaTriggers.class") != null);
            assertTrue(jf.getEntry(CompatRelocation.TO + "LegacyMobEffectInstance.class") != null);
            assertTrue(jf.getEntry(CompatRelocation.TO + "LegacyDamageSource.class") != null);
        }
        try (URLClassLoader l = new URLClassLoader(new URL[] {jar.toUri().toURL()}, app)) {
            Class<?> c = Class.forName("net.neoforged.forgestuck.gamecompat.UnsupportedForgeApi", true, l);
            assertEquals("net.neoforged.forgestuck.gamecompat.UnsupportedForgeApi", c.getName());
            var m = c.getMethod("fail", String.class, String.class);
            var ex = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> m.invoke(null, "X.y", "Z.z"));
            assertTrue(ex.getCause() instanceof UnsupportedOperationException);
            // the relocated copy is a distinct class from the service-layer original
            assertTrue(c != net.neoforged.forgestuck.compat.UnsupportedForgeApi.class);
        }
    }

    @Test
    void runtimeTransformerPointsModsAtRelocatedCompat(@TempDir Path dir) throws Exception {
        var files = new LinkedHashMap<String, byte[]>();
        files.put("com/ex/Ok.class", ForgeOwnerRemapTest.caller("com/ex/Ok", false, "get", "()Ljava/lang/Object;"));
        files.put("com/ex/Bad.class", ForgeOwnerRemapTest.caller("com/ex/Bad", false, "noSuchMember", "()Z"));
        Path out = dir.resolve("out.jar");
        JarTransformer.forRuntime().transform(SrgCompatTest.jar(dir, "in.jar", files), out);
        for (String c : List.of("com/ex/Ok.class", "com/ex/Bad.class")) {
            String body = new String(SrgCompatTest.read(out, c), StandardCharsets.ISO_8859_1);
            assertFalse(body.contains(CompatRelocation.FROM), c);
            assertTrue(body.contains(CompatRelocation.TO), c);
        }
        assertTrue(new String(SrgCompatTest.read(out, "com/ex/Bad.class"), StandardCharsets.ISO_8859_1)
                .contains(CompatRelocation.TO + "UnsupportedForgeApi"));
    }

    @Test
    void compatModuleReadsMinecraftInTheSameLayerButNotAcrossAnIsolatedLayer(@TempDir Path dir) throws Exception {
        Path mods = Files.createDirectories(dir.resolve("game"));
        Path jar = CompatRelocation.writeRuntimeJar(mods, getClass().getClassLoader());
        writeFakeMinecraft(mods.resolve("minecraft.jar"));

        // GAMELIBRARY: FML puts it in the game layer together with minecraft.
        var finder = java.lang.module.ModuleFinder.of(jar, mods.resolve("minecraft.jar"));
        var cfg = java.lang.ModuleLayer.boot().configuration().resolve(finder,
                java.lang.module.ModuleFinder.of(), java.util.Set.of(CompatRelocation.MODULE_NAME, "minecraft"));
        var layer = java.lang.ModuleLayer.defineModulesWithOneLoader(cfg, List.of(java.lang.ModuleLayer.boot()),
                ClassLoader.getSystemClassLoader()).layer();
        Module compat = layer.findModule(CompatRelocation.MODULE_NAME).orElseThrow();
        Module mc = layer.findModule("minecraft").orElseThrow();
        assertTrue(compat.canRead(mc));

        // LIBRARY (the previous manifest): a separate earlier layer that cannot see minecraft at all.
        var plugin = java.lang.module.ModuleFinder.of(jar);
        var pcfg = java.lang.ModuleLayer.boot().configuration().resolve(plugin,
                java.lang.module.ModuleFinder.of(), java.util.Set.of(CompatRelocation.MODULE_NAME));
        var pLayer = java.lang.ModuleLayer.defineModulesWithOneLoader(pcfg, List.of(java.lang.ModuleLayer.boot()),
                ClassLoader.getSystemClassLoader()).layer();
        assertFalse(pLayer.findModule("minecraft").isPresent());
        assertFalse(pLayer.findModule(CompatRelocation.MODULE_NAME).orElseThrow().canRead(mc));
    }

    private static void writeFakeMinecraft(Path jar) throws Exception {
        var cw = new org.objectweb.asm.ClassWriter(0);
        cw.visit(org.objectweb.asm.Opcodes.V21, org.objectweb.asm.Opcodes.ACC_MODULE, "module-info", null, null, null);
        var mv = cw.visitModule("minecraft", 0, null);
        mv.visitRequire("java.base", org.objectweb.asm.Opcodes.ACC_MANDATED, null);
        mv.visitExport("net/minecraft/core/registries", 0);
        mv.visitEnd();
        cw.visitEnd();
        var holder = new org.objectweb.asm.ClassWriter(0);
        holder.visit(org.objectweb.asm.Opcodes.V21, org.objectweb.asm.Opcodes.ACC_PUBLIC,
                "net/minecraft/core/registries/Registries", null, "java/lang/Object", null);
        holder.visitEnd();
        try (var out = new java.util.jar.JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new java.util.jar.JarEntry("module-info.class"));
            out.write(cw.toByteArray());
            out.closeEntry();
            out.putNextEntry(new java.util.jar.JarEntry("net/minecraft/core/registries/Registries.class"));
            out.write(holder.toByteArray());
            out.closeEntry();
        }
    }

    @Test
    void progressEventsAreOrderedAndCached(@TempDir Path dir) throws Exception {
        Path src = NestedJarTest.outer(dir, "a.jar", java.util.Map.of());
        Path cache = dir.resolve("cache");
        List<String> log = new ArrayList<>();
        StartupDisplay display = new StartupDisplay() {
            public void begin(String title, int total) {
                log.add("begin " + title + " " + total);
            }

            public void update(int position, String text) {
                log.add("update " + position + " " + text);
            }

            public void message(String text) {
                log.add("msg " + text);
            }

            public void end(String text) {
                log.add("end " + text.replaceAll("\\d+ ms", "N ms"));
            }
        };
        StartupProgress progress = new StartupProgress(display, 1);
        JarTransformer t = JarTransformer.withDefaultSteps().withProgress(progress);
        progress.begin();
        progress.next("a.jar");
        assertFalse(t.transformCached(src, cache).cacheHit());
        progress.end();
        assertEquals("begin Forge Stuck 1", log.get(0));
        assertEquals("msg Forge Stuck: scanning 1 mod jar(s)", log.get(1));
        assertEquals("update 0 Forge Stuck: checking a.jar (1/1)", log.get(2));
        assertEquals("update 0 Forge Stuck: transforming a.jar (1/1)", log.get(3));
        assertTrue(log.get(4).startsWith("msg Forge Stuck: transformed a.jar in "), log.get(4));
        assertEquals("end Forge Stuck: done (1 transformed, 0 cached, N ms)", log.get(5));

        log.clear();
        StartupProgress second = new StartupProgress(display, 1);
        t.withProgress(second);
        second.begin();
        second.next("a.jar");
        assertTrue(t.transformCached(src, cache).cacheHit());
        second.end();
        assertEquals("update 0 Forge Stuck: using cached a.jar (1/1)", log.get(3));
        assertEquals("end Forge Stuck: done (0 transformed, 1 cached, N ms)", log.get(4));
    }

    @Test
    void nestedJarsAreAnnounced(@TempDir Path dir) throws Exception {
        byte[] nested = NestedJarTest.jar(java.util.Map.of("META-INF/mods.toml", NestedJarTest.legacy("lib_mod")));
        Path src = NestedJarTest.outer(dir, "o.jar", java.util.Map.of(NestedJarTest.PATH, nested));
        List<String> events = new ArrayList<>();
        JarTransformer t = JarTransformer.withDefaultSteps().withProgress(new net.neoforged.forgestuck.transformer.jar.TransformProgress() {
            public void nested(String outer, String entry) {
                events.add(outer + "|" + entry);
            }
        });
        t.transformCached(src, dir.resolve("c"));
        assertEquals(List.of("o.jar|" + NestedJarTest.PATH), events);
    }

    @Test
    void fmlDisplayNeverThrowsOutsideAnEarlyWindow() {
        var d = new net.neoforged.forgestuck.locator.FmlStartupDisplay();
        d.begin("Forge Stuck", 2);
        d.update(0, "x");
        d.message("y");
        d.end("z");
    }
}
