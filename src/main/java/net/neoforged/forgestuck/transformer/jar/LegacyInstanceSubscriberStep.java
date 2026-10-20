package net.neoforged.forgestuck.transformer.jar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.Type;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge's automatic {@code @Mod.EventBusSubscriber} registration used {@code bus.register(Class)}, which only
 * registered <em>static</em> {@code @SubscribeEvent} methods and silently ignored instance ones. NeoForge 1.21.1
 * throws for an instance method. Scala singleton module classes have no static forwarders on the class carrying
 * {@code @EventBusSubscriber}, so their handlers are exposed through synthetic static bridges to the module instance.
 * Other instance methods are dropped, matching Forge's class-registration behavior.
 */
public final class LegacyInstanceSubscriberStep implements ClassTransformStep {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgestuck");
    private static final Set<String> SUBSCRIBER = Set.of("Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;",
            "Lnet/neoforged/fml/common/EventBusSubscriber;");
    private static final Set<String> SUBSCRIBE = Set.of("Lnet/minecraftforge/eventbus/api/SubscribeEvent;",
            "Lnet/neoforged/bus/api/SubscribeEvent;");
    private final Set<String> staticSubscribers = ConcurrentHashMap.newKeySet();

    @Override
    public void scan(String className, byte[] bytes, OwnerRemapReport report) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if ((has(node.visibleAnnotations) || has(node.invisibleAnnotations))
                && node.methods.stream().anyMatch(method -> (method.access & Opcodes.ACC_STATIC) != 0
                        && (hasSubscribe(method.visibleAnnotations) || hasSubscribe(method.invisibleAnnotations)))) {
            staticSubscribers.add(className);
        }
    }

    @Override
    public byte[] apply(String className, byte[] bytes) {
        if (bytes == null || className.startsWith("net.neoforged.forgestuck")) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        if (!has(node.visibleAnnotations) && !has(node.invisibleAnnotations)) return bytes;
        List<String> dropped = new ArrayList<>();
        List<MethodNode> bridges = new ArrayList<>();
        boolean scalaModule = node.fields.stream().anyMatch(field -> field.name.equals("MODULE$")
                && (field.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)
                && field.desc.equals("L" + node.name + ";"));
        String companion = className.endsWith("$") ? className.substring(0, className.length() - 1) : "";
        boolean hasStaticCompanion = !companion.isEmpty() && staticSubscribers.contains(companion);
        for (MethodNode m : node.methods) {
            if ((m.access & Opcodes.ACC_STATIC) != 0) continue;
            List<AnnotationNode> visible = subscribedCopies(m.visibleAnnotations);
            List<AnnotationNode> invisible = subscribedCopies(m.invisibleAnnotations);
            if (visible.isEmpty() && invisible.isEmpty()) continue;
            if (scalaModule && !hasStaticCompanion && !m.name.startsWith("<")) {
                MethodNode bridge = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                        "forgestuck$event$" + m.name + "$" + bridges.size(), m.desc, m.signature,
                        m.exceptions == null ? null : m.exceptions.toArray(String[]::new));
                bridge.visibleAnnotations = visible.isEmpty() ? null : visible;
                bridge.invisibleAnnotations = invisible.isEmpty() ? null : invisible;
                bridge.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, node.name, "MODULE$", "L" + node.name + ";"));
                Type[] arguments = Type.getArgumentTypes(m.desc);
                int slot = 0;
                for (Type argument : arguments) {
                    bridge.instructions.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), slot));
                    slot += argument.getSize();
                }
                bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, node.name, m.name, m.desc, false));
                bridge.instructions.add(new InsnNode(Type.getReturnType(m.desc).getOpcode(Opcodes.IRETURN)));
                bridge.maxStack = Math.max(1 + slot, Type.getReturnType(m.desc).getSize());
                bridge.maxLocals = slot;
                bridges.add(bridge);
                remove(m.visibleAnnotations);
                remove(m.invisibleAnnotations);
            } else {
                remove(m.visibleAnnotations);
                remove(m.invisibleAnnotations);
                dropped.add(m.name + m.desc);
            }
        }
        node.methods.addAll(bridges);
        boolean anyStatic = node.methods.stream().anyMatch(m -> (m.access & Opcodes.ACC_STATIC) != 0
                && (hasSubscribe(m.visibleAnnotations) || hasSubscribe(m.invisibleAnnotations)));
        boolean classStripped = false;
        if (!anyStatic) {
            // NeoForge throws when register(Class) finds no @SubscribeEvent methods; Forge registered nothing.
            classStripped = node.visibleAnnotations != null && node.visibleAnnotations.removeIf(a -> SUBSCRIBER.contains(a.desc));
            classStripped |= node.invisibleAnnotations != null && node.invisibleAnnotations.removeIf(a -> SUBSCRIBER.contains(a.desc));
        }
        if (dropped.isEmpty() && bridges.isEmpty() && !classStripped) return bytes;
        if (!dropped.isEmpty()) {
            LOGGER.warn("[Forge Stuck!!] {} is an @EventBusSubscriber class with instance @SubscribeEvent method(s) {}; "
                    + "Forge ignored these during automatic registration (static methods only), so the annotation was "
                    + "removed to match instead of failing in NeoForge", className, dropped);
        }
        if (!bridges.isEmpty()) {
            LOGGER.info("[Forge Stuck!!] {} exposed {} Scala singleton event handler(s) through static bridges",
                    className, bridges.size());
        }
        if (classStripped) {
            LOGGER.warn("[Forge Stuck!!] {} has no static @SubscribeEvent methods left; its @EventBusSubscriber "
                    + "annotation was removed because NeoForge rejects registering a class without handlers (Forge "
                    + "registered nothing for it)", className);
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean hasSubscribe(List<AnnotationNode> list) {
        return list != null && list.stream().anyMatch(a -> SUBSCRIBE.contains(a.desc));
    }

    private static boolean has(List<AnnotationNode> list) {
        return list != null && list.stream().anyMatch(a -> SUBSCRIBER.contains(a.desc));
    }

    private static List<AnnotationNode> subscribedCopies(List<AnnotationNode> annotations) {
        List<AnnotationNode> copies = new ArrayList<>();
        if (annotations == null) return copies;
        for (AnnotationNode annotation : annotations) {
            if (!SUBSCRIBE.contains(annotation.desc)) continue;
            AnnotationNode copy = new AnnotationNode(annotation.desc);
            annotation.accept(copy);
            copies.add(copy);
        }
        return copies;
    }

    private static void remove(List<AnnotationNode> list) {
        if (list != null) list.removeIf(a -> SUBSCRIBE.contains(a.desc));
    }
}