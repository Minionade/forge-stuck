package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.Lifecycle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

/**
 * Synthetic Forge 1.20.1-style mod in an unrelated namespace: transformed with the default owner table, loaded in a
 * fresh classloader and driven through a real NeoForge event bus and {@link RegisterEvent}.
 */
class RegistrationLifecycleTest {
    private static final String FR = "net/minecraftforge/registries/";
    private static final String FEB = "net/minecraftforge/eventbus/api/IEventBus";

    @BeforeAll
    static void bootstrap() {
        net.neoforged.forgestuck.testsupport.TestBootstrap.init();
    }

    private static final class Loader extends ClassLoader {
        Loader() {
            super(net.minecraft.world.flag.FeatureFlagRegistry.class.getClassLoader());
        }

        Class<?> define(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

    private static byte[] supplier() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "zz/unrelated/Sup", null, "java/lang/Object",
                new String[] {"java/util/function/Supplier"});
        var c = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD, 0);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        c.visitInsn(Opcodes.RETURN);
        c.visitMaxs(0, 0);
        c.visitEnd();
        var g = cw.visitMethod(Opcodes.ACC_PUBLIC, "get", "()Ljava/lang/Object;", null, null);
        g.visitCode();
        g.visitLdcInsn("zzmod");
        g.visitLdcInsn("gizmo");
        g.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/resources/ResourceLocation", "fromNamespaceAndPath",
                "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;", false);
        g.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/sounds/SoundEvent", "createVariableRangeEvent",
                "(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/sounds/SoundEvent;", false);
        g.visitInsn(Opcodes.ARETURN);
        g.visitMaxs(0, 0);
        g.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] forgeMod() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "zz/unrelated/Mod1", null, "java/lang/Object", null);
        var mod = cw.visitAnnotation("Lnet/minecraftforge/fml/common/Mod;", true);
        mod.visit("value", "zzmod");
        mod.visitEnd();
        var sub = cw.visitAnnotation("Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;", true);
        sub.visitEnum("bus", "Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber$Bus;", "FORGE");
        sub.visitEnd();
        var m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "init", "(L" + FEB + ";)L" + FR + "RegistryObject;",
                null, null);
        m.visitCode();
        m.visitFieldInsn(Opcodes.GETSTATIC, FR + "ForgeRegistries", "SOUND_EVENTS", "L" + FR + "IForgeRegistry;");
        m.visitLdcInsn("zzmod");
        m.visitMethodInsn(Opcodes.INVOKESTATIC, FR + "DeferredRegister", "create",
                "(L" + FR + "IForgeRegistry;Ljava/lang/String;)L" + FR + "DeferredRegister;", false);
        m.visitVarInsn(Opcodes.ASTORE, 1);
        m.visitVarInsn(Opcodes.ALOAD, 1);
        m.visitLdcInsn("gizmo");
        m.visitTypeInsn(Opcodes.NEW, "zz/unrelated/Sup");
        m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, "zz/unrelated/Sup", "<init>", "()V", false);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, FR + "DeferredRegister", "register",
                "(Ljava/lang/String;Ljava/util/function/Supplier;)L" + FR + "RegistryObject;", false);
        m.visitVarInsn(Opcodes.ASTORE, 2);
        m.visitVarInsn(Opcodes.ALOAD, 1);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, FR + "DeferredRegister", "register", "(L" + FEB + ";)V", false);
        m.visitVarInsn(Opcodes.ALOAD, 2);
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();

        var registry = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "legacyRegistry",
                "(L" + FR + "RegisterEvent;)L" + FR + "IForgeRegistry;", null, null);
        registry.visitCode();
        registry.visitVarInsn(Opcodes.ALOAD, 0);
        registry.visitMethodInsn(Opcodes.INVOKEVIRTUAL, FR + "RegisterEvent", "getForgeRegistry",
                "()L" + FR + "IForgeRegistry;", false);
        registry.visitInsn(Opcodes.ARETURN);
        registry.visitMaxs(0, 0);
        registry.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    void forgeStyleDeferredRegistrationProducesFinalRegisteredId() throws Exception {
        ForgeOwnerRemapStep step = new ForgeOwnerRemapStep();
        byte[] mod = step.apply("zz.unrelated.Mod1", forgeMod());

        ClassNode node = new ClassNode();
        new ClassReader(mod).accept(node, 0);
        assertTrue(node.visibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lnet/neoforged/fml/common/Mod;")),
                "@Mod must be remapped to the annotation FML scans for");
        AnnotationNode sub = node.visibleAnnotations.stream()
                .filter(a -> a.desc.equals("Lnet/neoforged/fml/common/EventBusSubscriber;")).findFirst().orElseThrow();
        assertEquals("GAME", ((String[]) sub.values.get(1))[1], "Forge bus FORGE maps to NeoForge GAME bus");

        Loader loader = new Loader();
        loader.define("zz.unrelated.Sup", supplier());
        Class<?> modClass = loader.define("zz.unrelated.Mod1", mod);

        Method init = java.util.Arrays.stream(modClass.getMethods()).filter(x -> x.getName().equals("init")).findFirst().orElseThrow();
        ClassLoader busLoader = init.getParameterTypes()[0].getClassLoader();
        Object builder = Class.forName("net.neoforged.bus.api.BusBuilder", true, busLoader).getMethod("builder").invoke(null);
        Object bus = builder.getClass().getMethod("build").invoke(builder);
        Object registryObject = init.invoke(null, bus);
                Class<?> rl = Class.forName("net.minecraft.resources.ResourceLocation", true, busLoader);
        Object id = rl.getMethod("fromNamespaceAndPath", String.class, String.class).invoke(null, "zzmod", "gizmo");
        assertEquals(id, registryObject.getClass().getMethod("getId").invoke(registryObject));

        Class<?> regs = Class.forName("net.minecraft.core.registries.Registries", true, busLoader);
        Object itemKey = regs.getField("SOUND_EVENT").get(null);
        Class<?> lifecycle = Class.forName("com.mojang.serialization.Lifecycle", true, busLoader);
        Class<?> keyClass = Class.forName("net.minecraft.resources.ResourceKey", true, busLoader);
        Class<?> mapped = Class.forName("net.minecraft.core.MappedRegistry", true, busLoader);
        Object registry = mapped.getConstructor(keyClass, lifecycle)
                .newInstance(itemKey, lifecycle.getMethod("stable").invoke(null));
        Class<?> regClass = Class.forName("net.minecraft.core.Registry", true, busLoader);
        Constructor<?> ctor = Class.forName("net.neoforged.neoforge.registries.RegisterEvent", true, busLoader)
                .getDeclaredConstructor(keyClass, regClass);
        ctor.setAccessible(true);
        Object event = ctor.newInstance(itemKey, registry);
        Object legacyRegistry = modClass.getMethod("legacyRegistry", event.getClass()).invoke(null, event);
        Class<?> legacyRegistryApi = Class.forName("net.neoforged.forgestuck.compat.IForgeRegistry", true,
                legacyRegistry.getClass().getClassLoader());
        assertEquals(itemKey, legacyRegistryApi.getMethod("getRegistryKey").invoke(legacyRegistry));
        bus.getClass().getMethod("post", Class.forName("net.neoforged.bus.api.Event", true, busLoader)).invoke(bus, event);

        assertEquals(true, legacyRegistryApi.getMethod("containsKey", rl).invoke(legacyRegistry, id));
        assertEquals(true, regClass.getMethod("containsKey", rl).invoke(registry, id),
                "id must be registered exactly as <modid>:<name>");
        assertEquals(1, regClass.getMethod("size").invoke(registry));
    }
    @Test
    void nativeAnnotationsAreNotTouchedWhenNoForgeOwnersPresent() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "zz/unrelated/Native", null, "java/lang/Object", null);
        cw.visitEnd();
        byte[] in = cw.toByteArray();
        assertSame(in, new ForgeOwnerRemapStep().apply("zz.unrelated.Native", in));
    }
}
