package net.neoforged.forgestuck.transformer.jar;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Owner mappings for Forge 1.20.1's config, environment and mod-context APIs. NeoForge forked most of them with the
 * same member names and erased descriptors ({@code ForgeConfigSpec} -> {@code ModConfigSpec}); the ones it removed
 * (DistExecutor, ModLoadingContext.registerConfig) go to {@code net.neoforged.forgestuck.compat} stand-ins. Every
 * whitelisted member is reflection-checked against the target in {@code ForgeOwnerMappingTargetsTest}.
 */
final class ConfigEnvironmentMappings {
    private ConfigEnvironmentMappings() {}

    static final String FC = "net/minecraftforge/common/ForgeConfigSpec";
    static final String MC = "net/neoforged/neoforge/common/ModConfigSpec";
    private static final String COMPAT = "net/neoforged/forgestuck/compat/";
    private static final String CFG = "net/minecraftforge/fml/config/";
    private static final String DIST = "Lnet/minecraftforge/api/distmarker/Dist;";
    private static final String S = "Ljava/lang/String;";
    private static final String O = "Ljava/lang/Object;";
    private static final String LIST = "Ljava/util/List;";
    private static final String PRED = "Ljava/util/function/Predicate;";
    private static final String SUP = "Ljava/util/function/Supplier;";
    private static final String CLS = "Ljava/lang/Class;";
    private static final String COLL = "Ljava/util/Collection;";
    private static final String ENUM = "Ljava/lang/Enum;";
    private static final String CMP = "Ljava/lang/Comparable;";
    private static final String GET_METHOD = "Lcom/electronwill/nightconfig/core/EnumGetMethod;";

