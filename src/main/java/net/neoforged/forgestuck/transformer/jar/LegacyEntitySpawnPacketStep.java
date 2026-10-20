package net.neoforged.forgestuck.transformer.jar;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Adapts Forge's entity-only spawn-packet overrides to NeoForge's tracking-aware packet method. */
final class LegacyEntitySpawnPacketStep implements ClassTransformStep {
    private static final String LEGACY_HOOKS = "net/minecraftforge/network/NetworkHooks";
    private static final String LEGACY_METHOD_DESC =
            "()Lnet/minecraft/network/protocol/Packet;";
    private static final String SERVER_ENTITY = "net/minecraft/server/level/ServerEntity";
    private static final String ENTITY = "net/minecraft/world/entity/Entity";
    private static final String PACKET = "net/minecraft/network/protocol/Packet";
    private static final String ADD_ENTITY_PACKET =
            "net/minecraft/network/protocol/game/ClientboundAddEntityPacket";
    private static final String TARGET_METHOD_DESC =
            "(L" + SERVER_ENTITY + ";)L" + PACKET + ";";

    @Override
    public byte[] apply(String className, byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean changed = false;
        for (MethodNode method : node.methods) {
            if ((method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0
                    || !method.desc.equals(LEGACY_METHOD_DESC)
                    || !isLegacySpawnPacketOverride(method)) {
                continue;
            }
            method.name = "getAddEntityPacket";
            method.desc = TARGET_METHOD_DESC;
            method.signature = null;
            method.access |= Opcodes.ACC_PUBLIC;
            method.access &= ~(Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC);
            method.instructions = spawnPacketBody();
            method.tryCatchBlocks.clear();
            method.localVariables = null;
            method.visibleLocalVariableAnnotations = null;
            method.invisibleLocalVariableAnnotations = null;
            method.visibleParameterAnnotations = null;
            method.invisibleParameterAnnotations = null;
            method.parameters = null;
            method.maxStack = 0;
            method.maxLocals = 0;
            changed = true;
        }
        if (!changed) return bytes;

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean isLegacySpawnPacketOverride(MethodNode method) {
        AbstractInsnNode[] instructions = method.instructions.toArray();
        AbstractInsnNode[] meaningful = java.util.Arrays.stream(instructions)
                .filter(instruction -> instruction.getOpcode() >= 0)
                .toArray(AbstractInsnNode[]::new);
        return meaningful.length == 3
                && meaningful[0] instanceof VarInsnNode receiver
                && receiver.getOpcode() == Opcodes.ALOAD && receiver.var == 0
                && meaningful[1] instanceof MethodInsnNode call
                && call.getOpcode() == Opcodes.INVOKESTATIC
                && call.owner.equals(LEGACY_HOOKS)
                && call.name.equals("getEntitySpawningPacket")
                && call.desc.equals("(L" + ENTITY + ";)L" + PACKET + ";")
                && meaningful[2].getOpcode() == Opcodes.ARETURN;
    }

    private static InsnList spawnPacketBody() {
        InsnList instructions = new InsnList();
        instructions.add(new TypeInsnNode(Opcodes.NEW, ADD_ENTITY_PACKET));
        instructions.add(new InsnNode(Opcodes.DUP));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, ADD_ENTITY_PACKET, "<init>",
                "(L" + ENTITY + ";L" + SERVER_ENTITY + ";)V", false));
        instructions.add(new InsnNode(Opcodes.ARETURN));
        return instructions;
    }
}
