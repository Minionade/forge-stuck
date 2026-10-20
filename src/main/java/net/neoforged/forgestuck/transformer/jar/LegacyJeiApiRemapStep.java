package net.neoforged.forgestuck.transformer.jar;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Remaps JEI's Forge-specific API package to the equivalent NeoForge API package. */
public final class LegacyJeiApiRemapStep implements ClassTransformStep {
    private static final String LEGACY_FORGE_TYPES = "mezz/jei/api/forge/ForgeTypes";
    private static final String NEOFORGE_TYPES = "mezz/jei/api/neoforge/NeoForgeTypes";
    private static final String VANILLA_CATEGORY_REGISTRATION =
            "mezz/jei/api/registration/IVanillaCategoryExtensionRegistration";
    private static final String EXTENDABLE_RECIPE_CATEGORY =
            "mezz/jei/api/recipe/category/extensions/IExtendableRecipeCategory";
    private static final String LEGACY_CATEGORY_SHIM =
            "net/neoforged/forgestuck/compat/LegacyJeiCategoryExtensionShim";
    private static final String LEGACY_RECIPE_REGISTRATION =
            "net/neoforged/forgestuck/compat/LegacyJeiRecipeRegistration";
    private static final String RECIPE_REGISTRATION =
            "mezz/jei/api/registration/IRecipeRegistration";
    private static final String RECIPE_TYPE = "mezz/jei/api/recipe/RecipeType";
    private static final String ADD_RECIPES_DESC = "(L" + RECIPE_TYPE + ";Ljava/util/List;)V";

    @Override
    public byte[] apply(String className, byte[] bytes) {
        if (bytes == null) return null;
        String byteText = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        boolean remapForgeTypes = byteText.contains(LEGACY_FORGE_TYPES);
        boolean bridgeCraftingCategory = byteText.contains(VANILLA_CATEGORY_REGISTRATION)
                && byteText.contains("getCraftingCategory")
                && byteText.contains(EXTENDABLE_RECIPE_CATEGORY);
        boolean bridgeRecipeRegistration = byteText.contains(RECIPE_REGISTRATION)
                && byteText.contains("addRecipes");
        if (!remapForgeTypes && !bridgeCraftingCategory && !bridgeRecipeRegistration) return bytes;

        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassRemapper(writer, new Remapper() {
            @Override
            public String map(String internalName) {
                return internalName.equals(LEGACY_FORGE_TYPES) ? NEOFORGE_TYPES : internalName;
            }
        }), 0);
        byte[] remapped = writer.toByteArray();
        if (!bridgeCraftingCategory && !bridgeRecipeRegistration) return remapped;

        ClassNode node = new ClassNode();
        new ClassReader(remapped).accept(node, 0);
        boolean changed = false;
        for (var method : node.methods) {
            for (var instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)) continue;
                if (bridgeRecipeRegistration && call.getOpcode() == Opcodes.INVOKEINTERFACE
                        && call.owner.equals(RECIPE_REGISTRATION)
                        && call.name.equals("addRecipes")
                        && call.desc.equals(ADD_RECIPES_DESC)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_RECIPE_REGISTRATION;
                    call.name = "addRecipes";
                    call.desc = "(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/List;)V";
                    call.itf = false;
                    changed = true;
                } else if (bridgeCraftingCategory && call.getOpcode() == Opcodes.INVOKEINTERFACE
                        && call.owner.equals(VANILLA_CATEGORY_REGISTRATION)
                        && call.name.equals("getCraftingCategory")
                        && call.desc.equals("()L" + EXTENDABLE_RECIPE_CATEGORY + ";")) {
                    MethodInsnNode shimCall = new MethodInsnNode(Opcodes.INVOKESTATIC, LEGACY_CATEGORY_SHIM,
                            "getCraftingCategory", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                    method.instructions.set(call, shimCall);
                    method.instructions.insert(shimCall,
                            new TypeInsnNode(Opcodes.CHECKCAST, EXTENDABLE_RECIPE_CATEGORY));
                    changed = true;
                }
            }
        }
        if (!changed) return remapped;
        ClassWriter bridgedWriter = new ClassWriter(0);
        node.accept(bridgedWriter);
        return bridgedWriter.toByteArray();
    }
}
