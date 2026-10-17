package net.neoforged.forgestuck.transformer.jar;

import java.util.List;
import java.util.Set;

/**
 * Forge 1.20.1 crafting conditions. NeoForge kept {@code ICondition}/{@code IContext} (different members), the
 * simple built-ins with identical constructors, and replaced registration with codecs, so serializer/registration
 * go to {@code compat.LegacyConditions}. And/Or (array constructors) and the ingredient parts of CraftingHelper
 * are intentionally unmapped. Members are reflection-checked in {@code ForgeOwnerMappingTargetsTest}.
 */
final class ConditionMappings {
    private ConditionMappings() {}

    private static final String C = "net/minecraftforge/common/crafting/conditions/";
    private static final String COMPAT = "net/neoforged/forgestuck/compat/";
    private static final String NC = "net/neoforged/neoforge/common/conditions/";

    static List<ForgeOwnerMappings.Entry> entries() {
        String ic = "L" + C + "ICondition;";
        String ctx = "L" + C + "ICondition$IContext;";
        String ser = "L" + C + "IConditionSerializer;";
        String json = "Lcom/google/gson/JsonObject;";
        String arr = "Lcom/google/gson/JsonArray;";
        String rl = "Lnet/minecraft/resources/ResourceLocation;";
        return List.of(
                e(C + "ICondition", NC + "ICondition", Set.of("test(" + ctx + ")Z")),
                e(C + "ICondition$IContext", NC + "ICondition$IContext", Set.of(
                        "EMPTY" + ctx, "TAGS_INVALID" + ctx,
                        "getTag(Lnet/minecraft/tags/TagKey;)Ljava/util/Collection;")),
                e(C + "IConditionSerializer", COMPAT + "LegacyConditionSerializer", Set.of(
                        "write(" + json + ic + ")V", "read(" + json + ")" + ic, "getID()" + rl,
                        "getJson(" + ic + ")" + json)),
                e("net/minecraftforge/common/crafting/CraftingHelper", COMPAT + "LegacyConditions", Set.of(
                        "register(" + ser + ")" + ser, "getCondition(" + json + ")" + ic,
                        "processConditions(" + arr + ctx + ")Z",
                        "processConditions(" + json + "Ljava/lang/String;" + ctx + ")Z",
                        "serialize(" + ic + ")" + json, "serialize([" + ic + ")" + arr)),
                e(C + "TrueCondition", NC + "TrueCondition", Set.of("INSTANCE" + "L" + C + "TrueCondition;")),
                e(C + "FalseCondition", NC + "FalseCondition", Set.of("INSTANCE" + "L" + C + "FalseCondition;")),
                e(C + "NotCondition", NC + "NotCondition", Set.of()),
                e(C + "ModLoadedCondition", NC + "ModLoadedCondition", Set.of()),
                e(C + "ItemExistsCondition", NC + "ItemExistsCondition", Set.of()));
    }

    private static ForgeOwnerMappings.Entry e(String oldOwner, String newOwner, Set<String> members) {
        return new ForgeOwnerMappings.Entry(oldOwner, newOwner, members);
    }
}