package net.neoforged.forgestuck.transformer.jar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Repairs static field references to Minecraft classes whose field was renamed or retyped between 1.20.1 and 1.21.1
 * and where the legacy value can be reproduced exactly. Runs after {@link SrgMemberRemapStep}, so names are already
 * Mojang names.
 * <ul>
 * <li>{@code Holder<T>} fields that used to be plain {@code T} ({@code MobEffects.*}, {@code Attributes.*},
 * {@code Potions.*}, {@code SoundEvents.*}, {@code GameEvent.*}, table {@code game-field-holders.txt} generated from
 * the 1.21.1 jar): the access reads the Holder and appends {@code Holder.value()} + checkcast, which is the value the
 * legacy field held.</li>
 * <li>{@code BuiltInLootTables.EMPTY}: {@code ResourceLocation} became {@code ResourceKey<LootTable>}; {@code location()}.</li>
 * <li>{@code BuiltInRegistries.POTION}: {@code DefaultedRegistry} became a plain {@code Registry}. The access is
 * retyped and every call that names {@code DefaultedRegistry} as owner (except {@code getDefaultKey}) is re-owned to
 * {@code Registry}, which declares the same erased methods, so it works on both kinds of registry.</li>
 * <li>Renamed enum constants with identical meaning/ordinal ({@code AttributeModifier.Operation}, {@code EquipmentSlot.Type.ARMOR}).</li>
 * <li>{@code Enchantments.*}: now {@code ResourceKey<Enchantment>} (enchantments are data-driven); there is no
 * registry-less equivalent, so such an access fails when executed.</li>
 * </ul>
 */
public final class GameFieldRepairStep implements ClassTransformStep {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgestuck");
    private static final String HOLDER = "net/minecraft/core/Holder";
    private static final String DEFAULTED = "net/minecraft/core/DefaultedRegistry";
    private static final String REGISTRY = "net/minecraft/core/Registry";
    private static final Map<String, String> HOLDERS = loadHolders();
    private static final Map<String, String> RENAMES = Map.of(
            "net/minecraft/world/entity/ai/attributes/AttributeModifier$Operation.ADDITION", "ADD_VALUE",
            "net/minecraft/world/entity/ai/attributes/AttributeModifier$Operation.MULTIPLY_BASE", "ADD_MULTIPLIED_BASE",
            "net/minecraft/world/entity/ai/attributes/AttributeModifier$Operation.MULTIPLY_TOTAL", "ADD_MULTIPLIED_TOTAL",
            "net/minecraft/world/entity/EquipmentSlot$Type.ARMOR", "HUMANOID_ARMOR");

    private static Map<String, String> loadHolders() {
        Map<String, String> map = new HashMap<>();
        try (InputStream in = GameFieldRepairStep.class.getResourceAsStream("/forgestuck/game-field-holders.txt")) {
            if (in == null) return map;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                if (line.isBlank() || line.startsWith("#")) continue;
                int eq = line.lastIndexOf('=');
                map.put(line.substring(0, eq), line.substring(eq + 1));
            }
        } catch (IOException e) {
            throw new IllegalStateException("game-field-holders.txt unreadable", e);
        }
        return map;
    }

    @Override
    public byte[] apply(String className, byte[] bytes) {
        if (bytes == null || className.startsWith("net.neoforged.forgestuck")) return bytes;
        String raw = new String(bytes, StandardCharsets.ISO_8859_1);
        if (!raw.contains("net/minecraft/")) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        TreeSet<String> repaired = new TreeSet<>();
        TreeSet<String> failed = new TreeSet<>();
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call && call.owner.equals(DEFAULTED) && call.getOpcode() == Opcodes.INVOKEINTERFACE
                        && !call.name.equals("getDefaultKey")) {
                    call.owner = REGISTRY;
                    repaired.add("DefaultedRegistry." + call.name);
                } else if (insn instanceof FieldInsnNode f && f.getOpcode() == Opcodes.GETSTATIC && f.owner.startsWith("net/minecraft/")) {
                    repairField(m, f, node.name, repaired, failed);
                }
            }
        }
        if (repaired.isEmpty() && failed.isEmpty()) return bytes;
        if (!repaired.isEmpty()) LOGGER.info("[Forge Stuck!!] repaired {} retyped/renamed game field/member reference(s) in {}: {}", repaired.size(), className, repaired);
        if (!failed.isEmpty()) LOGGER.warn("[Forge Stuck!!] {} reads game field(s) with no 1.21.1 equivalent value; they throw when executed: {}", className, failed);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void repairField(MethodNode m, FieldInsnNode f, String self, TreeSet<String> repaired, TreeSet<String> failed) {
        String key = f.owner + "." + f.name;
        String renamed = RENAMES.get(key);
        if (renamed != null) {
            f.name = renamed;
            repaired.add(key + "->" + renamed);
            return;
        }
        String targetDesc = HOLDERS.get(key + "|" + f.desc);
        if (targetDesc != null) {
            String legacy = f.desc.substring(1, f.desc.length() - 1);
            f.desc = targetDesc;
            InsnList add = new InsnList();
            add.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, HOLDER, "value", "()Ljava/lang/Object;", true));
            add.add(new TypeInsnNode(Opcodes.CHECKCAST, legacy));
            m.instructions.insert(f, add);
            repaired.add(key + " (Holder.value())");
            return;
        }
        if (key.equals("net/minecraft/world/level/storage/loot/BuiltInLootTables.EMPTY")
                && f.desc.equals("Lnet/minecraft/resources/ResourceLocation;")) {
            f.desc = "Lnet/minecraft/resources/ResourceKey;";
            m.instructions.insert(f, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/resources/ResourceKey", "location",
                    "()Lnet/minecraft/resources/ResourceLocation;", false));
            repaired.add(key + " (ResourceKey.location())");
        } else if (key.equals("net/minecraft/core/registries/BuiltInRegistries.POTION")
                && f.desc.equals("Lnet/minecraft/core/DefaultedRegistry;")) {
            f.desc = "Lnet/minecraft/core/Registry;";
            repaired.add(key + " (DefaultedRegistry -> Registry)");
        } else if (f.owner.equals("net/minecraft/world/item/enchantment/Enchantments")
                && f.desc.equals("Lnet/minecraft/world/item/enchantment/Enchantment;")) {
            ForgeOwnerRemapStep.failAtCall(m, f, key + " (1.21.1 enchantments are data-driven ResourceKeys; no registry-less Enchantment instance exists)",
                    self + "." + m.name);
            failed.add(key);
        }
    }
}
