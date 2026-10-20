package net.neoforged.forgestuck.transformer.jar;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/** Supplies the codec methods required by 1.21 for legacy recipe serializer implementations. */
final class LegacyRecipeSerializerCodecStep implements ClassTransformStep {
    private static final String RECIPE = "net/minecraft/world/item/crafting/Recipe";
    private static final String SERIALIZER = "net/minecraft/world/item/crafting/RecipeSerializer";
    private static final String ADAPTER = "net/neoforged/forgestuck/compat/LegacyRecipeSerializerCodec";
    private static final String LEGACY_JSON_METHOD =
            "(Lnet/minecraft/resources/ResourceLocation;Lcom/google/gson/JsonObject;)L" + RECIPE + ";";
    private static final String CODEC_METHOD = "()Lcom/mojang/serialization/MapCodec;";
    private static final String STREAM_CODEC_METHOD =
            "()Lnet/minecraft/network/codec/StreamCodec;";

    @Override
    public byte[] apply(String className, byte[] bytes) {
        if (bytes == null) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean hasLegacyJsonMethod = node.methods.stream()
                .anyMatch(method -> method.desc.equals(LEGACY_JSON_METHOD));
        if (!hasLegacyJsonMethod) return bytes;

        boolean hasCodec = node.methods.stream()
                .anyMatch(method -> method.name.equals("codec") && method.desc.equals(CODEC_METHOD));
        boolean hasStreamCodec = node.methods.stream()
                .anyMatch(method -> method.name.equals("streamCodec") && method.desc.equals(STREAM_CODEC_METHOD));
        if (hasCodec && hasStreamCodec) return bytes;
        if (!hasCodec) node.methods.add(adapterMethod("codec", CODEC_METHOD, "codec"));
        if (!hasStreamCodec) node.methods.add(adapterMethod("streamCodec", STREAM_CODEC_METHOD, "streamCodec"));

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode adapterMethod(String name, String descriptor, String adapterMethod) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, ADAPTER, adapterMethod,
                "(L" + SERIALIZER + ";)" + (name.equals("codec")
                        ? "Lcom/mojang/serialization/MapCodec;"
                        : "Lnet/minecraft/network/codec/StreamCodec;"),
                false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        return method;
    }
}
