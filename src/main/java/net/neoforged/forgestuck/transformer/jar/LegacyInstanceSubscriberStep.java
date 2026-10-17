package net.neoforged.forgestuck.transformer.jar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge's automatic {@code @Mod.EventBusSubscriber} registration used {@code bus.register(Class)}, which only
 * registered <em>static</em> {@code @SubscribeEvent} methods and silently ignored instance ones. NeoForge 1.21.1
 * throws for an instance method. To keep the Forge behavior, the {@code @SubscribeEvent} marker is dropped from
 * instance methods of such classes (the methods themselves stay callable). Typical case: a Scala {@code object}'s
 * module class {@code Foo$}, whose companion {@code Foo} already carries annotated static forwarders; generating
 * static bridges instead would register every handler twice.
 */
public final class LegacyInstanceSubscriberStep implements ClassTransformStep {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgestuck");
    private static final Set<String> SUBSCRIBER = Set.of("Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;",
            "Lnet/neoforged/fml/common/EventBusSubscriber;");
    private static final Set<String> SUBSCRIBE = Set.of("Lnet/minecraftforge/eventbus/api/SubscribeEvent;",
            "Lnet/neoforged/bus/api/SubscribeEvent;");

    @Override
    public byte[] apply(String className, byte[] bytes) {
        if (bytes == null || className.startsWith("net.neoforged.forgestuck")) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        if (!has(node.visibleAnnotations) && !has(node.invisibleAnnotations)) return bytes;
        List<String> dropped = new ArrayList<>();
        for (MethodNode m : node.methods) {
            if ((m.access & Opcodes.ACC_STATIC) != 0) continue;
            boolean removed = remove(m.visibleAnnotations) | remove(m.invisibleAnnotations);
            if (removed) dropped.add(m.name + m.desc);
        }
        if (dropped.isEmpty()) return bytes;
        LOGGER.warn("[Forge Stuck!!] {} is an @EventBusSubscriber class with instance @SubscribeEvent method(s) {}; "
                + "Forge ignored these during automatic registration (static methods only), so the annotation was "
                + "removed to match instead of failing in NeoForge", className, dropped);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean has(List<AnnotationNode> list) {
        return list != null && list.stream().anyMatch(a -> SUBSCRIBER.contains(a.desc));
    }

    private static boolean remove(List<AnnotationNode> list) {
        return list != null && list.removeIf(a -> SUBSCRIBE.contains(a.desc));
    }
}