package net.neoforged.forgestuck.compat;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Forge 1.20.1 {@code ForgeMod}: only members that have a NeoForge 1.21.1 counterpart. The legacy fields were
 * {@code RegistryObject}s (compat {@link RegistryObject}); NeoForge declares them as plain {@code Holder}s, so each
 * is re-created from the target's registry key. The legacy gravity attribute is provided by vanilla in 1.21.1.
 */
public final class LegacyForgeMod {
    private LegacyForgeMod() {}

    public static final RegistryObject<FluidType> LAVA_TYPE = holder(NeoForgeMod.LAVA_TYPE.getKey());
    public static final RegistryObject<FluidType> WATER_TYPE = holder(NeoForgeMod.WATER_TYPE.getKey());
    public static final RegistryObject<Attribute> SWIM_SPEED = holder(NeoForgeMod.SWIM_SPEED.getKey());
    public static final RegistryObject<Attribute> NAMETAG_DISTANCE = holder(
            NeoForgeMod.NAMETAG_DISTANCE.getKey());
    public static final RegistryObject<Attribute> ENTITY_GRAVITY = holder(
            Attributes.GRAVITY.unwrapKey().orElseThrow(() -> new IllegalStateException("Gravity attribute has no key")));

    @SuppressWarnings("unchecked")
    private static <T> RegistryObject<T> holder(net.minecraft.resources.ResourceKey<?> key) {
        return new RegistryObject<>((net.minecraft.resources.ResourceKey<T>) key) {};
    }

    public static void enableMilkFluid() {
        NeoForgeMod.enableMilkFluid();
    }
}