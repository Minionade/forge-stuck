package net.neoforged.forgestuck.transformer.jar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Expands legacy base-event subscriptions, preserving the original callable method. */
public final class LegacyRenderLivingListenerStep implements ClassTransformStep {
    private static final String OLD = "net/minecraftforge/client/event/RenderLivingEvent";
    private static final String NATIVE = "net/neoforged/neoforge/client/event/RenderLivingEvent";
    private static final Set<String> SUBSCRIBE = Set.of("Lnet/minecraftforge/eventbus/api/SubscribeEvent;", "Lnet/neoforged/bus/api/SubscribeEvent;");
    private static final String BUS = "net/minecraftforge/eventbus/api/IEventBus";
    private static final String PRIORITY = "Lnet/minecraftforge/eventbus/api/EventPriority;";
    private static final Set<String> REGISTRATIONS = Set.of(
            "(Ljava/util/function/Consumer;)V",
            "(" + PRIORITY + "Ljava/util/function/Consumer;)V",
            "(" + PRIORITY + "ZLjava/util/function/Consumer;)V",
            "(Ljava/lang/Class;Ljava/util/function/Consumer;)V",
            "(" + PRIORITY + "Ljava/lang/Class;Ljava/util/function/Consumer;)V",
            "(" + PRIORITY + "ZLjava/lang/Class;Ljava/util/function/Consumer;)V");

    @Override
    public byte[] apply(String className, byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        if (has(node.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;")
                || has(node.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;")) return bytes;
        boolean changed = false;
        List<MethodNode> generated = new ArrayList<>();
        int counter = 0;
        for (MethodNode method : node.methods) {
            String base = method.desc.equals("(L" + OLD + ";)V") ? OLD
                    : method.desc.equals("(L" + NATIVE + ";)V") ? NATIVE : null;
            if (base != null && (annotated(method.visibleAnnotations) || annotated(method.invisibleAnnotations))) {
                for (String phase : List.of("Pre", "Post")) {
                    String name;
                    do { name = "forgestuck$renderLiving$" + phase + "$" + counter++; }
                    while (hasName(node.methods, name) || hasName(generated, name));
                    MethodNode bridge = new MethodNode(Opcodes.ACC_PUBLIC | (method.access & Opcodes.ACC_STATIC),
                            name, "(L" + base + "$" + phase + ";)V", null,
                            method.exceptions == null ? null : method.exceptions.toArray(String[]::new));
                    bridge.visibleAnnotations = copySubscriptions(method.visibleAnnotations);
                    bridge.invisibleAnnotations = copySubscriptions(method.invisibleAnnotations);
                    boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
                    if (!isStatic) bridge.visitVarInsn(Opcodes.ALOAD, 0);
                    bridge.visitVarInsn(Opcodes.ALOAD, isStatic ? 0 : 1);
                    boolean itf = (node.access & Opcodes.ACC_INTERFACE) != 0;
                    int opcode = isStatic ? Opcodes.INVOKESTATIC : (method.access & Opcodes.ACC_PRIVATE) != 0
                            ? Opcodes.INVOKESPECIAL : itf ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL;
                    bridge.visitMethodInsn(opcode, node.name, method.name, method.desc, itf);
                    bridge.visitInsn(Opcodes.RETURN);
                    bridge.visitMaxs(isStatic ? 1 : 2, isStatic ? 1 : 2);
                    bridge.visitEnd();
                    generated.add(bridge);
                }
                removeSubscriptions(method.visibleAnnotations);
                removeSubscriptions(method.invisibleAnnotations);
                changed = true;
            }
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)) continue;
                if (call.owner.equals(BUS) && call.name.equals("addListener") && REGISTRATIONS.contains(call.desc)) {
                    call.desc = "(L" + BUS + ";" + call.desc.substring(1);
                    call.owner = "net/neoforged/forgestuck/compat/LegacyRenderListenerRegistration";
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.itf = false;
                    changed = true;
                } else if (Set.of(OLD, OLD + "$Pre", OLD + "$Post", NATIVE, NATIVE + "$Pre", NATIVE + "$Post").contains(call.owner)
                        && Set.of("isCanceled()Z", "setCanceled(Z)V", "isCancelable()Z", "hasResult()Z").contains(call.name + call.desc)) {
                    call.desc = "(L" + NATIVE + ";" + call.desc.substring(1);
                    call.owner = "net/neoforged/forgestuck/compat/LegacyRenderLivingAccess";
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.itf = false;
                    changed = true;
                }
            }
        }
        if (!changed) return bytes;
        node.methods.addAll(generated);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
    private static boolean hasName(List<MethodNode> methods, String name) { return methods.stream().anyMatch(m -> m.name.equals(name)); }
    private static boolean has(List<AnnotationNode> annotations, String descriptor) {
        return annotations != null && annotations.stream().anyMatch(a -> a.desc.equals(descriptor));
    }
    private static boolean annotated(List<AnnotationNode> annotations) {
        return annotations != null && annotations.stream().anyMatch(a -> SUBSCRIBE.contains(a.desc));
    }
    private static List<AnnotationNode> copySubscriptions(List<AnnotationNode> annotations) {
        if (annotations == null) return null;
        List<AnnotationNode> copy = new ArrayList<>();
        for (AnnotationNode a : annotations) if (SUBSCRIBE.contains(a.desc)) {
            AnnotationNode duplicate = new AnnotationNode(a.desc);
            a.accept(duplicate);
            copy.add(duplicate);
        }
        return copy;
    }
    private static void removeSubscriptions(List<AnnotationNode> annotations) {
        if (annotations != null) annotations.removeIf(a -> SUBSCRIBE.contains(a.desc));
    }
}
