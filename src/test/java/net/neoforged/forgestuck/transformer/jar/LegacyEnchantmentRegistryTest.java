package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.forgestuck.compat.LegacyEnchantmentRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class LegacyEnchantmentRegistryTest {
    @Test
    void keyNeedsNoAccessAndLookupsFailPerCallWithoutServer() {
        Registry<?> r = LegacyEnchantmentRegistry.ENCHANTMENT;
        assertEquals(Registries.ENCHANTMENT, r.key());
        var ex = assertThrows(UnsupportedOperationException.class,
                () -> r.containsKey(ResourceLocation.withDefaultNamespace("sharpness")));
        assertTrue(ex.getMessage().contains("RegistryAccess"), ex.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void callsForwardToTheLiveRegistryWhenAnAccessExists() throws Exception {
        Registry<?> live = (Registry<?>) Proxy.newProxyInstance(Registry.class.getClassLoader(),
                new Class<?>[] {Registry.class}, (p, m, a) -> switch (m.getName()) {
                    case "containsKey" -> a[0] instanceof ResourceLocation rl && rl.getPath().equals("sharpness");
                    default -> throw new UnsupportedOperationException(m.getName());
                });
        RegistryAccess access = (RegistryAccess) Proxy.newProxyInstance(RegistryAccess.class.getClassLoader(),
                new Class<?>[] {RegistryAccess.class}, (p, m, a) -> {
                    if (m.getName().equals("registry")) return Optional.of(live);
                    throw new UnsupportedOperationException(m.getName());
                });
        var create = LegacyEnchantmentRegistry.class.getDeclaredMethod("create", java.util.function.Supplier.class);
        create.setAccessible(true);
        Registry<?> r = (Registry<?>) create.invoke(null, (java.util.function.Supplier<Optional<RegistryAccess>>) () -> Optional.of(access));
        assertTrue(r.containsKey(ResourceLocation.withDefaultNamespace("sharpness")));
        assertFalse(r.containsKey(ResourceLocation.withDefaultNamespace("nothing")));
    }

    @Test
    void srgReferenceToRemovedStaticRegistryIsRedirectedToTheLiveView(@TempDir java.nio.file.Path dir) throws Exception {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/Ench", null, "java/lang/Object", null);
        var m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "go", "()Ljava/lang/Object;", null, null);
        m.visitCode();
        m.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/core/registries/BuiltInRegistries", "f_256876_",
                "Lnet/minecraft/core/Registry;");
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();
        var files = new LinkedHashMap<String, byte[]>();
        files.put("com/ex/Ench.class", cw.toByteArray());
        java.nio.file.Path out = dir.resolve("out.jar");
        JarTransformer.withDefaultSteps().transform(SrgCompatTest.jar(dir, "in.jar", files), out);
        byte[] b = SrgCompatTest.read(out, "com/ex/Ench.class");
        assertEquals(java.util.List.of(), SrgResidualTest.residualSrgRefs(b));
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() {
                return defineClass("com.ex.Ench", b, 0, b.length);
            }
        };
        Class<?> c = (Class<?>) loader.getClass().getDeclaredMethod("define").invoke(loader);
        Registry<?> r = (Registry<?>) c.getMethod("go").invoke(null);
        assertEquals(Registries.ENCHANTMENT, r.key());
    }
}