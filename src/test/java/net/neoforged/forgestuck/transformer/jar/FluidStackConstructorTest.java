package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.invoke.MethodHandle;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.forgestuck.testsupport.TestBootstrap;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;

class FluidStackConstructorTest {
    private static final String OLD = "net/minecraftforge/fluids/FluidStack";
    private static final String COPY = "(L" + OLD + ";I)V";
    private static final String FLUID = "Lnet/minecraft/world/level/material/Fluid;";

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    void copyAllocationAndHandlePreserveComponentsAndSource() throws Throwable {
        Class<?> transformed = load(copyUser());
        FluidStack source = new FluidStack(Fluids.WATER, 1000);
        CompoundTag tag = new CompoundTag();
        tag.putString("marker", "preserved");
        source.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        FluidStack copy = (FluidStack) transformed.getMethod("copy", FluidStack.class, int.class)
                .invoke(null, source, 250);
        MethodHandle handle = (MethodHandle) transformed.getMethod("handle").invoke(null);
        FluidStack handleCopy = (FluidStack) handle.invokeWithArguments(source, 500);
        for (FluidStack result : new FluidStack[] {copy, handleCopy}) {
            assertNotSame(source, result);
            assertSame(Fluids.WATER, result.getFluid());
            assertEquals(source.get(DataComponents.CUSTOM_DATA), result.get(DataComponents.CUSTOM_DATA));
        }
        assertEquals(250, copy.getAmount());
        assertEquals(500, handleCopy.getAmount());
        assertEquals(1000, source.getAmount());
        copy.set(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        assertEquals("preserved", source.get(DataComponents.CUSTOM_DATA).copyTag().getString("marker"));
    }

    @Test
    void legacyTagCallsUseIsolatedMutableCustomDataShim() throws Exception {
        Class<?> transformed = load(tagAccessUser());
        FluidStack stack = new FluidStack(Fluids.WATER, 1000);
        assertSame(Fluids.WATER,
                transformed.getMethod("raw", FluidStack.class).invoke(null, stack));
        assertFalse((boolean) transformed.getMethod("has", FluidStack.class).invoke(null, stack));
        assertNull(transformed.getMethod("get", FluidStack.class).invoke(null, stack));

        CompoundTag live = (CompoundTag) transformed.getMethod("getOrCreate", FluidStack.class)
                .invoke(null, stack);
        live.putString("marker", "source");
        assertTrue((boolean) transformed.getMethod("has", FluidStack.class).invoke(null, stack));
        assertEquals("source", ((CompoundTag) transformed.getMethod("get", FluidStack.class)
                .invoke(null, stack)).getString("marker"));

        FluidStack copy = stack.copy();
        CompoundTag copyTag = (CompoundTag) transformed.getMethod("getOrCreate", FluidStack.class)
                .invoke(null, copy);
        copyTag.putString("marker", "copy");
        assertEquals("source", ((CompoundTag) transformed.getMethod("get", FluidStack.class)
                .invoke(null, stack)).getString("marker"));

        CompoundTag assigned = new CompoundTag();
        assigned.putString("marker", "assigned");
        transformed.getMethod("set", FluidStack.class, CompoundTag.class).invoke(null, stack, assigned);
        assigned.putString("marker", "changed");
        assertEquals("assigned", ((CompoundTag) transformed.getMethod("get", FluidStack.class)
                .invoke(null, stack)).getString("marker"));
        transformed.getMethod("set", FluidStack.class, CompoundTag.class).invoke(null, stack, (Object) null);
        assertFalse((boolean) transformed.getMethod("has", FluidStack.class).invoke(null, stack));
        assertNull(transformed.getMethod("get", FluidStack.class).invoke(null, stack));
    }

    @Test
    void legacyNbtSerializationCallsAndHandlesUseCompatShim() throws Throwable {
        Class<?> transformed = load(serializationUser());
        FluidStack stack = new FluidStack(Fluids.WATER, 500);
        CompoundTag fluidTag = new CompoundTag();
        fluidTag.putString("marker", "preserved");
        net.neoforged.forgestuck.compat.LegacyFluidStack.setTag(stack, fluidTag);

        CompoundTag nbt = new CompoundTag();
        assertSame(nbt, transformed.getMethod("write", FluidStack.class, CompoundTag.class)
                .invoke(null, stack, nbt));
        assertEquals("minecraft:water", nbt.getString("FluidName"));
        assertEquals(500, nbt.getInt("Amount"));
        assertEquals("preserved", nbt.getCompound("Tag").getString("marker"));

        FluidStack loaded = (FluidStack) transformed.getMethod("load", CompoundTag.class).invoke(null, nbt);
        assertSame(Fluids.WATER, loaded.getFluid());
        assertEquals(500, loaded.getAmount());
        assertEquals("preserved", net.neoforged.forgestuck.compat.LegacyFluidStack.getTag(loaded).getString("marker"));

        MethodHandle loader = (MethodHandle) transformed.getMethod("loadHandle").invoke(null);
        MethodHandle writer = (MethodHandle) transformed.getMethod("writeHandle").invoke(null);
        assertSame(Fluids.WATER, ((FluidStack) loader.invokeWithArguments(nbt)).getFluid());
        assertEquals("minecraft:water", ((CompoundTag) writer.invokeWithArguments(stack, new CompoundTag()))
                .getString("FluidName"));
        assertSame(FluidStack.EMPTY,
                transformed.getMethod("load", CompoundTag.class).invoke(null, new CompoundTag()));
    }

    @Test
    void nestedBasicAllocationIsNotMistakenForOuterCopyConstructor() throws Exception {
        Class<?> transformed = load(copyUser());
        FluidStack copy = (FluidStack) transformed.getMethod("nested", Fluid.class, int.class)
                .invoke(null, Fluids.WATER, 750);
        assertSame(Fluids.WATER, copy.getFluid());
        assertEquals(750, copy.getAmount());
    }

    private static Class<?> load(byte[] input) {
        byte[] output = new ForgeOwnerRemapStep().apply("com.ex.FluidCopy", input);
        assertEquals(Set.of(), ForgeOwnerMappings.defaults().remainingOwners(output));
        return new ClassLoader(FluidStackConstructorTest.class.getClassLoader()) {
            Class<?> define() {
                return defineClass("com.ex.FluidCopy", output, 0, output.length);
            }
        }.define();
    }

    private static byte[] tagAccessUser() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/FluidCopy", null, "java/lang/Object", null);
        addTagCall(writer, "raw", FLUID, "getRawFluid", "()" + FLUID, Opcodes.ARETURN);
        addTagCall(writer, "get", "Lnet/minecraft/nbt/CompoundTag;", "getTag",
                "()Lnet/minecraft/nbt/CompoundTag;", Opcodes.ARETURN);
        addTagCall(writer, "getOrCreate", "Lnet/minecraft/nbt/CompoundTag;", "getOrCreateTag",
                "()Lnet/minecraft/nbt/CompoundTag;", Opcodes.ARETURN);
        addTagCall(writer, "has", "Z", "hasTag", "()Z", Opcodes.IRETURN);

        var set = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "set",
                "(L" + OLD + ";Lnet/minecraft/nbt/CompoundTag;)V", null, null);
        set.visitCode();
        set.visitVarInsn(Opcodes.ALOAD, 0);
        set.visitVarInsn(Opcodes.ALOAD, 1);
        set.visitMethodInsn(Opcodes.INVOKEVIRTUAL, OLD, "setTag", "(Lnet/minecraft/nbt/CompoundTag;)V", false);
        set.visitInsn(Opcodes.RETURN);
        set.visitMaxs(0, 0);
        set.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] serializationUser() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/FluidCopy", null, "java/lang/Object", null);

        var load = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "load",
                "(Lnet/minecraft/nbt/CompoundTag;)L" + OLD + ";", null, null);
        load.visitCode();
        load.visitVarInsn(Opcodes.ALOAD, 0);
        load.visitMethodInsn(Opcodes.INVOKESTATIC, OLD, "loadFluidStackFromNBT",
                "(Lnet/minecraft/nbt/CompoundTag;)L" + OLD + ";", false);
        load.visitInsn(Opcodes.ARETURN);
        load.visitMaxs(0, 0);
        load.visitEnd();

        var write = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "write",
                "(L" + OLD + ";Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;", null, null);
        write.visitCode();
        write.visitVarInsn(Opcodes.ALOAD, 0);
        write.visitVarInsn(Opcodes.ALOAD, 1);
        write.visitMethodInsn(Opcodes.INVOKEVIRTUAL, OLD, "writeToNBT",
                "(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;", false);
        write.visitInsn(Opcodes.ARETURN);
        write.visitMaxs(0, 0);
        write.visitEnd();

        var loadHandle = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "loadHandle",
                "()Ljava/lang/invoke/MethodHandle;", null, null);
        loadHandle.visitCode();
        loadHandle.visitLdcInsn(new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, OLD, "loadFluidStackFromNBT",
                "(Lnet/minecraft/nbt/CompoundTag;)L" + OLD + ";", false));
        loadHandle.visitInsn(Opcodes.ARETURN);
        loadHandle.visitMaxs(0, 0);
        loadHandle.visitEnd();

        var writeHandle = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "writeHandle",
                "()Ljava/lang/invoke/MethodHandle;", null, null);
        writeHandle.visitCode();
        writeHandle.visitLdcInsn(new org.objectweb.asm.Handle(Opcodes.H_INVOKEVIRTUAL, OLD, "writeToNBT",
                "(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;", false));
        writeHandle.visitInsn(Opcodes.ARETURN);
        writeHandle.visitMaxs(0, 0);
        writeHandle.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void addTagCall(ClassWriter writer, String methodName, String returnType, String target,
            String targetDescriptor, int returnOpcode) {
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, methodName,
                "(L" + OLD + ";)" + returnType, null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, OLD, target, targetDescriptor, false);
        method.visitInsn(returnOpcode);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static byte[] copyUser() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/FluidCopy", null, "java/lang/Object", null);
        var copy = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "copy",
                "(L" + OLD + ";I)L" + OLD + ";", null, null);
        copy.visitCode();
        copy.visitTypeInsn(Opcodes.NEW, OLD);
        copy.visitInsn(Opcodes.DUP);
        copy.visitVarInsn(Opcodes.ALOAD, 0);
        copy.visitVarInsn(Opcodes.ILOAD, 1);
        copy.visitMethodInsn(Opcodes.INVOKESPECIAL, OLD, "<init>", COPY, false);
        copy.visitInsn(Opcodes.ARETURN);
        copy.visitMaxs(0, 0);
        copy.visitEnd();

        var handle = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "handle",
                "()Ljava/lang/invoke/MethodHandle;", null, null);
        handle.visitCode();
        handle.visitLdcInsn(new Handle(Opcodes.H_NEWINVOKESPECIAL, OLD, "<init>", COPY, false));
        handle.visitInsn(Opcodes.ARETURN);
        handle.visitMaxs(0, 0);
        handle.visitEnd();

        var nested = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "nested",
                "(" + FLUID + "I)L" + OLD + ";", null, null);
        nested.visitCode();
        nested.visitTypeInsn(Opcodes.NEW, OLD);
        nested.visitInsn(Opcodes.DUP);
        nested.visitTypeInsn(Opcodes.NEW, OLD);
        nested.visitInsn(Opcodes.DUP);
        nested.visitVarInsn(Opcodes.ALOAD, 0);
        nested.visitIntInsn(Opcodes.SIPUSH, 1000);
        nested.visitMethodInsn(Opcodes.INVOKESPECIAL, OLD, "<init>", "(" + FLUID + "I)V", false);
        nested.visitVarInsn(Opcodes.ILOAD, 1);
        nested.visitMethodInsn(Opcodes.INVOKESPECIAL, OLD, "<init>", COPY, false);
        nested.visitInsn(Opcodes.ARETURN);
        nested.visitMaxs(0, 0);
        nested.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
