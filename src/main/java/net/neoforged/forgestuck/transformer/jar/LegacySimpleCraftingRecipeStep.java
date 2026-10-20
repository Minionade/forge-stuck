package net.neoforged.forgestuck.transformer.jar;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Adapts Gendustry's 1.20 custom-recipe factory to the 1.21 crafting serializer contract. */
final class LegacySimpleCraftingRecipeStep implements ClassTransformStep {
    private static final String RECIPE_TYPES = "thedarkcolour/gendustry/registry/GRecipeTypes";
    private static final String RECIPE = "thedarkcolour/gendustry/recipe/GeneticTemplateRecipe";
    private static final String RESOURCE_LOCATION = "net/minecraft/resources/ResourceLocation";
    private static final String CATEGORY = "net/minecraft/world/item/crafting/CraftingBookCategory";
    private static final String CRAFTING_RECIPE = "net/minecraft/world/item/crafting/CraftingRecipe";
    private static final String SIMPLE_SERIALIZER = "net/minecraft/world/item/crafting/SimpleCraftingRecipeSerializer";
    private static final String FACTORY = SIMPLE_SERIALIZER + "$Factory";
    private static final String LEGACY_FACTORY_METHOD =
            "(L" + RESOURCE_LOCATION + ";L" + CATEGORY + ";)L" + CRAFTING_RECIPE + ";";
    private static final String CURRENT_FACTORY_METHOD = "(L" + CATEGORY + ";)L" + CRAFTING_RECIPE + ";";
    private static final String LEGACY_RECIPE_CONSTRUCTOR =
            "(L" + RESOURCE_LOCATION + ";L" + CATEGORY + ";)V";
    private static final String CURRENT_RECIPE_CONSTRUCTOR = "(L" + CATEGORY + ";)V";

    @Override
    public byte[] apply(String className, byte[] bytes) {
        if (bytes == null || (!className.equals(RECIPE_TYPES.replace('/', '.'))
                && !className.equals(RECIPE.replace('/', '.')))) {
            return bytes;
        }
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean changed = false;
        if (node.name.equals(RECIPE_TYPES)) {
            for (MethodNode method : node.methods) {
                for (var instruction : method.instructions.toArray()) {
                    if (!(instruction instanceof InvokeDynamicInsnNode indy)
                            || !indy.name.equals("m_247316_")
                            || !indy.desc.equals("()L" + FACTORY + ";")
                            || !indy.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                            || indy.bsmArgs.length < 3
                            || !(indy.bsmArgs[0] instanceof Type samType)
                            || !samType.getDescriptor().equals(LEGACY_FACTORY_METHOD)
                            || !(indy.bsmArgs[1] instanceof Handle constructor)
                            || constructor.getTag() != Opcodes.H_NEWINVOKESPECIAL
                            || !constructor.getOwner().equals(RECIPE)
                            || !constructor.getDesc().equals(LEGACY_RECIPE_CONSTRUCTOR)
                            || !(indy.bsmArgs[2] instanceof Type instantiatedType)
                            || !instantiatedType.getDescriptor().equals(
                                    "(L" + RESOURCE_LOCATION + ";L" + CATEGORY + ";)L" + RECIPE + ";")) {
                        continue;
                    }
                    indy.name = "create";
                    indy.bsmArgs[0] = Type.getMethodType(CURRENT_FACTORY_METHOD);
                    indy.bsmArgs[1] = new Handle(
                            constructor.getTag(), constructor.getOwner(), constructor.getName(),
                            CURRENT_RECIPE_CONSTRUCTOR, constructor.isInterface());
                    indy.bsmArgs[2] = Type.getMethodType("(L" + CATEGORY + ";)L" + RECIPE + ";");
                    changed = true;
                }
            }
        } else if (node.name.equals(RECIPE)) {
            for (MethodNode method : node.methods) {
                if (!method.name.equals("<init>") || !method.desc.equals(LEGACY_RECIPE_CONSTRUCTOR)) continue;
                method.desc = CURRENT_RECIPE_CONSTRUCTOR;
                method.instructions.clear();
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
                method.instructions.add(new MethodInsnNode(
                        Opcodes.INVOKESPECIAL, "net/minecraft/world/item/crafting/CustomRecipe",
                        "<init>", CURRENT_RECIPE_CONSTRUCTOR, false));
                method.instructions.add(new InsnNode(Opcodes.RETURN));
                method.maxLocals = 2;
                method.maxStack = 2;
                changed = true;
            }
        }
        if (!changed) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