    static List<ForgeOwnerMappings.Entry> entries() {
        String b = "L" + FC + "$Builder;";
        String cv = "L" + FC + "$ConfigValue;";
        String bv = "L" + FC + "$BooleanValue;";
        String iv = "L" + FC + "$IntValue;";
        String lv = "L" + FC + "$LongValue;";
        String dv = "L" + FC + "$DoubleValue;";
        String ev = "L" + FC + "$EnumValue;";
        Set<String> builder = new TreeSet<>();
        builder.addAll(Set.of(
                "<init>()V",
                "comment(" + S + ")" + b, "comment([" + S + ")" + b, "translation(" + S + ")" + b,
                "worldRestart()" + b, "push(" + S + ")" + b, "push(" + LIST + ")" + b, "pop()" + b, "pop(I)" + b,
                "build()L" + FC + ";", "configure(Ljava/util/function/Function;)Lorg/apache/commons/lang3/tuple/Pair;"));
        for (String path : List.of(S, LIST)) {
            builder.addAll(Set.of(
                    "define(" + path + O + ")" + cv,
                    "define(" + path + O + PRED + ")" + cv,
                    "define(" + path + SUP + PRED + ")" + cv,
                    "define(" + path + "Z)" + bv,
                    "define(" + path + SUP + ")" + bv,
                    "defineInRange(" + path + "III)" + iv,
                    "defineInRange(" + path + SUP + "II)" + iv,
                    "defineInRange(" + path + "JJJ)" + lv,
                    "defineInRange(" + path + SUP + "JJ)" + lv,
                    "defineInRange(" + path + "DDD)" + dv,
                    "defineInRange(" + path + SUP + "DD)" + dv,
                    "defineInRange(" + path + CMP + CMP + CMP + CLS + ")" + cv,
                    "defineInRange(" + path + SUP + CMP + CMP + CLS + ")" + cv,
                    "defineInList(" + path + O + COLL + ")" + cv,
                    "defineInList(" + path + SUP + COLL + ")" + cv,
                    "defineList(" + path + LIST + PRED + ")" + cv,
                    "defineList(" + path + SUP + PRED + ")" + cv,
                    "defineListAllowEmpty(" + path + LIST + PRED + ")" + cv,
                    "defineListAllowEmpty(" + path + SUP + PRED + ")" + cv,
                    "defineEnum(" + path + ENUM + ")" + ev,
                    "defineEnum(" + path + ENUM + GET_METHOD + ")" + ev,
                    "defineEnum(" + path + ENUM + "[" + ENUM + ")" + ev,
                    "defineEnum(" + path + ENUM + COLL + ")" + ev,
                    "defineEnum(" + path + ENUM + PRED + ")" + ev));
        }
        Set<String> value = Set.of("get()" + O, "set(" + O + ")V", "save()V", "next()" + b, "getPath()" + LIST);
        String type = "L" + CFG + "ModConfig$Type;";
        String spec = "L" + CFG + "IConfigSpec;";
        String ctx = "Lnet/minecraftforge/fml/ModLoadingContext;";
        String dx = "net/minecraftforge/fml/DistExecutor";
        String cd = COMPAT + "DistExecutor";
        return List.of(
                e(FC, MC, Set.of("isLoaded()Z", "isEmpty()Z", "save()V")),
                e(FC + "$Builder", MC + "$Builder", builder),
                e(FC + "$ConfigValue", MC + "$ConfigValue", value),
                e(FC + "$BooleanValue", MC + "$BooleanValue", value),
                e(FC + "$IntValue", MC + "$IntValue", value),
                e(FC + "$LongValue", MC + "$LongValue", value),
                e(FC + "$DoubleValue", MC + "$DoubleValue", value),
                e(FC + "$EnumValue", MC + "$EnumValue", value),
                e(CFG + "ModConfig", "net/neoforged/fml/config/ModConfig", Set.of(
                        "getType()" + type, "getFileName()" + S, "getSpec()" + spec, "getModId()" + S)),
                e(CFG + "ModConfig$Type", "net/neoforged/fml/config/ModConfig$Type", Set.of(
                        "COMMON" + type, "CLIENT" + type, "SERVER" + type, "ordinal()I", "name()" + S,
                        "values()[" + type, "valueOf(" + S + ")" + type, "extension()" + S)),
                e("net/minecraftforge/fml/event/config/ModConfigEvent", "net/neoforged/fml/event/config/ModConfigEvent",
                        Set.of("getConfig()L" + CFG + "ModConfig;")),
                e("net/minecraftforge/fml/event/config/ModConfigEvent$Loading",
                        "net/neoforged/fml/event/config/ModConfigEvent$Loading",
                        Set.of("getConfig()L" + CFG + "ModConfig;")),
                e("net/minecraftforge/fml/event/config/ModConfigEvent$Reloading",
                        "net/neoforged/fml/event/config/ModConfigEvent$Reloading",
                        Set.of("getConfig()L" + CFG + "ModConfig;")),
                e("net/minecraftforge/common/MinecraftForge", "net/neoforged/neoforge/common/NeoForge",
                        Set.of("EVENT_BUS" + "Lnet/minecraftforge/eventbus/api/IEventBus;")),
                e("net/minecraftforge/fml/ModLoadingContext", COMPAT + "ModLoadingContext", Set.of(
                        "get()" + ctx, "getActiveContainer()Lnet/minecraftforge/fml/ModContainer;",
                        "getActiveNamespace()" + S,
                        "registerConfig(" + type + spec + ")V", "registerConfig(" + type + spec + S + ")V")),
                e("net/minecraftforge/fml/ModContainer", "net/neoforged/fml/ModContainer",
                        Set.of("getModId()" + S, "getNamespace()" + S)),
                e("net/minecraftforge/fml/ModList", "net/neoforged/fml/ModList", Set.of(
                        "get()Lnet/minecraftforge/fml/ModList;", "isLoaded(" + S + ")Z",
                        "getModContainerById(" + S + ")Ljava/util/Optional;")),
                e("net/minecraftforge/fml/loading/FMLEnvironment", "net/neoforged/fml/loading/FMLEnvironment",
                        Set.of("dist" + DIST, "production" + "Z")),
                e("net/minecraftforge/fml/loading/FMLPaths", "net/neoforged/fml/loading/FMLPaths", Set.of(
                        "GAMEDIR" + "Lnet/minecraftforge/fml/loading/FMLPaths;",
                        "MODSDIR" + "Lnet/minecraftforge/fml/loading/FMLPaths;",
                        "CONFIGDIR" + "Lnet/minecraftforge/fml/loading/FMLPaths;",
                        "get()Ljava/nio/file/Path;")),
                e("net/minecraftforge/fml/LogicalSide", "net/neoforged/fml/LogicalSide", Set.of(
                        "CLIENT" + "Lnet/minecraftforge/fml/LogicalSide;", "SERVER" + "Lnet/minecraftforge/fml/LogicalSide;",
                        "isClient()Z", "isServer()Z")),
                e(dx, cd, Set.of(
                        "callWhenOn(" + DIST + SUP + ")" + O, "unsafeCallWhenOn(" + DIST + SUP + ")" + O,
                        "safeCallWhenOn(" + DIST + SUP + ")" + O,
                        "runWhenOn(" + DIST + SUP + ")V", "unsafeRunWhenOn(" + DIST + SUP + ")V",
                        "safeRunWhenOn(" + DIST + SUP + ")V",
                        "runForDist(" + SUP + SUP + ")" + O, "unsafeRunForDist(" + SUP + SUP + ")" + O,
                        "safeRunForDist(" + SUP + SUP + ")" + O)),
                e(dx + "$SafeReferent", cd + "$SafeReferent", Set.of()),
                e(dx + "$SafeCallable", cd + "$SafeCallable", Set.of("call()" + O)),
                e(dx + "$SafeSupplier", cd + "$SafeSupplier", Set.of("get()" + O)),
                e(dx + "$SafeRunnable", cd + "$SafeRunnable", Set.of("run()V")));
    }

    private static ForgeOwnerMappings.Entry e(String oldOwner, String newOwner, Set<String> members) {
        return new ForgeOwnerMappings.Entry(oldOwner, newOwner, members);
    }
}
