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
