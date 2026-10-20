package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

class LegacySimpleCraftingRecipeStepTest {
    private static final String CATEGORY = "net/minecraft/world/item/crafting/CraftingBookCategory";
    private static final String RESOURCE_LOCATION = "net/minecraft/resources/ResourceLocation";
    private static final String RECIPE = "thedarkcolour/gendustry/recipe/GeneticTemplateRecipe";
    private static final String FACTORY =
            "net/minecraft/world/item/crafting/SimpleCraftingRecipeSerializer$Factory";
    private static final String LEGACY_FACTORY_METHOD =
            "(L" + RESOURCE_LOCATION + ";L" + CATEGORY + ";)"
                    + "Lnet/minecraft/world/item/crafting/CraftingRecipe;";

    @Test
    void adaptsGeneticTemplateFactoryAndConstructorToCurrentCraftingRecipeApi() {
        ClassWriter factoryWriter = new ClassWriter(0);
        factoryWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "thedarkcolour/gendustry/registry/GRecipeTypes", null,
                "java/lang/Object", null);
        var method = factoryWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create",
                "()L" + FACTORY + ";", null, null);
        method.visitCode();
        method.visitInvokeDynamicInsn("m_247316_", "()L" + FACTORY + ";",
                new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                                + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;"
                                + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                        false),
                Type.getMethodType(LEGACY_FACTORY_METHOD),
                new Handle(Opcodes.H_NEWINVOKESPECIAL, RECIPE, "<init>",
                        "(L" + RESOURCE_LOCATION + ";L" + CATEGORY + ";)V", false),
                Type.getMethodType("(L" + RESOURCE_LOCATION + ";L" + CATEGORY + ";)L" + RECIPE + ";"));
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        factoryWriter.visitEnd();

        ClassNode factory = new ClassNode();
        new ClassReader(new LegacySimpleCraftingRecipeStep()
                        .apply("thedarkcolour.gendustry.registry.GRecipeTypes", factoryWriter.toByteArray()))
                .accept(factory, 0);
        InvokeDynamicInsnNode indy = Arrays.stream(factory.methods.get(0).instructions.toArray())
                .filter(InvokeDynamicInsnNode.class::isInstance)
                .map(InvokeDynamicInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("create", indy.name);
        assertEquals("(L" + CATEGORY + ";)Lnet/minecraft/world/item/crafting/CraftingRecipe;",
                ((Type) indy.bsmArgs[0]).getDescriptor());
        assertEquals("(L" + CATEGORY + ";)V", ((Handle) indy.bsmArgs[1]).getDesc());
        assertEquals("(L" + CATEGORY + ";)L" + RECIPE + ";", ((Type) indy.bsmArgs[2]).getDescriptor());

        ClassWriter recipeWriter = new ClassWriter(0);
        recipeWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, RECIPE, null,
                "net/minecraft/world/item/crafting/CustomRecipe", null);
        var constructor = recipeWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                "(L" + RESOURCE_LOCATION + ";L" + CATEGORY + ";)V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitVarInsn(Opcodes.ALOAD, 2);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "net/minecraft/world/item/crafting/CustomRecipe", "<init>",
                "(L" + RESOURCE_LOCATION + ";L" + CATEGORY + ";)V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(3, 3);
        constructor.visitEnd();
        recipeWriter.visitEnd();

        ClassNode recipe = new ClassNode();
        new ClassReader(new LegacySimpleCraftingRecipeStep()
                        .apply("thedarkcolour.gendustry.recipe.GeneticTemplateRecipe", recipeWriter.toByteArray()))
                .accept(recipe, 0);
        var transformedConstructor = recipe.methods.get(0);
        assertEquals("(L" + CATEGORY + ";)V", transformedConstructor.desc);
        MethodInsnNode superCall = Arrays.stream(transformedConstructor.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("(L" + CATEGORY + ";)V", superCall.desc);
    }
}
