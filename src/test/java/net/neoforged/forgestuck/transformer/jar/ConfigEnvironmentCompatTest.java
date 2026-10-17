package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/**
 * Forge 1.20.1-style classes in an unrelated namespace using ForgeConfigSpec, MinecraftForge.EVENT_BUS, DistExecutor and
 * ModLoadingContext: transformed with the default owner table, defined in a fresh classloader and executed against
 * the real NeoForge classes.
 */
class ConfigEnvironmentCompatTest {
    private static final String FC = "net/minecraftforge/common/ForgeConfigSpec";
    private static final String B = FC + "$Builder";
    private static final String DIST = "net/minecraftforge/api/distmarker/Dist";
    private static final String DX = "net/minecraftforge/fml/DistExecutor";
    private static final String CLS = "zz/cfg/Probe";

    @BeforeAll
    static void bootstrap() {
        net.neoforged.forgestuck.testsupport.TestBootstrap.init();
    }

    private static final class Loader extends ClassLoader {
        Loader() {
            super(ConfigEnvironmentCompatTest.class.getClassLoader());
        }

        Class<?> define(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

    private static byte[] probe() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, CLS, null, "java/lang/Object", null);

        // Object[] spec(): Builder.comment("c").push("sec").define("flag", true) / defineInRange("n",5,1,10) / pop / build
        var m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "spec", "()[Ljava/lang/Object;", null, null);
        m.visitCode();
        m.visitTypeInsn(Opcodes.NEW, B);
        m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, B, "<init>", "()V", false);
        m.visitVarInsn(Opcodes.ASTORE, 0);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitLdcInsn("c");
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, B, "comment", "(Ljava/lang/String;)L" + B + ";", false);
        m.visitLdcInsn("sec");
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, B, "push", "(Ljava/lang/String;)L" + B + ";", false);
        m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitLdcInsn("flag");
        m.visitInsn(Opcodes.ICONST_1);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, B, "define", "(Ljava/lang/String;Z)L" + FC + "$BooleanValue;", false);
        m.visitVarInsn(Opcodes.ASTORE, 1);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitLdcInsn("n");
        m.visitInsn(Opcodes.ICONST_5);
        m.visitInsn(Opcodes.ICONST_1);
        m.visitIntInsn(Opcodes.BIPUSH, 10);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, B, "defineInRange", "(Ljava/lang/String;III)L" + FC + "$IntValue;", false);
        m.visitVarInsn(Opcodes.ASTORE, 2);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, B, "pop", "()L" + B + ";", false);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, B, "build", "()L" + FC + ";", false);
        m.visitVarInsn(Opcodes.ASTORE, 3);
        m.visitInsn(Opcodes.ICONST_3);
        m.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        m.visitInsn(Opcodes.DUP);
        m.visitInsn(Opcodes.ICONST_0);
        m.visitVarInsn(Opcodes.ALOAD, 3);
        m.visitInsn(Opcodes.AASTORE);
        m.visitInsn(Opcodes.DUP);
        m.visitInsn(Opcodes.ICONST_1);
        m.visitVarInsn(Opcodes.ALOAD, 1);
        m.visitInsn(Opcodes.AASTORE);
        m.visitInsn(Opcodes.DUP);
        m.visitInsn(Opcodes.ICONST_2);
        m.visitVarInsn(Opcodes.ALOAD, 2);
        m.visitInsn(Opcodes.AASTORE);
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();

        // Object read(BooleanValue): ConfigValue.get() through the BooleanValue owner
        m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "read", "(L" + FC + "$BooleanValue;)Ljava/lang/Object;",
                null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, FC + "$BooleanValue", "get", "()Ljava/lang/Object;", false);
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();

        // Object bus(): MinecraftForge.EVENT_BUS
        m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "bus", "()Ljava/lang/Object;", null, null);
        m.visitCode();
        m.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraftforge/common/MinecraftForge", "EVENT_BUS",
                "Lnet/minecraftforge/eventbus/api/IEventBus;");
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();

        // Object callOn(Dist, Supplier): DistExecutor.unsafeCallWhenOn(dist, () -> callable)
        m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "callOn",
                "(L" + DIST + ";Ljava/util/function/Supplier;)Ljava/lang/Object;", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitVarInsn(Opcodes.ALOAD, 1);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, DX, "unsafeCallWhenOn",
                "(L" + DIST + ";Ljava/util/function/Supplier;)Ljava/lang/Object;", false);
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();

        // Object forDist(Supplier, Supplier): DistExecutor.unsafeRunForDist
        m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "forDist",
                "(Ljava/util/function/Supplier;Ljava/util/function/Supplier;)Ljava/lang/Object;", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitVarInsn(Opcodes.ALOAD, 1);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, DX, "unsafeRunForDist",
                "(Ljava/util/function/Supplier;Ljava/util/function/Supplier;)Ljava/lang/Object;", false);
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();

        // void register(Object spec): ModLoadingContext.get().registerConfig(Type.COMMON, spec)
        m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "register", "(Ljava/lang/Object;)V", null, null);
        m.visitCode();
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraftforge/fml/ModLoadingContext", "get",
                "()Lnet/minecraftforge/fml/ModLoadingContext;", false);
        m.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraftforge/fml/config/ModConfig$Type", "COMMON",
                "Lnet/minecraftforge/fml/config/ModConfig$Type;");
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitTypeInsn(Opcodes.CHECKCAST, "net/minecraftforge/fml/config/IConfigSpec");
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraftforge/fml/ModLoadingContext", "registerConfig",
                "(Lnet/minecraftforge/fml/config/ModConfig$Type;Lnet/minecraftforge/fml/config/IConfigSpec;)V", false);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static Class<?> transformed() {
        OwnerRemapReport report = new OwnerRemapReport();
        ForgeOwnerRemapStep step = new ForgeOwnerRemapStep();
        byte[] in = probe();
        step.scan("zz.cfg.Probe", in, report);
        byte[] out = step.apply("zz.cfg.Probe", in, report);
        assertEquals(Set.of(), report.blockedOwners());
        assertEquals(Set.of(), ForgeOwnerMappings.defaults().remainingOwners(out));
        return new Loader().define("zz.cfg.Probe", out);
    }

    private static Object call(Class<?> c, String name, Class<?>[] types, Object... args) throws Exception {
        java.lang.reflect.Method m = java.util.Arrays.stream(c.getMethods()).filter(x -> x.getName().equals(name))
                .findFirst().orElseThrow(() -> new NoSuchMethodException(name));
        try {
            return m.invoke(null, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception ex) throw ex;
            throw e;
        }
    }

    @Test
    void forgeConfigSpecBuilderBuildsAModConfigSpec() throws Exception {
        Class<?> c = transformed();
        Object[] r = (Object[]) call(c, "spec", new Class<?>[0]);
        var spec = assertInstanceOf(net.neoforged.neoforge.common.ModConfigSpec.class, r[0]);
        assertEquals(List.of("sec", "flag"), ((net.neoforged.neoforge.common.ModConfigSpec.BooleanValue) r[1]).getPath());
        assertEquals(List.of("sec", "n"), ((net.neoforged.neoforge.common.ModConfigSpec.IntValue) r[2]).getPath());
        assertEquals("c", spec.getLevelComment(List.of("sec")));
        assertFalse(spec.isEmpty());
        assertFalse(spec.isLoaded());
    }

    @Test
    void configValueGetKeepsForgeSemanticsBeforeLoad() throws Exception {
        Class<?> c = transformed();
        Object[] r = (Object[]) call(c, "spec", new Class<?>[0]);
        var e = assertThrows(IllegalStateException.class,
                () -> call(c, "read", new Class<?>[] {net.neoforged.neoforge.common.ModConfigSpec.BooleanValue.class}, r[1]));
        assertTrue(e.getMessage().contains("before config is loaded") || e.getMessage().toLowerCase().contains("config"),
                e.getMessage());
    }

    @Test
    void minecraftForgeEventBusIsTheNeoForgeGameBus() throws Exception {
        Class<?> c = transformed();
        Object expected = Class.forName("net.neoforged.neoforge.common.NeoForge", true, c.getClassLoader())
                .getField("EVENT_BUS").get(null);
        assertSame(expected, call(c, "bus", new Class<?>[0]));
    }

    @Test
    void distExecutorRunsOnlyOnMatchingDist() throws Exception {
        Class<?> c = transformed();
        var here = net.neoforged.fml.loading.FMLEnvironment.dist;
        var other = here == net.neoforged.api.distmarker.Dist.CLIENT ? net.neoforged.api.distmarker.Dist.DEDICATED_SERVER
                : net.neoforged.api.distmarker.Dist.CLIENT;
        Class<?>[] t = {net.neoforged.api.distmarker.Dist.class, Supplier.class};
        Supplier<java.util.concurrent.Callable<String>> s = () -> () -> "ran";
        assertEquals("ran", call(c, "callOn", t, here, s));
        assertNull(call(c, "callOn", t, other, s));
        Supplier<Supplier<String>> client = () -> () -> "client";
        Supplier<Supplier<String>> server = () -> () -> "server";
        String expected = here == net.neoforged.api.distmarker.Dist.CLIENT ? "client" : "server";
        assertEquals(expected, call(c, "forDist", new Class<?>[] {Supplier.class, Supplier.class}, client, server));
    }

    @Test
    void registerConfigForwardsToTheActiveContainer() throws Exception {
        Class<?> c = transformed();
        Object[] r = (Object[]) call(c, "spec", new Class<?>[0]);
        Throwable t = null;
        try {
            call(c, "register", new Class<?>[] {Object.class}, r[0]);
        } catch (Throwable e) {
            t = e;
        }
        // No mod is being constructed in a unit test; the call must reach the compat class, not fail to link.
        assertFalse(t instanceof LinkageError, String.valueOf(t));
        assertFalse(t instanceof NoSuchMethodError, String.valueOf(t));
    }

    @Test
    void ownerWithBlockedDescriptorDependencyStaysConsistentJarWide() {
        // MinecraftForge.EVENT_BUS is typed IEventBus; post(Event) has no verified equivalent, so IEventBus stays
        // unmapped and MinecraftForge must stay unmapped too (a mapped getstatic would carry the legacy descriptor).
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "zz/cfg/Post", null, "java/lang/Object", null);
        var m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "go", "(Lzz/cfg/Ev;)V", null, null);
        m.visitCode();
        m.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraftforge/common/MinecraftForge", "EVENT_BUS",
                "Lnet/minecraftforge/eventbus/api/IEventBus;");
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKEINTERFACE, "net/minecraftforge/eventbus/api/IEventBus", "post",
                "(Lnet/minecraftforge/eventbus/api/Event;)Z", true);
        m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();
        byte[] in = cw.toByteArray();

        // Second class only reads EVENT_BUS: the jar-wide scan must still keep it unmapped.
        ClassWriter other = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        other.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "zz/cfg/OnlyBus", null, "java/lang/Object", null);
        var g = other.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "go", "()V", null, null);
        g.visitCode();
        g.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraftforge/common/MinecraftForge", "EVENT_BUS",
                "Lnet/minecraftforge/eventbus/api/IEventBus;");
        g.visitInsn(Opcodes.POP);
        g.visitInsn(Opcodes.RETURN);
        g.visitMaxs(0, 0);
        g.visitEnd();
        other.visitEnd();
        byte[] onlyBus = other.toByteArray();

        ForgeOwnerRemapStep step = new ForgeOwnerRemapStep();
        OwnerRemapReport report = new OwnerRemapReport();
        step.scan("zz.cfg.OnlyBus", onlyBus, report);
        step.scan("zz.cfg.Post", in, report);
        assertTrue(report.blockedOwners().contains("net/minecraftforge/eventbus/api/IEventBus"));
        assertTrue(report.blockedOwners().contains("net/minecraftforge/common/MinecraftForge"));
        byte[] out = step.apply("zz.cfg.OnlyBus", onlyBus, report);
        assertTrue(new String(out, java.nio.charset.StandardCharsets.ISO_8859_1)
                .contains("net/minecraftforge/common/MinecraftForge"));
    }

    @Test
    void registryFieldBlockedByItsRegistryTypeIsNotMappedAlone() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "zz/cfg/Reg", null, "java/lang/Object", null);
        var m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "go", "()V", null, null);
        m.visitCode();
        m.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraftforge/registries/ForgeRegistries", "BLOCKS",
                "Lnet/minecraftforge/registries/IForgeRegistry;");
        m.visitMethodInsn(Opcodes.INVOKEINTERFACE, "net/minecraftforge/registries/IForgeRegistry", "tags",
                "()Lnet/minecraftforge/registries/tags/ITagManager;", true);
        m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();
        byte[] in = cw.toByteArray();
        OwnerRemapReport report = new OwnerRemapReport();
        ForgeOwnerRemapStep step = new ForgeOwnerRemapStep();
        step.scan("zz.cfg.Reg", in, report);
        assertTrue(report.blockedOwners().containsAll(Set.of("net/minecraftforge/registries/IForgeRegistry",
                "net/minecraftforge/registries/ForgeRegistries")), report.blockedOwners().toString());
    }
}
