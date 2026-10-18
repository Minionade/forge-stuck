package net.neoforged.forgestuck.transformer.jar;

import java.util.List;
import java.util.Set;

/** Legacy custom ingredient types and serializers, adapted to NeoForge's {@code ICustomIngredient}. */
final class IngredientMappings {
    private IngredientMappings() {}

    private static final String FORGE = "net/minecraftforge/common/crafting/";
    private static final String COMPAT = "net/neoforged/forgestuck/compat/";
    private static final String INGREDIENT = "Lnet/minecraft/world/item/crafting/Ingredient;";

    static List<ForgeOwnerMappings.Entry> entries() {
        return List.of(
                new ForgeOwnerMappings.Entry(FORGE + "CompoundIngredient",
                        "net/neoforged/neoforge/common/crafting/CompoundIngredient",
                        Set.of("of([" + INGREDIENT + ")" + INGREDIENT)),
                new ForgeOwnerMappings.Entry(FORGE + "DifferenceIngredient",
                        "net/neoforged/neoforge/common/crafting/DifferenceIngredient",
                        Set.of("of(" + INGREDIENT + INGREDIENT + ")" + INGREDIENT)),
                new ForgeOwnerMappings.Entry(FORGE + "IntersectionIngredient",
                        "net/neoforged/neoforge/common/crafting/IntersectionIngredient",
                        Set.of("of([" + INGREDIENT + ")" + INGREDIENT)),
                new ForgeOwnerMappings.Entry(FORGE + "AbstractIngredient",
                        COMPAT + "LegacyAbstractIngredient", Set.of(
                                "<init>()V",
                                "<init>(Ljava/util/stream/Stream;)V",
                                "invalidate()V",
                                "checkInvalidation()Z",
                                "markValid()V",
                                "m_43908_()[Lnet/minecraft/world/item/ItemStack;",
                                "m_43931_()Lit/unimi/dsi/fastutil/ints/IntList;",
                                "m_43942_()Lcom/google/gson/JsonElement;",
                                "m_43947_()Z",
                                "getSerializer()Lnet/minecraftforge/common/crafting/IIngredientSerializer;")),
                new ForgeOwnerMappings.Entry(FORGE + "IIngredientSerializer",
                        COMPAT + "LegacyIngredientSerializer", Set.of(
                                "parse(Lcom/google/gson/JsonObject;)" + INGREDIENT,
                                "write(Lnet/minecraft/network/FriendlyByteBuf;" + INGREDIENT + ")V",
                                "parse(Lnet/minecraft/network/FriendlyByteBuf;)" + INGREDIENT)));
    }
}
