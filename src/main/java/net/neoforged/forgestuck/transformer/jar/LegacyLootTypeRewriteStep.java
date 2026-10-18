package net.neoforged.forgestuck.transformer.jar;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Rewrites {@code new LootXxxType(Serializer)} (1.20; the 1.21 constructors take a {@code MapCodec}) into
 * {@code LegacyLootBridge.xxxType(serializer)}. Must run before owner remapping, while the descriptor still names
 * the vanilla 1.20 {@code Serializer}.
 */
public final class LegacyLootTypeRewriteStep implements ClassTransformStep {
    static final String SERIALIZER = "net/minecraft/world/level/storage/loot/Serializer";
    static final String COMPAT_SERIALIZER = "net/neoforged/forgestuck/compat/LegacyLootSerializer";
    static final String BRIDGE = "net/neoforged/forgestuck/compat/LegacyLootBridge";
    private static final String LOOT = "net/minecraft/world/level/storage/loot/";
    private static final String CONDITION_ARRAY = "[L" + LOOT + "predicates/LootItemCondition;";
    private static final String FUNCTION_ARRAY = "[L" + LOOT + "functions/LootItemFunction;";
    private static final Map<String, String> FACTORIES = Map.of(
            LOOT + "functions/LootItemFunctionType", "functionType",
            LOOT + "predicates/LootItemConditionType", "conditionType",
            LOOT + "providers/nbt/LootNbtProviderType", "nbtProviderType",
            LOOT + "entries/LootPoolEntryType", "entryType",
            LOOT + "providers/number/LootNumberProviderType", "numberProviderType",
            LOOT + "providers/score/LootScoreProviderType", "scoreProviderType");

    @Override
    public byte[] apply(String className, byte[] bytes) {
        if (!contains(bytes, SERIALIZER)
                && !contains(bytes, LOOT + "entries/LootPoolEntryContainer")
                && !contains(bytes, LOOT + "entries/LootPoolSingletonContainer")
                && !contains(bytes, LOOT + "functions/LootItemConditionalFunction")) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean modified = false;
        for (MethodNode method : node.methods) {
            if (method.instructions == null) continue;
            modified |= rewrite(method.instructions);
            modified |= rewriteLegacyFields(method.instructions);
        }
        if (!modified) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean rewriteLegacyFields(InsnList insns) {
        boolean modified = false;
        for (AbstractInsnNode insn : insns.toArray()) {
            if (!(insn instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETFIELD) continue;
            String helper = switch (field.owner + "." + field.name + field.desc) {
                case LOOT + "entries/LootPoolEntryContainer.conditions" + CONDITION_ARRAY -> "conditions";
                case LOOT + "entries/LootPoolSingletonContainer.functions" + FUNCTION_ARRAY -> "functions";
                case LOOT + "functions/LootItemConditionalFunction.predicates" + CONDITION_ARRAY -> "predicates";
                default -> null;
            };
            if (helper == null) continue;
            insns.set(field, new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, helper,
                    "(Ljava/lang/Object;)" + field.desc, false));
            modified = true;
        }
        return modified;
    }

    private static boolean rewrite(InsnList insns) {
        // pair every NEW with its constructor call by nesting depth, then drop NEW/DUP and call the factory
        Deque<TypeInsnNode> open = new ArrayDeque<>();
        boolean modified = false;
        for (AbstractInsnNode insn : insns.toArray()) {
            if (insn instanceof TypeInsnNode t && t.getOpcode() == Opcodes.NEW) {
                open.push(t);
            } else if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                    && call.name.equals("<init>") && !open.isEmpty() && open.peek().desc.equals(call.owner)) {
                TypeInsnNode created = open.pop();
                String factory = FACTORIES.get(call.owner);
                if (factory == null || !call.desc.equals("(L" + SERIALIZER + ";)V")) continue;
                AbstractInsnNode dup = created.getNext();
                if (dup == null || dup.getOpcode() != Opcodes.DUP) continue;
                insns.remove(dup);
                insns.remove(created);
                insns.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, factory,
                        "(L" + COMPAT_SERIALIZER + ";)L" + call.owner + ";", false));
                modified = true;
            }
        }
        return modified;
    }

    private static boolean contains(byte[] bytes, String needle) {
        byte[] n = needle.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        outer:
        for (int i = 0; i <= bytes.length - n.length; i++) {
            for (int j = 0; j < n.length; j++) {
                if (bytes[i + j] != n[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
