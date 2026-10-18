package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

/** Guards that every default owner mapping points at a real class and every whitelisted member really exists. */
class ForgeOwnerMappingTargetsTest {
    private static final List<String> OWNERS = List.of(
            "net/minecraftforge/eventbus/api/IGenericEvent",
            "net/minecraftforge/eventbus/api/GenericEvent",
            "net/minecraftforge/common/SoundAction", "net/minecraftforge/common/SoundActions",
            "net/minecraftforge/event/AttachCapabilitiesEvent",
            "net/minecraftforge/common/capabilities/ICapabilityProvider",
            "net/minecraftforge/common/extensions/IForgeBlockEntity",
            "net/minecraft/network/FriendlyByteBuf$Writer", "net/minecraft/network/FriendlyByteBuf$Reader",
            "net/minecraftforge/registries/RegistryObject", "net/minecraftforge/registries/DeferredRegister",
            "net/minecraftforge/registries/IForgeRegistry", "net/minecraftforge/registries/ForgeRegistries",
            "net/minecraftforge/registries/ForgeRegistries$Keys", "net/minecraftforge/registries/RegisterEvent",
            "net/minecraftforge/registries/RegisterEvent$RegisterHelper", "net/minecraftforge/fml/common/Mod",
            "net/minecraftforge/fml/common/Mod$EventBusSubscriber",
            "net/minecraftforge/fml/common/Mod$EventBusSubscriber$Bus",
            "net/minecraftforge/eventbus/api/IEventBus", "net/minecraftforge/eventbus/api/SubscribeEvent",
            "net/minecraftforge/eventbus/api/EventPriority",
            "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext",
            "net/minecraftforge/fml/config/IConfigSpec", "net/minecraftforge/api/distmarker/Dist",
            "net/minecraftforge/api/distmarker/OnlyIn",
            "net/minecraftforge/fml/event/lifecycle/FMLCommonSetupEvent",
            "net/minecraftforge/fml/event/lifecycle/FMLClientSetupEvent",
            "net/minecraftforge/fml/event/lifecycle/FMLDedicatedServerSetupEvent",
            "net/minecraftforge/common/util/LazyOptional", "net/minecraftforge/common/util/NonNullConsumer",
            "net/minecraftforge/common/util/NonNullFunction", "net/minecraftforge/common/util/NonNullSupplier",
            "net/minecraftforge/common/util/NonNullPredicate",
            "net/minecraftforge/common/extensions/IForgeItem", "net/minecraft/world/item/DyeableLeatherItem",
            "net/minecraft/world/item/ArmorMaterial",
            "net/minecraftforge/client/model/data/ModelData",
            "net/minecraftforge/client/model/data/ModelData$Builder",
            "net/minecraftforge/client/model/data/ModelProperty",
            "net/minecraftforge/common/extensions/IForgeBlock",
            "net/minecraftforge/items/IItemHandler", "net/minecraftforge/items/IItemHandlerModifiable",
            "net/minecraftforge/items/SlotItemHandler",
            "net/minecraftforge/registries/RegistryBuilder", "net/minecraft/world/level/block/StemGrownBlock",
            "net/minecraft/world/level/block/SandBlock", "net/minecraftforge/event/entity/living/LootingLevelEvent",
            "net/minecraftforge/client/event/EntityRenderersEvent$AddLayers",
            "net/minecraftforge/common/util/INBTSerializable",
            "net/minecraftforge/common/ForgeConfigSpec", "net/minecraftforge/common/ForgeConfigSpec$Builder",
            "net/minecraftforge/common/ForgeConfigSpec$ConfigValue", "net/minecraftforge/common/ForgeConfigSpec$BooleanValue",
            "net/minecraftforge/common/ForgeConfigSpec$IntValue", "net/minecraftforge/common/ForgeConfigSpec$LongValue",
            "net/minecraftforge/common/ForgeConfigSpec$DoubleValue", "net/minecraftforge/common/ForgeConfigSpec$EnumValue",
            "net/minecraftforge/fml/config/ModConfig", "net/minecraftforge/fml/config/ModConfig$Type",
            "net/minecraftforge/fml/event/config/ModConfigEvent", "net/minecraftforge/fml/event/config/ModConfigEvent$Loading",
            "net/minecraftforge/fml/event/config/ModConfigEvent$Reloading", "net/minecraftforge/common/MinecraftForge",
            "net/minecraftforge/fml/ModLoadingContext", "net/minecraftforge/fml/ModContainer",
            "net/minecraftforge/fml/ModList", "net/minecraftforge/fml/loading/FMLEnvironment",
            "net/minecraftforge/fml/loading/FMLPaths", "net/minecraftforge/fml/LogicalSide",
            "net/minecraftforge/fml/DistExecutor", "net/minecraftforge/fml/DistExecutor$SafeReferent",
            "net/minecraftforge/fml/DistExecutor$SafeCallable", "net/minecraftforge/fml/DistExecutor$SafeSupplier",
            "net/minecraftforge/fml/DistExecutor$SafeRunnable");

