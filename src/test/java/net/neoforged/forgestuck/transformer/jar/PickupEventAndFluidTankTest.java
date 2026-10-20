package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import net.neoforged.forgestuck.transformer.jar.ForgeOwnerMappings;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.fluids.IFluidTank;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

class PickupEventAndFluidTankTest {
    private static final String EVENT = "net/minecraftforge/event/entity/player/EntityItemPickupEvent";
    private static final String TANK = "net/minecraftforge/fluids/IFluidTank";

    @Test
    void legacyPickupHandlerCancelsThroughNeoForgeEvent() throws Exception {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/Pickup", null, "java/lang/Object", null);
        var m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "on", "(L" + EVENT + ";Z)Z", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, EVENT, "getEntity",
                "()Lnet/minecraft/world/entity/player/Player;", false);
        m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, EVENT, "getItem",
                "()Lnet/minecraft/world/entity/item/ItemEntity;", false);
        m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitVarInsn(Opcodes.ILOAD, 1);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, EVENT, "setCanceled", "(Z)V", false);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, EVENT, "isCanceled", "()Z", false);
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        Class<?> type = load("com.ex.Pickup", w.toByteArray());
        var event = new ItemEntityPickupEvent.Pre(null, null);
        var on = type.getMethod("on", ItemEntityPickupEvent.Pre.class, boolean.class);
        assertEquals(true, on.invoke(null, event, true));
        assertEquals(TriState.FALSE, event.canPickup());
        assertEquals(false, on.invoke(null, event, false));
        assertEquals(TriState.DEFAULT, event.canPickup());
    }

    @Test
    void legacyFluidTankImplementationsLoadAgainstNeoForgeInterface() throws Exception {
        String stack = "Lnet/minecraftforge/fluids/FluidStack;";
        String action = "Lnet/minecraftforge/fluids/capability/IFluidHandler$FluidAction;";
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/Tank", null, "java/lang/Object", new String[] {TANK});
        var c = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD, 0);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        c.visitInsn(Opcodes.RETURN);
        c.visitMaxs(0, 0);
        c.visitEnd();
        tankMethod(w, "getFluid", "()" + stack, true);
        tankMethod(w, "getFluidAmount", "()I", false);
        tankMethod(w, "getCapacity", "()I", false);
        tankMethod(w, "isFluidValid", "(" + stack + ")Z", false);
        tankMethod(w, "fill", "(" + stack + action + ")I", false);
        tankMethod(w, "drain", "(I" + action + ")" + stack, true);
        tankMethod(w, "drain", "(" + stack + action + ")" + stack, true);
        w.visitEnd();
        Class<?> type = load("com.ex.Tank", w.toByteArray());
        assertInstanceOf(IFluidTank.class, type.getConstructor().newInstance());
    }

    @Test
    void legacyFluidTankNbtMethodsRoundTripThroughAdapter() throws Exception {
        net.neoforged.forgestuck.testsupport.TestBootstrap.init();
        String tankOwner = "net/minecraftforge/fluids/capability/templates/FluidTank";
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/TankNbt", null, "java/lang/Object", null);
        var write = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "write",
                "(L" + tankOwner + ";Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;", null, null);
        write.visitCode();
        write.visitVarInsn(Opcodes.ALOAD, 0);
        write.visitVarInsn(Opcodes.ALOAD, 1);
        write.visitMethodInsn(Opcodes.INVOKEVIRTUAL, tankOwner, "writeToNBT",
                "(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;", false);
        write.visitInsn(Opcodes.ARETURN);
        write.visitMaxs(0, 0);
        write.visitEnd();
        var read = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "read",
                "(L" + tankOwner + ";Lnet/minecraft/nbt/CompoundTag;)L" + tankOwner + ";", null, null);
        read.visitCode();
        read.visitVarInsn(Opcodes.ALOAD, 0);
        read.visitVarInsn(Opcodes.ALOAD, 1);
        read.visitMethodInsn(Opcodes.INVOKEVIRTUAL, tankOwner, "readFromNBT",
                "(Lnet/minecraft/nbt/CompoundTag;)L" + tankOwner + ";", false);
        read.visitInsn(Opcodes.ARETURN);
        read.visitMaxs(0, 0);
        read.visitEnd();
        w.visitEnd();

        Class<?> type = load("com.ex.TankNbt", w.toByteArray());
        FluidTank source = new FluidTank(1000);
        source.setFluid(new FluidStack(Fluids.WATER, 500));
        CompoundTag nbt = (CompoundTag) type.getMethod("write", FluidTank.class, CompoundTag.class)
                .invoke(null, source, new CompoundTag());
        assertTrue(nbt.contains("Fluid", CompoundTag.TAG_COMPOUND));

        FluidTank restored = new FluidTank(1000);
        assertSame(restored, type.getMethod("read", FluidTank.class, CompoundTag.class)
                .invoke(null, restored, nbt));
        assertEquals(500, restored.getFluidAmount());
        assertEquals(Fluids.WATER, restored.getFluid().getFluid());
    }

    @Test
    void legacyFluidTankNullSetFluidNormalizesToEmpty() {
        net.neoforged.forgestuck.testsupport.TestBootstrap.init();
        FluidTank tank = new FluidTank(1000);
        tank.setFluid(new FluidStack(Fluids.WATER, 250));

        net.neoforged.forgestuck.compat.LegacyFluidTank.setFluid(tank, null);

        assertTrue(tank.getFluid().isEmpty());
        assertEquals(0, tank.getFluidAmount());
    }

    @Test
    void remapsFluidTankSetFluidToNullSafeCompatibilityHelper() {
        String tankOwner = "net/neoforged/neoforge/fluids/capability/templates/FluidTank";
        String fluidStack = "net/neoforged/neoforge/fluids/FluidStack";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/FluidTankSetFluid", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "clear",
                "(L" + tankOwner + ";L" + fluidStack + ";)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, tankOwner, "setFluid",
                "(L" + fluidStack + ";)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new org.objectweb.asm.ClassReader(new SrgMemberRemapStep().apply(
                "sample.FluidTankSetFluid", writer.toByteArray())).accept(transformed, 0);
        var call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(org.objectweb.asm.tree.MethodInsnNode.class::isInstance)
                .map(org.objectweb.asm.tree.MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyFluidTank", call.owner);
        assertEquals("setFluid", call.name);
        assertEquals("(L" + tankOwner + ";L" + fluidStack + ";)V", call.desc);
    }

    @Test
    void rewritesFluidTankNbtCallsOnIndirectSubclasses() {
        String tankOwner = "net/minecraftforge/fluids/capability/templates/FluidTank";
        String baseClass = "sample/FluidTankBase";
        String subclass = "sample/FluidTankSubclass";
        ClassWriter baseWriter = new ClassWriter(0);
        baseWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, baseClass, null, tankOwner, null);
        baseWriter.visitEnd();
        ClassWriter subclassWriter = new ClassWriter(0);
        subclassWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, subclass, null, baseClass, null);
        subclassWriter.visitEnd();

        ClassWriter callerWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        callerWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/TankNbtCaller", null, "java/lang/Object", null);
        var write = callerWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "write",
                "(L" + subclass + ";Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;", null, null);
        write.visitCode();
        write.visitVarInsn(Opcodes.ALOAD, 0);
        write.visitVarInsn(Opcodes.ALOAD, 1);
        write.visitMethodInsn(Opcodes.INVOKEVIRTUAL, subclass, "writeToNBT",
                "(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;", false);
        write.visitInsn(Opcodes.ARETURN);
        write.visitMaxs(0, 0);
        write.visitEnd();
        var read = callerWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "read",
                "(L" + subclass + ";Lnet/minecraft/nbt/CompoundTag;)L" + tankOwner + ";", null, null);
        read.visitCode();
        read.visitVarInsn(Opcodes.ALOAD, 0);
        read.visitVarInsn(Opcodes.ALOAD, 1);
        read.visitMethodInsn(Opcodes.INVOKEVIRTUAL, subclass, "readFromNBT",
                "(Lnet/minecraft/nbt/CompoundTag;)L" + tankOwner + ";", false);
        read.visitInsn(Opcodes.ARETURN);
        read.visitMaxs(0, 0);
        read.visitEnd();
        callerWriter.visitEnd();

        ForgeOwnerRemapStep step = new ForgeOwnerRemapStep();
        step.scan("sample.FluidTankBase", baseWriter.toByteArray(), null);
        step.scan("sample.FluidTankSubclass", subclassWriter.toByteArray(), null);
        ClassNode transformed = new ClassNode();
        new org.objectweb.asm.ClassReader(step.apply("sample.TankNbtCaller", callerWriter.toByteArray()))
                .accept(transformed, 0);

        var calls = java.util.Arrays.stream(transformed.methods.toArray(new org.objectweb.asm.tree.MethodNode[0]))
                .flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                .filter(org.objectweb.asm.tree.MethodInsnNode.class::isInstance)
                .map(org.objectweb.asm.tree.MethodInsnNode.class::cast)
                .toList();
        assertEquals(2, calls.size());
        assertTrue(calls.stream().allMatch(call ->
                call.getOpcode() == Opcodes.INVOKESTATIC
                        && call.owner.equals("net/neoforged/forgestuck/compat/LegacyFluidTank")));
    }

    private static void tankMethod(ClassWriter w, String name, String desc, boolean ref) {
        var m = w.visitMethod(Opcodes.ACC_PUBLIC, name, desc, null, null);
        m.visitCode();
        if (ref) {
            m.visitInsn(Opcodes.ACONST_NULL);
            m.visitInsn(Opcodes.ARETURN);
        } else {
            m.visitInsn(Opcodes.ICONST_0);
            m.visitInsn(Opcodes.IRETURN);
        }
        m.visitMaxs(0, 0);
        m.visitEnd();
    }

    private static Class<?> load(String name, byte[] input) {
        byte[] out = new ForgeOwnerRemapStep().apply(name, input);
        assertEquals(Set.of(), ForgeOwnerMappings.defaults().remainingOwners(out));
        return new ClassLoader(PickupEventAndFluidTankTest.class.getClassLoader()) {
            Class<?> define() {
                return defineClass(name, out, 0, out.length);
            }
        }.define();
    }
}
