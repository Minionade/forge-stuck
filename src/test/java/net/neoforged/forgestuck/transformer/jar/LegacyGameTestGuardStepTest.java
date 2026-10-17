package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import net.neoforged.forgestuck.compat.LegacyGameTestSupport;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;

class LegacyGameTestGuardStepTest {
    private static final String EVENT = "net/neoforged/neoforge/event/RegisterGameTestsEvent";
    private static final String ENV = "net/neoforged/fml/loading/FMLEnvironment";
    private final LegacyGameTestGuardStep step = new LegacyGameTestGuardStep();

    static byte[] hook(String event, boolean annotated, String testClass) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/Hook", null, "java/lang/Object", null);
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "calls", "I", null, null).visitEnd();
        var mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "register",
                "(L" + event + ";)V", null, new String[] {"java/lang/ClassNotFoundException"});
        if (annotated) mv.visitAnnotation(event.startsWith("net/minecraftforge/")
                ? "Lnet/minecraftforge/eventbus/api/SubscribeEvent;"
                : "Lnet/neoforged/bus/api/SubscribeEvent;", true).visitEnd();
        mv.visitCode();
        mv.visitFieldInsn(Opcodes.GETSTATIC, "example/Hook", "calls", "I");
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IADD);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, "example/Hook", "calls", "I");
        // Retain an original branch/frame to exercise verification after inserting the entry guard.
        Label lookup = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitJumpInsn(Opcodes.IFNULL, lookup);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitLabel(lookup);
        mv.visitLdcInsn(testClass);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Class", "forName",
                "(Ljava/lang/String;)Ljava/lang/Class;", false);
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] simpleClass(String name, boolean environment) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        if (environment) cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "production", "Z", null, null).visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private ClassLoader loader(String event, String testClass) {
        Map<String, byte[]> classes = Map.of(
                "example.Hook", step.apply("example.Hook", hook(event, true, testClass)),
                event.replace('/', '.'), simpleClass(event, false),
                ENV.replace('/', '.'), simpleClass(ENV, true),
                "example.gametest.Present", simpleClass("example/gametest/Present", false));
        return new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                byte[] bytes = classes.get(name);
                if (bytes == null) return super.loadClass(name, resolve);
                Class<?> type = findLoadedClass(name);
                if (type == null) type = defineClass(name, bytes, 0, bytes.length);
                if (resolve) resolveClass(type);
                return type;
            }
        };
    }

    @Test
    void skipsOnlyMissingDevTestsAndPreservesProductionFailure() throws Exception {
        for (String event : new String[] {EVENT, "net/minecraftforge/event/RegisterGameTestsEvent"}) {
            ClassLoader loader = loader(event, "example.gametest.Missing");
            Class<?> owner = loader.loadClass("example.Hook");
            var register = owner.getMethod("register", loader.loadClass(event.replace('/', '.')));
            register.invoke(null, new Object[] {null});
            assertEquals(0, owner.getField("calls").getInt(null));
            loader.loadClass(ENV.replace('/', '.')).getField("production").setBoolean(null, true);
            InvocationTargetException error = assertThrows(InvocationTargetException.class,
                    () -> register.invoke(null, new Object[] {null}));
            assertInstanceOf(ClassNotFoundException.class, error.getCause());
            assertEquals(1, owner.getField("calls").getInt(null));
        }
    }

    @Test
    void presentDevTestsStillRegister() throws Exception {
        ClassLoader loader = loader(EVENT, "example.gametest.Present");
        Class<?> owner = loader.loadClass("example.Hook");
        owner.getMethod("register", loader.loadClass(EVENT.replace('/', '.')))
                .invoke(null, new Object[] {null});
        assertEquals(1, owner.getField("calls").getInt(null));
    }

    @Test
    void unrelatedMethodsAndDependenciesAreUntouched() {
        for (byte[] bytes : new byte[][] {
                hook(EVENT, false, "example.gametest.Missing"),
                hook("example/OtherEvent", true, "example.gametest.Missing"),
                hook(EVENT, true, "example.MissingDependency")}) {
            assertSame(bytes, step.apply("example.Hook", bytes));
        }
    }

    @Test
    void presenceCheckDoesNotInitializeClassesOrSwallowLinkageErrors() {
        assertFalse(LegacyGameTestSupport.shouldSkip(false, getClass(), Uninitialized.class.getName()));
        assertFalse(LegacyGameTestSupport.shouldSkip(true, getClass(), "example.gametest.Missing"));
        ClassLoader broken = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("example.gametest.Broken")) throw new NoClassDefFoundError("missing dependency");
                return super.loadClass(name, resolve);
            }
        };
        // Use a class defined by the failing loader as the lookup owner.
        byte[] bytes = simpleClass("example/Owner", false);
        Class<?> owner = new ClassLoader(broken) {
            Class<?> defineOwner() { return defineClass("example.Owner", bytes, 0, bytes.length); }
        }.defineOwner();
        assertThrows(NoClassDefFoundError.class,
                () -> LegacyGameTestSupport.shouldSkip(false, owner, "example.gametest.Broken"));
    }

    private static class Uninitialized {
        static {
            if (true) throw new AssertionError("Presence checks must not initialize gametests");
        }
    }
}