    private static List<String> owners() {
        List<String> all = new ArrayList<>(OWNERS);
        for (ForgeOwnerMappings.Entry e : GeneratedOwnerTable.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : ConditionMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : NetworkMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : ItemAbilityAndFluidMappings.entries()) all.add(e.oldOwner());
        return all;
    }

    private static Class<?> load(String internal) throws ClassNotFoundException {
        String name = internal.replace('/', '.');
        try {
            return Class.forName(name, false, ForgeOwnerMappingTargetsTest.class.getClassLoader());
        } catch (LinkageError e) {
            // Other tests' app-parented loaders may already have defined the same NeoForge classes.
            return Class.forName(name, false, ClassLoader.getSystemClassLoader());
        }
    }

    @Test
    void everyMappedOwnerIsPresentAndTargetsExist() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        for (String owner : owners()) {
            assertNotNull(mappings.get(owner), "no mapping for " + owner);
            assertDoesNotThrow(() -> load(mappings.get(owner).newOwner()), "missing target for " + owner);
        }
    }

    @Test
    void capabilityProviderUsesLinkOnlyCompatWhileOtherCapabilityTypesRemainUnmapped() {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        assertEquals("net/neoforged/forgestuck/compat/LegacyICapabilityProvider",
                mappings.get("net/minecraftforge/common/capabilities/ICapabilityProvider").newOwner());
        for (String n : List.of("Capability", "CapabilityManager", "ICapabilitySerializable", "CapabilityToken")) {
            assertNull(mappings.get("net/minecraftforge/common/capabilities/" + n), n);
        }
    }

    @Test
    void everyWhitelistedMemberExistsOnTarget() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        List<String> missing = new ArrayList<>();
        for (String owner : owners()) {
            ForgeOwnerMappings.Entry e = mappings.get(owner);
            if (e.supportedMembers() == null) continue;
            Class<?> target = load(e.newOwner());
            for (String member : e.supportedMembers()) {
                int paren = member.indexOf('(');
                boolean isMethod = paren >= 0;
                String name;
                String desc;
                if (isMethod) {
                    name = member.substring(0, paren);
                    desc = member.substring(paren);
                } else {
                    java.util.regex.Matcher fm = FIELD.matcher(member);
                    assertTrue(fm.matches(), "unparsable field member " + member);
                    name = fm.group(1);
                    desc = fm.group(2);
                }
                desc = mappings.remapDescriptorTokens(desc, new HashSet<>(), new HashMap<>());
                if (LegacyNameMappings.looksLikeSrg(name)) {
                    String mapped = LegacyNameMappings.defaults().map(e.newOwner(), name, desc);
                    assertNotNull(mapped, "missing SRG mapping for " + e.newOwner() + "." + name + desc);
                    name = mapped;
                }
                boolean ok = !isMethod ? hasField(target, name, desc)
                        : name.equals("<init>") ? hasConstructor(target, desc) : hasMethod(target, name, desc);
                if (!ok) {
                    missing.add(e.newOwner() + "." + name + desc);
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    private static final java.util.regex.Pattern FIELD =
            java.util.regex.Pattern.compile("^(.+?)(\\[*(?:[ZBCSIJFD]|L(?:net|java|com|org)/[^;]+;))$");

    private static boolean hasConstructor(Class<?> c, String desc) {
        for (java.lang.reflect.Constructor<?> k : c.getDeclaredConstructors()) {
            int mod = k.getModifiers();
            boolean subclassOnly = java.lang.reflect.Modifier.isProtected(mod)
                    && java.lang.reflect.Modifier.isAbstract(c.getModifiers());
            if ((java.lang.reflect.Modifier.isPublic(mod) || subclassOnly)
                    && Type.getConstructorDescriptor(k).equals(desc)) return true;
        }
        return false;
    }

    private static boolean hasMethod(Class<?> c, String name, String desc) {
        List<Method> all = new ArrayList<>(List.of(c.getMethods()));
        all.addAll(List.of(c.getDeclaredMethods()));
        for (Method m : all) if (m.getName().equals(name) && Type.getMethodDescriptor(m).equals(desc)) return true;
        return false;
    }

    private static boolean hasField(Class<?> c, String name, String desc) {
        for (Field f : c.getFields()) if (f.getName().equals(name) && Type.getDescriptor(f.getType()).equals(desc)) return true;
        return false;
    }
}
