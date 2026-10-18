package net.neoforged.forgestuck.transformer.jar;

import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Keeps native shearable type identity while adapting old callbacks and calls. */
public final class LegacyHurtAndShearingStep implements ClassTransformStep {
    private static final String OLD = "net/minecraftforge/common/IForgeShearable";
    private static final String NATIVE = "net/neoforged/neoforge/common/IShearable";
    private static final String LEGACY = "net/neoforged/forgestuck/compat/LegacyShearable";
    private static final String HELPER = "net/neoforged/forgestuck/compat/LegacyShearing";
    private static final String BRIDGE = "net/neoforged/forgestuck/compat/LegacyRuntimeBootstrap";
    private static final String PLAYER = "Lnet/minecraft/world/entity/player/Player;";
    private static final String ARGS = "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;";
    private static final String CHECK = "(" + ARGS + ")Z";
    private static final String SHEAR = "(" + PLAYER + ARGS + "I)Ljava/util/List;";

    private static boolean oldMethod(String name, String desc) {
        return name.equals("isShearable") && desc.equals(CHECK)
                || name.equals("onSheared") && desc.equals(SHEAR);
    }
    private static String withReceiver(String owner, String desc) { return "(L" + owner + ";" + desc.substring(1); }

    private static boolean annotation(List<AnnotationNode> annotations, String descriptor) {
        return annotations != null && annotations.stream().anyMatch(a -> a.desc.equals(descriptor));
    }

    @Override
    public byte[] apply(String className, byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        if (annotation(node.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;")
                || annotation(node.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;")) return bytes;
        boolean changed = false;
        if (node.interfaces.contains(OLD)) {
            node.interfaces.replaceAll(i -> i.equals(OLD) ? LEGACY : i);
            // Concrete bridges take precedence over inherited native superclass methods.
            if ((node.access & Opcodes.ACC_INTERFACE) == 0) {
                addNativeBridge(node, "isShearable", "(" + PLAYER + ARGS + ")Z", "nativeIsShearable");
                addNativeBridge(node, "onSheared", "(" + PLAYER + ARGS + ")Ljava/util/List;", "nativeOnSheared");
            }
            changed = true;
        }
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(OLD)
                        && oldMethod(call.name, call.desc)) {
                    if (call.getOpcode() == Opcodes.INVOKESPECIAL) {
                        // IForgeShearable.super: call the legacy default, not the overriding callback.
                        call.owner = LEGACY;
                    } else {
                        call.desc = withReceiver(NATIVE, call.desc);
                        call.owner = HELPER;
                        call.setOpcode(Opcodes.INVOKESTATIC);
                        call.itf = false;
                    }
                    changed = true;
                } else if (instruction instanceof InvokeDynamicInsnNode indy) {
                    for (int i = 0; i < indy.bsmArgs.length; i++) {
                        if (indy.bsmArgs[i] instanceof Handle h) {
                            Handle mapped = adaptHandle(h);
                            if (!mapped.equals(h)) { indy.bsmArgs[i] = mapped; changed = true; }
                        }
                    }
                } else if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof Handle h) {
                    Handle mapped = adaptHandle(h);
                    if (!mapped.equals(h)) { ldc.cst = mapped; changed = true; }
                }
            }
        }
        boolean entrypoint = annotation(node.visibleAnnotations, "Lnet/minecraftforge/fml/common/Mod;")
                || annotation(node.visibleAnnotations, "Lnet/neoforged/fml/common/Mod;")
                || annotation(node.invisibleAnnotations, "Lnet/minecraftforge/fml/common/Mod;")
                || annotation(node.invisibleAnnotations, "Lnet/neoforged/fml/common/Mod;");
        if (entrypoint) {
            MethodNode init = node.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElse(null);
            if (init == null) {
                init = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
                init.instructions.add(new InsnNode(Opcodes.RETURN));
                node.methods.add(init);
            }
            boolean present = java.util.Arrays.stream(init.instructions.toArray()).anyMatch(i ->
                    i instanceof MethodInsnNode c && c.owner.equals(BRIDGE) && c.name.equals("install"));
            if (!present) init.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "install", "()V", false));
            changed |= !present;
        }
        if (!changed) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static Handle adaptHandle(Handle h) {
        if (!h.getOwner().equals(OLD) || !oldMethod(h.getName(), h.getDesc())) return h;
        if (h.getTag() == Opcodes.H_INVOKESPECIAL) {
            return new Handle(h.getTag(), LEGACY, h.getName(), h.getDesc(), true);
        }
        if (h.getTag() != Opcodes.H_INVOKEINTERFACE && h.getTag() != Opcodes.H_INVOKEVIRTUAL) return h;
        return new Handle(Opcodes.H_INVOKESTATIC, HELPER, h.getName(), withReceiver(NATIVE, h.getDesc()), false);
    }

    private static void addNativeBridge(ClassNode node, String name, String desc, String helper) {
        if (node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(desc))) return;
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, name, desc, null, null);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        int slot = 1;
        for (Type arg : Type.getArgumentTypes(desc)) {
            method.visitVarInsn(arg.getOpcode(Opcodes.ILOAD), slot);
            slot += arg.getSize();
        }
        method.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, helper, withReceiver(LEGACY, desc), false);
        method.visitInsn(Type.getReturnType(desc).getOpcode(Opcodes.IRETURN));
        method.visitMaxs(slot, slot);
        method.visitEnd();
        node.methods.add(method);
    }
}
