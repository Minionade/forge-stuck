package net.neoforged.forgestuck.compat;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public final class LegacyAttributeModifierShim {
    public static final UUID ATTACK_DAMAGE_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    public static final UUID ATTACK_SPEED_UUID = UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");

    private LegacyAttributeModifierShim() {}

    public static ResourceLocation toId(UUID uuid) {
        if (ATTACK_DAMAGE_UUID.equals(uuid)) return ResourceLocation.withDefaultNamespace("base_attack_damage");
        if (ATTACK_SPEED_UUID.equals(uuid)) return ResourceLocation.withDefaultNamespace("base_attack_speed");
        String path = "legacy/" + uuid.toString().replace("-", "");
        return ResourceLocation.fromNamespaceAndPath("forgestuck", path);
    }
}
