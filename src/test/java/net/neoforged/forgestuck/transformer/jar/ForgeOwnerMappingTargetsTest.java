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
            "net/minecraftforge/event/entity/living/LivingDamageEvent",
            "net/minecraftforge/common/SoundAction", "net/minecraftforge/common/SoundActions",
            "net/minecraftforge/common/Tags$Items", "net/minecraftforge/common/Tags$Blocks",
            "net/minecraft/core/particles/ParticleOptions",
            "net/minecraft/core/particles/ParticleOptions$Deserializer",
            "net/minecraftforge/common/loot/LootModifier",
            "net/minecraft/world/level/storage/loot/entries/LootPoolEntryContainer$Serializer",
            "net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer$Serializer",
            "net/minecraft/world/level/storage/loot/functions/LootItemConditionalFunction$Serializer",
            "net/minecraftforge/event/AttachCapabilitiesEvent",
            "net/minecraftforge/common/capabilities/ICapabilityProvider",
            "net/minecraft/world/level/pathfinder/BlockPathTypes",
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
        for (ForgeOwnerMappings.Entry e : TickMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : ConditionMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : DataMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : ModelGeneratorMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : ClientModelMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : CommonHookMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : FluidUtilityMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : WorldGenerationMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : ItemHandlerMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : NetworkMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : ItemAbilityAndFluidMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : KeyConflictMappings.entries()) all.add(e.oldOwner());
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
    void mappedForgeTagFieldsExistOnNeoForge() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        List<String> missing = new ArrayList<>();
        for (String owner : List.of("net/minecraftforge/common/Tags$Items",
                "net/minecraftforge/common/Tags$Blocks")) {
            ForgeOwnerMappings.Entry entry = mappings.get(owner);
            Class<?> target = load(entry.newOwner());
            for (String member : entry.supportedMembers()) {
                var matcher = FIELD.matcher(member);
                assertTrue(matcher.matches(), "invalid field mapping " + member);
                if (!hasField(target, matcher.group(1), matcher.group(2))) {
                    missing.add(entry.newOwner() + "." + member);
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void mappedItemFluidHandlerMethodsExistOnNeoForge() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        ForgeOwnerMappings.Entry entry = mappings.get("net/minecraftforge/fluids/capability/IFluidHandlerItem");
        Class<?> target = load(entry.newOwner());
        for (String member : entry.supportedMembers()) {
            int paren = member.indexOf('(');
            String name = member.substring(0, paren);
            String descriptor = mappings.remapDescriptorTokens(member.substring(paren), new HashSet<>(),
                    new HashMap<>());
            boolean exists = name.equals("<init>") ? hasConstructor(target, descriptor)
                    : hasMethod(target, name, descriptor);
            assertTrue(exists, "missing target method " + name + descriptor);
        }
    }

    @Test
    void mappedClientFluidExtensionMethodsExistOnNeoForge() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        ForgeOwnerMappings.Entry entry = GeneratedOwnerTable.entries().stream()
                .filter(candidate -> candidate.oldOwner().equals(
                        "net/minecraftforge/client/extensions/common/IClientFluidTypeExtensions"))
                .findFirst().orElseThrow();
        Class<?> target = load(entry.newOwner());
        for (String member : entry.supportedMembers()) {
            int paren = member.indexOf('(');
            String name = member.substring(0, paren);
            String descriptor = mappings.remapDescriptorTokens(member.substring(paren), new HashSet<>(),
                    new HashMap<>());
            boolean exists = name.equals("<init>") ? hasConstructor(target, descriptor)
                    : hasMethod(target, name, descriptor);
            assertTrue(exists, "missing target method " + name + descriptor);
        }
    }

    @Test
    void mappedFluidStackMembersExistOnNeoForge() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        ForgeOwnerMappings.Entry entry = mappings.get("net/minecraftforge/fluids/FluidStack");
        Class<?> target = load(entry.newOwner());
        for (String member : entry.supportedMembers()) {
            int paren = member.indexOf('(');
            String name;
            String descriptor;
            boolean method = paren >= 0;
            if (method) {
                name = member.substring(0, paren);
                descriptor = member.substring(paren);
            } else {
                var matcher = FIELD.matcher(member);
                assertTrue(matcher.matches(), "invalid field mapping " + member);
                name = matcher.group(1);
                descriptor = matcher.group(2);
            }
            descriptor = mappings.remapDescriptorTokens(descriptor, new HashSet<>(), new HashMap<>());
            boolean exists = method
                    ? name.equals("<init>") ? hasConstructor(target, descriptor) : hasMethod(target, name, descriptor)
                    : hasField(target, name, descriptor);
            assertTrue(exists, "missing target member " + entry.newOwner() + "." + name + descriptor);
        }
    }

    @Test
    void mappedFluidTypeStackOverloadsExistOnNeoForge() throws Exception {
        ForgeOwnerMappings.Entry entry = ConditionMappings.entries().stream()
                .filter(candidate -> candidate.oldOwner().equals("net/minecraftforge/fluids/FluidType"))
                .findFirst().orElseThrow();
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        Class<?> target = load(entry.newOwner());
        for (String member : entry.supportedMembers()) {
            int paren = member.indexOf('(');
            if (paren < 0 || member.startsWith("<init>")) continue;
            String name = member.substring(0, paren);
            String descriptor = mappings.remapDescriptorTokens(member.substring(paren), new HashSet<>(),
                    new HashMap<>());
            assertTrue(hasMethod(target, name, descriptor), "missing target method " + name + descriptor);
        }
    }

    @Test
    void mappedModelGeneratorMembersExistOnNeoForge() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        List<String> missing = new ArrayList<>();
        for (ForgeOwnerMappings.Entry entry : ModelGeneratorMappings.entries()) {
            Class<?> target = load(entry.newOwner());
            for (String member : entry.supportedMembers()) {
                int paren = member.indexOf('(');
                String name;
                String descriptor;
                boolean method = paren >= 0;
                if (method) {
                    name = member.substring(0, paren);
                    descriptor = member.substring(paren);
                } else {
                    var matcher = FIELD.matcher(member);
                    assertTrue(matcher.matches(), "invalid field mapping " + member);
                    name = matcher.group(1);
                    descriptor = matcher.group(2);
                }
                descriptor = mappings.remapDescriptorTokens(descriptor, new HashSet<>(), new HashMap<>());
                boolean exists = method
                        ? name.equals("<init>") ? hasConstructor(target, descriptor) : hasMethod(target, name, descriptor)
                        : hasField(target, name, descriptor);
                if (!exists) missing.add(entry.newOwner() + "." + name + descriptor);
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void mappedItemHandlerHelperMembersExistOnNeoForge() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        List<String> missing = new ArrayList<>();
        for (ForgeOwnerMappings.Entry entry : ItemHandlerMappings.entries()) {
            Class<?> target = load(entry.newOwner());
            for (String member : entry.supportedMembers()) {
                int paren = member.indexOf('(');
                String name = member.substring(0, paren);
                String descriptor = mappings.remapDescriptorTokens(member.substring(paren), new HashSet<>(),
                        new HashMap<>());
                boolean exists = name.equals("<init>") ? hasConstructor(target, descriptor)
                        : hasMethod(target, name, descriptor);
                if (!exists) {
                    missing.add(entry.newOwner() + "." + name + descriptor);
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void mappedClientModelMembersExistOnNeoForge() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        List<String> missing = new ArrayList<>();
        for (ForgeOwnerMappings.Entry entry : ClientModelMappings.entries()) {
            Class<?> target = load(entry.newOwner());
            for (String member : entry.supportedMembers()) {
                int paren = member.indexOf('(');
                String name;
                String descriptor;
                boolean method = paren >= 0;
                if (method) {
                    name = member.substring(0, paren);
                    descriptor = member.substring(paren);
                } else {
                    var matcher = FIELD.matcher(member);
                    assertTrue(matcher.matches(), "invalid field mapping " + member);
                    name = matcher.group(1);
                    descriptor = matcher.group(2);
                }
                descriptor = mappings.remapDescriptorTokens(descriptor, new HashSet<>(), new HashMap<>());
                boolean exists = method
                        ? name.equals("<init>") ? hasConstructor(target, descriptor) : hasMethod(target, name, descriptor)
                        : hasField(target, name, descriptor);
                if (!exists) missing.add(entry.newOwner() + "." + name + descriptor);
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void mappedFluidUtilityMembersExistOnNeoForge() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        List<String> missing = new ArrayList<>();
        for (ForgeOwnerMappings.Entry entry : FluidUtilityMappings.entries()) {
            Class<?> target = load(entry.newOwner());
            for (String member : entry.supportedMembers()) {
                int paren = member.indexOf('(');
                String name;
                String descriptor;
                boolean method = paren >= 0;
                if (method) {
                    name = member.substring(0, paren);
                    descriptor = member.substring(paren);
                } else {
                    var matcher = FIELD.matcher(member);
                    assertTrue(matcher.matches(), "invalid field mapping " + member);
                    name = matcher.group(1);
                    descriptor = matcher.group(2);
                }
                descriptor = mappings.remapDescriptorTokens(descriptor, new HashSet<>(), new HashMap<>());
                boolean exists = method
                        ? name.equals("<init>") ? hasConstructor(target, descriptor) : hasMethod(target, name, descriptor)
                        : hasField(target, name, descriptor);
                if (!exists) missing.add(entry.newOwner() + "." + name + descriptor);
            }
        }
        assertEquals(List.of(), missing);
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
