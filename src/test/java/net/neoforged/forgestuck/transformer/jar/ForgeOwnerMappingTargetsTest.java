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
            "net/minecraftforge/common/util/NonNullPredicate");

    private static Class<?> load(String internal) throws ClassNotFoundException {
        return Class.forName(internal.replace('/', '.'), false, ForgeOwnerMappingTargetsTest.class.getClassLoader());
    }

    @Test
    void everyMappedOwnerIsPresentAndTargetsExist() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        for (String owner : OWNERS) {
            assertNotNull(mappings.get(owner), "no mapping for " + owner);
            assertDoesNotThrow(() -> load(mappings.get(owner).newOwner()), "missing target for " + owner);
        }
    }

    @Test
    void capabilityApiIsNotMappedToUnrelatedTypes() {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        for (String n : List.of("Capability", "CapabilityManager", "ICapabilityProvider", "ICapabilitySerializable",
                "CapabilityToken")) {
            assertNull(mappings.get("net/minecraftforge/common/capabilities/" + n), n);
        }
    }

    @Test
    void everyWhitelistedMemberExistsOnTarget() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        List<String> missing = new ArrayList<>();
        for (String owner : OWNERS) {
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
                    int l = member.indexOf("Lnet/");
                    name = member.substring(0, l);
                    desc = member.substring(l);
                }
                desc = mappings.remapDescriptorTokens(desc, new HashSet<>(), new HashMap<>());
                if (isMethod ? !hasMethod(target, name, desc) : !hasField(target, name, desc)) {
                    missing.add(e.newOwner() + "." + name + desc);
                }
            }
        }
        assertEquals(List.of(), missing);
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
