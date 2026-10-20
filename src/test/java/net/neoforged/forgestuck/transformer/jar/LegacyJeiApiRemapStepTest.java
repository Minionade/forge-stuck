package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

class LegacyJeiApiRemapStepTest {
    @Test
    void remapsForgeIngredientTypeOwnerToNeoForgeTypes() {
        String legacyOwner = "mezz/jei/api/forge/ForgeTypes";
        String currentOwner = "mezz/jei/api/neoforge/NeoForgeTypes";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/LegacyRecipeCategory", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "fluidType",
                "()Lmezz/jei/api/ingredients/IIngredientTypeWithSubtypes;", null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, legacyOwner, "FLUID_STACK",
                "Lmezz/jei/api/ingredients/IIngredientTypeWithSubtypes;");
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        byte[] original = writer.toByteArray();

        byte[] transformed = new LegacyJeiApiRemapStep().apply("com.example.LegacyRecipeCategory", original);

        assertNotSame(original, transformed);
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        FieldInsnNode field = (FieldInsnNode) node.methods.get(0).instructions.getFirst();
        assertEquals(currentOwner, field.owner);
        assertEquals("FLUID_STACK", field.name);
        assertEquals("Lmezz/jei/api/ingredients/IIngredientTypeWithSubtypes;", field.desc);
    }

    @Test
    void leavesClassesWithoutLegacyJeiTypesUntouched() {
        byte[] original = new byte[] {1, 2, 3};

        assertSame(original, new LegacyJeiApiRemapStep().apply("com.example.Unrelated", original));
    }

    @Test
    void bridgesLegacyCraftingCategoryRegistrationToRuntimeShim() {
        String registration = "mezz/jei/api/registration/IVanillaCategoryExtensionRegistration";
        String legacyCategory = "mezz/jei/api/recipe/category/extensions/IExtendableRecipeCategory";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/LegacyJeiPlugin", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "register", "(L" + registration + ";)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, registration, "getCraftingCategory",
                "()L" + legacyCategory + ";", true);
        method.visitVarInsn(Opcodes.ASTORE, 2);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 3);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new LegacyJeiApiRemapStep().apply(
                "com.example.LegacyJeiPlugin", writer.toByteArray())).accept(transformed, 0);
        var instructions = transformed.methods.get(0).instructions;
        MethodInsnNode shimCall = java.util.Arrays.stream(instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, shimCall.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyJeiCategoryExtensionShim", shimCall.owner);
        assertEquals("getCraftingCategory", shimCall.name);
        assertEquals("(Ljava/lang/Object;)Ljava/lang/Object;", shimCall.desc);
        TypeInsnNode cast = java.util.Arrays.stream(instructions.toArray())
                .filter(TypeInsnNode.class::isInstance).map(TypeInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.CHECKCAST, cast.getOpcode());
        assertEquals(legacyCategory, cast.desc);
    }

    @Test
    void bridgesRecipeRegistrationWithoutRequiringSrgMembers() {
        String registration = "mezz/jei/api/registration/IRecipeRegistration";
        String recipeType = "mezz/jei/api/recipe/RecipeType";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/LegacyJeiRecipePlugin", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "register",
                "(L" + registration + ";L" + recipeType + ";Ljava/util/List;)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ALOAD, 3);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, registration, "addRecipes",
                "(L" + recipeType + ";Ljava/util/List;)V", true);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 4);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new LegacyJeiApiRemapStep().apply(
                "com.example.LegacyJeiRecipePlugin", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyJeiRecipeRegistration", call.owner);
        assertEquals("addRecipes", call.name);
        assertEquals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/List;)V", call.desc);
    }
}
