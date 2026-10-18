package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class ForgeOwnerRemapTest {
    static final String OLD = "net/minecraftforge/registries/RegistryObject";
    static final String NEW = "net/neoforged/forgestuck/compat/RegistryObject";
    static final String LIVING = "net/minecraft/world/entity/LivingEntity";

    /** Class (optionally a Mixin) with a method invoking {@code OLD.<method><desc>} and a field of type OLD. */
    static byte[] caller(String name, boolean mixin, String method, String desc) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        if (mixin) {
            AnnotationVisitor a = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
            AnnotationVisitor arr = a.visitArray("value");
            arr.visit(null, Type.getObjectType(LIVING));
            arr.visitEnd();
            a.visitEnd();
        }
        cw.visitField(Opcodes.ACC_PUBLIC, "holder", "L" + OLD + ";", "L" + OLD + "<Ljava/lang/Object;>;", null).visitEnd();
        var m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "go", "(L" + OLD + ";)V", null, null);
        var at = m.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/At;", true);
        at.visit("target", "L" + OLD + ";get()Ljava/lang/Object;");
        at.visitEnd();
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, OLD, method, desc, false);
        m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    static ClassNode parse(byte[] b) {
        ClassNode n = new ClassNode();
        new ClassReader(b).accept(n, 0);
        return n;
    }

    @Test
    void legacyMobEffectModifierCallUsesHolderAndResourceLocationAdapter() {
        String owner = "net/minecraft/world/effect/MobEffect";
        String desc = "(Lnet/minecraft/world/entity/ai/attributes/Attribute;Ljava/lang/String;D"
                + "Lnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;)L" + owner + ";";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/EffectCompat", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "add", "(L" + owner + ";"
                + desc.substring(1), null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.DLOAD, 3);
        method.visitVarInsn(Opcodes.ALOAD, 5);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "addAttributeModifier", desc, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = parse(new ForgeOwnerRemapStep().apply("com.ex.EffectCompat", writer.toByteArray()));
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyMobEffect", call.owner);
        assertEquals("(L" + owner + ";Lnet/minecraft/world/entity/ai/attributes/Attribute;Ljava/lang/String;D"
                + "Lnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;)L" + owner + ";",
                call.desc);
    }

    @Test
    void criticalHitHooksAndLegacyEventMembersUseCompatAdapter() {
        String hooks = "net/minecraftforge/common/ForgeHooks";
        String oldEvent = "net/minecraftforge/event/entity/player/CriticalHitEvent";
        String helper = "net/neoforged/forgestuck/compat/LegacyCriticalHitEvent";
        String eventDesc = "L" + oldEvent + ";";
        String oldResult = "Lnet/minecraftforge/eventbus/api/Event$Result;";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/CriticalHitCompat", null, "java/lang/Object", null);

        var create = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create",
                "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;ZF)" + eventDesc,
                null, null);
        create.visitCode();
        create.visitVarInsn(Opcodes.ALOAD, 0);
        create.visitVarInsn(Opcodes.ALOAD, 1);
        create.visitVarInsn(Opcodes.ILOAD, 2);
        create.visitVarInsn(Opcodes.FLOAD, 3);
        create.visitMethodInsn(Opcodes.INVOKESTATIC, hooks, "getCriticalHit",
                "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;ZF)" + eventDesc,
                false);
        create.visitInsn(Opcodes.ARETURN);
        create.visitMaxs(0, 0);
        create.visitEnd();

        var getDamage = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getDamage",
                "(" + eventDesc + ")F", null, null);
        getDamage.visitCode();
        getDamage.visitVarInsn(Opcodes.ALOAD, 0);
        getDamage.visitMethodInsn(Opcodes.INVOKEVIRTUAL, oldEvent, "getDamageModifier", "()F", false);
        getDamage.visitInsn(Opcodes.FRETURN);
        getDamage.visitMaxs(0, 0);
        getDamage.visitEnd();

        var setResult = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "setResult",
                "(" + eventDesc + oldResult + ")V", null, null);
        setResult.visitCode();
        setResult.visitVarInsn(Opcodes.ALOAD, 0);
        setResult.visitVarInsn(Opcodes.ALOAD, 1);
        setResult.visitMethodInsn(Opcodes.INVOKEVIRTUAL, oldEvent, "setResult", "(" + oldResult + ")V", false);
        setResult.visitInsn(Opcodes.RETURN);
        setResult.visitMaxs(0, 0);
        setResult.visitEnd();
        writer.visitEnd();

        ClassNode transformed = parse(new ForgeOwnerRemapStep().apply("com.ex.CriticalHitCompat", writer.toByteArray()));
        List<MethodInsnNode> calls = java.util.Arrays.stream(transformed.methods.toArray(new org.objectweb.asm.tree.MethodNode[0]))
                .flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals(3, calls.size());
        assertTrue(calls.stream().allMatch(call -> call.getOpcode() == Opcodes.INVOKESTATIC
                && helper.equals(call.owner)));
        assertEquals("(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;ZF)"
                + "Lnet/neoforged/neoforge/event/entity/player/CriticalHitEvent;", calls.get(0).desc);
        assertEquals("(Lnet/neoforged/neoforge/event/entity/player/CriticalHitEvent;)F", calls.get(1).desc);
        assertEquals("(Lnet/neoforged/neoforge/event/entity/player/CriticalHitEvent;"
                + "Lnet/neoforged/forgestuck/compat/LegacyEventResult;)V", calls.get(2).desc);
    }

    @Test
    void cropGrowthHooksAndMethodHandlesUseCompatAdapter() {
        String hooks = "net/minecraftforge/common/ForgeHooks";
        String helper = "net/neoforged/forgestuck/compat/LegacyCropGrowth";
        String preDesc = "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                + "Lnet/minecraft/world/level/block/state/BlockState;Z)Z";
        String postDesc = "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                + "Lnet/minecraft/world/level/block/state/BlockState;)V";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/CropGrowthCompat", null, "java/lang/Object", null);
        var pre = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "pre", preDesc, null, null);
        pre.visitCode();
        pre.visitInsn(Opcodes.ACONST_NULL);
        pre.visitInsn(Opcodes.ACONST_NULL);
        pre.visitInsn(Opcodes.ACONST_NULL);
        pre.visitInsn(Opcodes.ICONST_1);
        pre.visitMethodInsn(Opcodes.INVOKESTATIC, hooks, "onCropsGrowPre", preDesc, false);
        pre.visitInsn(Opcodes.IRETURN);
        pre.visitMaxs(0, 0);
        pre.visitEnd();

        var post = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "post", postDesc, null, null);
        post.visitCode();
        post.visitInsn(Opcodes.ACONST_NULL);
        post.visitInsn(Opcodes.ACONST_NULL);
        post.visitInsn(Opcodes.ACONST_NULL);
        post.visitMethodInsn(Opcodes.INVOKESTATIC, hooks, "onCropsGrowPost", postDesc, false);
        post.visitInsn(Opcodes.RETURN);
        post.visitMaxs(0, 0);
        post.visitEnd();

        var handle = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "preHandle", "()V", null, null);
        handle.visitCode();
        handle.visitLdcInsn(new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, hooks, "onCropsGrowPre",
                preDesc, false));
        handle.visitInsn(Opcodes.POP);
        handle.visitInsn(Opcodes.RETURN);
        handle.visitMaxs(0, 0);
        handle.visitEnd();
        writer.visitEnd();

        ClassNode transformed = parse(new ForgeOwnerRemapStep().apply("com.ex.CropGrowthCompat", writer.toByteArray()));
        List<MethodInsnNode> calls = java.util.Arrays.stream(transformed.methods.toArray(new org.objectweb.asm.tree.MethodNode[0]))
                .flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals(2, calls.size());
        assertTrue(calls.stream().allMatch(call -> call.getOpcode() == Opcodes.INVOKESTATIC
                && helper.equals(call.owner)));
        assertEquals("onCropsGrowPre", calls.get(0).name);
        assertEquals(preDesc, calls.get(0).desc);
        assertEquals("onCropsGrowPost", calls.get(1).name);
        assertEquals(postDesc, calls.get(1).desc);

        org.objectweb.asm.tree.LdcInsnNode handleConstant = java.util.Arrays.stream(
                        transformed.methods.get(2).instructions.toArray())
                .filter(org.objectweb.asm.tree.LdcInsnNode.class::isInstance)
                .map(org.objectweb.asm.tree.LdcInsnNode.class::cast)
                .findFirst().orElseThrow();
        org.objectweb.asm.Handle transformedHandle = (org.objectweb.asm.Handle) handleConstant.cst;
        assertEquals(Opcodes.H_INVOKESTATIC, transformedHandle.getTag());
        assertEquals(helper, transformedHandle.getOwner());
        assertEquals("onCropsGrowPre", transformedHandle.getName());
        assertEquals(preDesc, transformedHandle.getDesc());
    }

    @Test
    void hasNoElementsHookUsesIngredientCompatibilityAdapter() {
        String hook = "net/minecraftforge/common/ForgeHooks";
        String helper = "net/neoforged/forgestuck/compat/LegacyIngredientHooks";
        String desc = "(Lnet/minecraft/world/item/crafting/Ingredient;)Z";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/IngredientHookCompat", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "empty", desc, null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, hook, "hasNoElements", desc, false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = parse(new ForgeOwnerRemapStep().apply("com.ex.IngredientHookCompat",
                writer.toByteArray()));
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals(helper, call.owner);
        assertEquals("hasNoElements", call.name);
        assertEquals(desc, call.desc);
    }

    @Test
    void fmlLoaderProductionQueryUsesCompatibilityAdapter() {
        String oldOwner = "net/minecraftforge/fml/loading/FMLLoader";
        String helper = "net/neoforged/forgestuck/compat/LegacyFMLLoader";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/FMLLoaderCompat", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "production", "()Z", null, null);
        method.visitCode();
        method.visitMethodInsn(Opcodes.INVOKESTATIC, oldOwner, "isProduction", "()Z", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = parse(new ForgeOwnerRemapStep().apply("com.ex.FMLLoaderCompat",
                writer.toByteArray()));
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals(helper, call.owner);
        assertEquals("isProduction", call.name);
        assertEquals("()Z", call.desc);
    }

    @Test
    void legacySrgMobEffectModifierCallUsesHolderAndResourceLocationAdapter() {
        String owner = "net/minecraft/world/effect/MobEffect";
        String callOwner = "cofh/core/common/effect/ChilledMobEffect";
        String desc = "(Lnet/minecraft/world/entity/ai/attributes/Attribute;Ljava/lang/String;D"
                + "Lnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;)L" + owner + ";";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/EffectCompat", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "add", "(L" + callOwner + ";"
                + desc.substring(1), null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.DLOAD, 3);
        method.visitVarInsn(Opcodes.ALOAD, 5);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, callOwner, "m_19472_", desc, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = parse(new ForgeOwnerRemapStep().apply("com.ex.EffectCompat", writer.toByteArray()));
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyMobEffect", call.owner);
        assertEquals("(L" + owner + ";Lnet/minecraft/world/entity/ai/attributes/Attribute;Ljava/lang/String;D"
                + "Lnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;)L" + owner + ";",
                call.desc);
    }

    @Test
    void legacyCustomClientFactoryUsesEntityBuilderAdapter() {
        String owner = "net/minecraft/world/entity/EntityType$Builder";
        String desc = "(Ljava/util/function/BiFunction;)L" + owner + ";";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/EntityCompat", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "factory",
                "(L" + owner + ";Ljava/util/function/BiFunction;)L" + owner + ";", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "setCustomClientFactory", desc, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = parse(new ForgeOwnerRemapStep().apply("com.ex.EntityCompat", writer.toByteArray()));
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyEntityTypeBuilder", call.owner);
        assertEquals("(L" + owner + ";Ljava/util/function/BiFunction;)L" + owner + ";", call.desc);
    }

    @Test
    void legacyForgeBurnTimeHookUsesNeoForgeItemStackExtension() {
        String stack = "net/minecraft/world/item/ItemStack";
        String recipeType = "net/minecraft/world/item/crafting/RecipeType";
        String oldOwner = "net/minecraftforge/common/ForgeHooks";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/BurnTimeCompat", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "burnTime",
                "(L" + stack + ";L" + recipeType + ";)I", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, oldOwner, "getBurnTime",
                "(L" + stack + ";L" + recipeType + ";)I", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = parse(new ForgeOwnerRemapStep().apply("com.ex.BurnTimeCompat", writer.toByteArray()));
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKEINTERFACE, call.getOpcode());
        assertEquals("net/neoforged/neoforge/common/extensions/IItemStackExtension", call.owner);
        assertEquals("getBurnTime", call.name);
        assertEquals("(L" + recipeType + ";)I", call.desc);
        assertTrue(call.itf);
    }

    @Test
    void legacyForgeBurnTimeMethodHandleUsesNeoForgeItemStackExtension() {
        String stack = "net/minecraft/world/item/ItemStack";
        String recipeType = "net/minecraft/world/item/crafting/RecipeType";
        String oldOwner = "net/minecraftforge/common/ForgeHooks";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/BurnTimeHandle", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "handle", "()Ljava/lang/Object;",
                null, null);
        method.visitCode();
        method.visitLdcInsn(new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, oldOwner, "getBurnTime",
                "(L" + stack + ";L" + recipeType + ";)I", false));
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = parse(new ForgeOwnerRemapStep().apply("com.ex.BurnTimeHandle", writer.toByteArray()));
        Object value = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(org.objectweb.asm.tree.LdcInsnNode.class::isInstance)
                .map(org.objectweb.asm.tree.LdcInsnNode.class::cast)
                .map(ldc -> ldc.cst).findFirst().orElseThrow();
        assertEquals(new org.objectweb.asm.Handle(Opcodes.H_INVOKEINTERFACE,
                "net/neoforged/neoforge/common/extensions/IItemStackExtension", "getBurnTime",
                "(L" + recipeType + ";)I", true), value);
    }

    @Test
    void legacyNetworkHooksOpenScreenOverloadsMapToCompatibilityAdapter() {
        String oldOwner = "net/minecraftforge/network/NetworkHooks";
        String newOwner = "net/neoforged/forgestuck/compat/LegacyNetworkHooks";
        List<String> descriptors = List.of(
                "(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/MenuProvider;)V",
                "(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/MenuProvider;"
                        + "Ljava/util/function/Consumer;)V",
                "(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/MenuProvider;"
                        + "Lnet/minecraft/core/BlockPos;)V");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/NetworkHooksCompat", null, "java/lang/Object", null);
        for (int i = 0; i < descriptors.size(); i++) {
            String descriptor = descriptors.get(i);
            var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "open" + i,
                    descriptor, null, null);
            method.visitCode();
            int local = 0;
            for (Type argument : Type.getArgumentTypes(descriptor)) {
                method.visitVarInsn(Opcodes.ALOAD, local);
                local += argument.getSize();
            }
            method.visitMethodInsn(Opcodes.INVOKESTATIC, oldOwner, "openScreen", descriptor, false);
            method.visitInsn(Opcodes.RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
        }
        writer.visitEnd();

        ClassNode transformed = parse(new ForgeOwnerRemapStep().apply("com.ex.NetworkHooksCompat",
                writer.toByteArray()));
        List<MethodInsnNode> calls = java.util.Arrays.stream(transformed.methods.toArray(new org.objectweb.asm.tree.MethodNode[0]))
                .flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals(3, calls.size());
        for (int i = 0; i < calls.size(); i++) {
            assertEquals(Opcodes.INVOKESTATIC, calls.get(i).getOpcode());
            assertEquals(newOwner, calls.get(i).owner);
            assertEquals(descriptors.get(i), calls.get(i).desc);
        }
    }

    @Test
    void registryObjectOwnerIsRemapped() {
        byte[] in = caller("com/ex/M", true, "get", "()Ljava/lang/Object;");
        OwnerRemapReport report = new OwnerRemapReport();
        byte[] out = new ForgeOwnerRemapStep().apply("com.ex.M", in, report);
        ClassNode n = parse(out);
        MethodInsnNode call = (MethodInsnNode) n.methods.get(0).instructions.toArray()[1];
        assertEquals(NEW, call.owner);
        assertEquals("(L" + NEW + ";)V", n.methods.get(0).desc);
        assertEquals("L" + NEW + ";", n.fields.get(0).desc);
        assertEquals("L" + NEW + "<Ljava/lang/Object;>;", n.fields.get(0).signature);
        assertEquals("L" + NEW + ";get()Ljava/lang/Object;",
                n.methods.get(0).visibleAnnotations.get(0).values.get(1));
        assertFalse(new String(out, StandardCharsets.ISO_8859_1).contains(OLD));
        assertTrue(report.total() >= 4);
        assertEquals(Set.of(), ForgeOwnerMappings.defaults().remainingOwners(out));
    }

    @Test
    void objectMethodsOnMappedOwnerDoNotBlockRemap() {
        byte[] in = caller("com/ex/H", false, "hashCode", "()I");
        byte[] out = new ForgeOwnerRemapStep().apply("com.ex.H", in);
        assertNotSame(in, out);
        MethodInsnNode call = (MethodInsnNode) parse(out).methods.get(0).instructions.toArray()[1];
        assertEquals(NEW, call.owner);
        assertEquals("hashCode", call.name);
    }

    @Test
    void forgeItemTagConstantRemapsToVerifiedNeoForgeField() {
        String oldOwner = "net/minecraftforge/common/Tags$Items";
        String newOwner = "net/neoforged/neoforge/common/Tags$Items";
        String descriptor = "Lnet/minecraft/tags/TagKey;";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/TagAccess", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "get", "()" + descriptor, null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, oldOwner, "ORES", descriptor);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = parse(new ForgeOwnerRemapStep().apply("com.ex.TagAccess", writer.toByteArray()));
        var field = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(org.objectweb.asm.tree.FieldInsnNode.class::isInstance)
                .map(org.objectweb.asm.tree.FieldInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals(newOwner, field.owner);
        assertEquals("ORES", field.name);
        assertEquals(descriptor, field.desc);
    }

    @Test
    void removedItemOwnersAreMappedToNeoForgeAndComponentShim() {
        String forgeItem = "net/minecraftforge/common/extensions/IForgeItem";
        String dyeable = "net/minecraft/world/item/DyeableLeatherItem";
        String compatDyeable = "net/neoforged/forgestuck/compat/LegacyDyeableLeatherItem";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/LegacyItem", null, "java/lang/Object",
                new String[] {forgeItem, dyeable});
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "color",
                "(L" + dyeable + ";Lnet/minecraft/world/item/ItemStack;)I", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, dyeable, "getColor",
                "(Lnet/minecraft/world/item/ItemStack;)I", true);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        byte[] out = new ForgeOwnerRemapStep().apply("com.ex.LegacyItem", writer.toByteArray());
        ClassNode node = parse(out);
        assertEquals("net/neoforged/neoforge/common/extensions/IItemExtension", node.interfaces.get(0));
        assertEquals(compatDyeable, node.interfaces.get(1));
        MethodInsnNode call = (MethodInsnNode) node.methods.get(0).instructions.toArray()[2];
        assertEquals(compatDyeable, call.owner);
        assertFalse(new String(out, StandardCharsets.ISO_8859_1).contains(forgeItem));
        assertFalse(new String(out, StandardCharsets.ISO_8859_1).contains(dyeable));
    }

    @Test
    void removedBlockAndItemHandlerOwnersAreMapped() {
        String oldBlock = "net/minecraftforge/common/extensions/IForgeBlock";
        String oldHandler = "net/minecraftforge/items/IItemHandlerModifiable";
        String newBlock = "net/neoforged/neoforge/common/extensions/IBlockExtension";
        String newHandler = "net/neoforged/neoforge/items/IItemHandlerModifiable";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/CompatInterfaces", null, "java/lang/Object",
                new String[] {oldBlock, oldHandler});
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "set",
                "(L" + oldHandler + ";Lnet/minecraft/world/item/ItemStack;)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, oldHandler, "setStackInSlot",
                "(ILnet/minecraft/world/item/ItemStack;)V", true);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = parse(new ForgeOwnerRemapStep().apply("com.ex.CompatInterfaces", writer.toByteArray()));
        assertEquals(newBlock, node.interfaces.get(0));
        assertEquals(newHandler, node.interfaces.get(1));
        MethodInsnNode call = (MethodInsnNode) node.methods.get(0).instructions.toArray()[3];
        assertEquals(newHandler, call.owner);
        assertEquals("(L" + newHandler + ";Lnet/minecraft/world/item/ItemStack;)V", node.methods.get(0).desc);
    }

    @Test
    void legacyNbtSerializableOwnerMapsToContextBridge() {
        String oldOwner = "net/minecraftforge/common/util/INBTSerializable";
        String newOwner = "net/neoforged/forgestuck/compat/LegacyINBTSerializable";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/Serializable", null, "java/lang/Object",
                new String[] {oldOwner});
        writer.visitEnd();

        ClassNode node = parse(new ForgeOwnerRemapStep().apply("com.ex.Serializable", writer.toByteArray()));
        assertEquals(newOwner, node.interfaces.get(0));
    }

    @Test
    void energyAndMenuFactoryOwnersAreMappedWithLegacyBufferAdapter() {
        String oldEnergy = "net/minecraftforge/energy/IEnergyStorage";
        String newEnergy = "net/neoforged/neoforge/energy/IEnergyStorage";
        String oldFactory = "net/minecraftforge/network/IContainerFactory";
        String oldMenuType = "net/minecraftforge/common/extensions/IForgeMenuType";
        String compatFactory = "net/neoforged/forgestuck/compat/LegacyContainerFactory";
        String compatMenuType = "net/neoforged/forgestuck/compat/LegacyForgeMenuType";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/LegacyMenuAndEnergy", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create",
                "(L" + oldFactory + ";)Lnet/minecraft/world/inventory/MenuType;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, oldMenuType, "create",
                "(L" + oldFactory + ";)Lnet/minecraft/world/inventory/MenuType;", true);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC, "energy", "L" + oldEnergy + ";", null, null).visitEnd();
        writer.visitEnd();

        ClassNode node = parse(new ForgeOwnerRemapStep().apply("com.ex.LegacyMenuAndEnergy", writer.toByteArray()));
        assertEquals(newEnergy, node.fields.get(0).desc.substring(1, node.fields.get(0).desc.length() - 1));
        assertEquals("(L" + compatFactory + ";)Lnet/minecraft/world/inventory/MenuType;", node.methods.get(0).desc);
        MethodInsnNode call = (MethodInsnNode) node.methods.get(0).instructions.toArray()[1];
        assertEquals(compatMenuType, call.owner);
        assertTrue(call.itf);
        assertEquals("(L" + compatFactory + ";)Lnet/minecraft/world/inventory/MenuType;", call.desc);
        assertEquals(Set.of(), ForgeOwnerMappings.defaults().remainingOwners(nodeBytes(node)));
    }

    @Test
    void inferredGenericListenerUsesWellFormedHelperDescriptor() {
        String bus = "net/minecraftforge/eventbus/api/IEventBus";
        String event = "net/minecraftforge/event/AttachCapabilitiesEvent";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/GenericListener", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "register", "(L" + bus + ";)V",
                null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitLdcInsn(Type.getType("Lnet/minecraft/world/level/Level;"));
        method.visitInvokeDynamicInsn("accept", "()Ljava/util/function/Consumer;",
                new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC,
                        "java/lang/invoke/LambdaMetafactory", "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                                + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
                                + "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
                                + "Ljava/lang/invoke/CallSite;", false),
                Type.getMethodType("(Ljava/lang/Object;)V"),
                new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, "com/ex/GenericListener", "onEvent",
                        "(L" + event + ";)V", false),
                Type.getMethodType("(L" + event + ";)V"));
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, bus, "addGenericListener",
                "(Ljava/lang/Class;Ljava/util/function/Consumer;)V", true);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        var handler = writer.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "onEvent",
                "(L" + event + ";)V", null, null);
        handler.visitCode();
        handler.visitInsn(Opcodes.RETURN);
        handler.visitMaxs(0, 0);
        handler.visitEnd();
        writer.visitEnd();

        ClassNode node = parse(new ForgeOwnerRemapStep().apply("com.ex.GenericListener", writer.toByteArray()));
        MethodInsnNode call = null;
        for (var insn : node.methods.get(0).instructions) {
            if (insn instanceof MethodInsnNode methodCall
                    && methodCall.owner.equals("net/neoforged/forgestuck/compat/LegacyGenericListenerRegistration")) {
                call = methodCall;
            }
        }
        assertNotNull(call);
        assertEquals("(Lnet/neoforged/bus/api/IEventBus;Ljava/lang/Class;Ljava/util/function/Consumer;"
                + "Ljava/lang/Class;)V", call.desc);
    }

    @Test
    void inferredPriorityGenericListenerUsesWellFormedHelperDescriptor() {
        String bus = "net/minecraftforge/eventbus/api/IEventBus";
        String event = "net/minecraftforge/event/AttachCapabilitiesEvent";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/PriorityGenericListener", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "register", "(L" + bus + ";)V",
                null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitLdcInsn(Type.getType("Lnet/minecraft/world/level/Level;"));
        String priority = "net/minecraftforge/eventbus/api/EventPriority";
        method.visitFieldInsn(Opcodes.GETSTATIC, priority, "LOW", "L" + priority + ";");
        method.visitInvokeDynamicInsn("accept", "()Ljava/util/function/Consumer;",
                new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC,
                        "java/lang/invoke/LambdaMetafactory", "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                                + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
                                + "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
                                + "Ljava/lang/invoke/CallSite;", false),
                Type.getMethodType("(Ljava/lang/Object;)V"),
                new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, "com/ex/PriorityGenericListener", "onEvent",
                        "(L" + event + ";)V", false),
                Type.getMethodType("(L" + event + ";)V"));
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, bus, "addGenericListener",
                "(Ljava/lang/Class;L" + priority + ";Ljava/util/function/Consumer;)V", true);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        var handler = writer.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "onEvent",
                "(L" + event + ";)V", null, null);
        handler.visitCode();
        handler.visitInsn(Opcodes.RETURN);
        handler.visitMaxs(0, 0);
        handler.visitEnd();
        writer.visitEnd();

        ClassNode node = parse(new ForgeOwnerRemapStep().apply("com.ex.PriorityGenericListener", writer.toByteArray()));
        MethodInsnNode call = java.util.Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(methodCall -> methodCall.owner.equals(
                        "net/neoforged/forgestuck/compat/LegacyGenericListenerRegistration"))
                .findFirst().orElseThrow();
        assertEquals("(Lnet/neoforged/bus/api/IEventBus;Ljava/lang/Class;Lnet/neoforged/bus/api/EventPriority;"
                + "Ljava/util/function/Consumer;Ljava/lang/Class;)V", call.desc);
        assertFalse(call.desc.contains(";;"));
    }

    @Test
    void legacyEventBusPostReturnsCancellationState() {
        String bus = "net/minecraftforge/eventbus/api/IEventBus";
        String event = "net/minecraftforge/eventbus/api/Event";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/EventBusPost", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "post",
                "(L" + bus + ";L" + event + ";)Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, bus, "post", "(L" + event + ";)Z", true);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = parse(new ForgeOwnerRemapStep().apply("com.ex.EventBusPost", writer.toByteArray()));
        MethodInsnNode call = java.util.Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyEventBusPost", call.owner);
        assertEquals("(Lnet/neoforged/bus/api/IEventBus;Lnet/neoforged/bus/api/Event;)Z", call.desc);
    }

    @Test
    void legacyCompositeConditionArrayConstructorsUseNeoForgeLists() throws Exception {
        String condition = "net/minecraftforge/common/crafting/conditions/ICondition";
        for (String target : List.of("AndCondition", "OrCondition")) {
            assertNotNull(Class.forName("net.neoforged.neoforge.common.conditions." + target)
                    .getDeclaredConstructor(List.class));
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/ConditionConstructors", null, "java/lang/Object", null);
        for (String type : List.of("AndCondition", "OrCondition")) {
            var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "new" + type,
                    "([L" + condition + ";)V", null, null);
            method.visitCode();
            method.visitTypeInsn(Opcodes.NEW, "net/minecraftforge/common/crafting/conditions/" + type);
            method.visitInsn(Opcodes.DUP);
            method.visitVarInsn(Opcodes.ALOAD, 0);
            method.visitMethodInsn(Opcodes.INVOKESPECIAL,
                    "net/minecraftforge/common/crafting/conditions/" + type, "<init>",
                    "([L" + condition + ";)V", false);
            method.visitInsn(Opcodes.POP);
            method.visitInsn(Opcodes.RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
        }
        writer.visitEnd();

        ClassNode node = parse(new ForgeOwnerRemapStep().apply("com.ex.ConditionConstructors", writer.toByteArray()));
        for (var method : node.methods) {
            if ("<init>".equals(method.name)) continue;
            List<MethodInsnNode> calls = java.util.Arrays.stream(method.instructions.toArray())
                    .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
            assertEquals(2, calls.size());
            assertEquals("net/neoforged/forgestuck/compat/LegacyConditionArrays", calls.get(0).owner);
            assertEquals("([Lnet/neoforged/neoforge/common/conditions/ICondition;)Ljava/util/List;",
                    calls.get(0).desc);
            assertEquals("net/neoforged/neoforge/common/conditions/"
                    + method.name.substring("new".length()), calls.get(1).owner);
            assertEquals("(Ljava/util/List;)V", calls.get(1).desc);
        }
    }

    @Test
    void fluidHandlerAndActionOwnersMapToNeoForge() {
        String oldHandler = "net/minecraftforge/fluids/capability/IFluidHandler";
        String newHandler = "net/neoforged/neoforge/fluids/capability/IFluidHandler";
        String oldItemHandler = oldHandler + "Item";
        String newItemHandler = "net/neoforged/neoforge/fluids/capability/IFluidHandlerItem";
        String oldAction = oldHandler + "$FluidAction";
        String newAction = newHandler + "$FluidAction";
        String oldStack = "net/minecraftforge/fluids/FluidStack";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/FluidCompat", null, "java/lang/Object",
                new String[] {oldHandler, oldItemHandler});
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "fill",
                "(L" + oldHandler + ";" + "L" + oldStack + ";L" + oldAction + ";)I", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, oldHandler, "fill",
                "(L" + oldStack + ";L" + oldAction + ";)I", true);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = parse(new ForgeOwnerRemapStep().apply("com.ex.FluidCompat", writer.toByteArray()));
        assertEquals(List.of(newHandler, newItemHandler), node.interfaces);
        assertEquals("(L" + newHandler + ";Lnet/neoforged/neoforge/fluids/FluidStack;"
                + "L" + newAction + ";)I", node.methods.get(0).desc);
        MethodInsnNode call = (MethodInsnNode) node.methods.get(0).instructions.toArray()[3];
        assertEquals(newHandler, call.owner);
        assertEquals("(Lnet/neoforged/neoforge/fluids/FluidStack;L" + newAction + ";)I", call.desc);
        assertEquals(Set.of(), ForgeOwnerMappings.defaults().remainingOwners(nodeBytes(node)));
    }

    @Test
    void emptyFluidHandlerConstructorUsesNeoForgeSingleton() {
        String owner = "net/minecraftforge/fluids/capability/templates/EmptyFluidHandler";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/EmptyFluidCompat", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create",
                "()L" + owner + ";", null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, owner);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", "()V", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = parse(new ForgeOwnerRemapStep().apply("com.ex.EmptyFluidCompat", writer.toByteArray()));
        var instructions = java.util.Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(instruction -> instruction.getOpcode() >= 0)
                .toArray(org.objectweb.asm.tree.AbstractInsnNode[]::new);
        assertEquals(2, instructions.length);
        assertInstanceOf(org.objectweb.asm.tree.FieldInsnNode.class, instructions[0]);
        var singleton = (org.objectweb.asm.tree.FieldInsnNode) instructions[0];
        assertEquals(Opcodes.GETSTATIC, singleton.getOpcode());
        assertEquals("net/neoforged/neoforge/fluids/capability/templates/EmptyFluidHandler", singleton.owner);
        assertEquals("INSTANCE", singleton.name);
        assertEquals("L" + singleton.owner + ";", singleton.desc);
        assertEquals(Opcodes.ARETURN, instructions[1].getOpcode());
    }

    @Test
    void legacyItemHandlerHelperCallsUseCompatibilityMethods() {
        String owner = "net/minecraftforge/items/ItemHandlerHelper";
        String itemStack = "Lnet/minecraft/world/item/ItemStack;";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/ItemHandlerHelperCompat", null, "java/lang/Object",
                null);
        var canStack = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "canStack",
                "(" + itemStack + itemStack + ")Z", null, null);
        canStack.visitCode();
        canStack.visitVarInsn(Opcodes.ALOAD, 0);
        canStack.visitVarInsn(Opcodes.ALOAD, 1);
        canStack.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "canItemStacksStack",
                "(" + itemStack + itemStack + ")Z", false);
        canStack.visitInsn(Opcodes.IRETURN);
        canStack.visitMaxs(0, 0);
        canStack.visitEnd();
        var copy = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "copy",
                "(" + itemStack + "I)" + itemStack, null, null);
        copy.visitCode();
        copy.visitVarInsn(Opcodes.ALOAD, 0);
        copy.visitVarInsn(Opcodes.ILOAD, 1);
        copy.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "copyStackWithSize",
                "(" + itemStack + "I)" + itemStack, false);
        copy.visitInsn(Opcodes.ARETURN);
        copy.visitMaxs(0, 0);
        copy.visitEnd();
        writer.visitEnd();

        ClassNode node = parse(new ForgeOwnerRemapStep().apply("com.ex.ItemHandlerHelperCompat", writer.toByteArray()));
        List<MethodInsnNode> calls = java.util.Arrays.stream(node.methods.toArray(
                        org.objectweb.asm.tree.MethodNode[]::new))
                .flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals(2, calls.size());
        assertTrue(calls.stream().allMatch(call -> call.getOpcode() == Opcodes.INVOKESTATIC
                && call.owner.equals("net/neoforged/forgestuck/compat/LegacyItemHandlerHelper")));
        assertEquals(List.of("(" + itemStack + itemStack + ")Z", "(" + itemStack + "I)" + itemStack),
                calls.stream().map(call -> call.desc).toList());
    }

    @Test
    void legacyLivingDamageEventMapsToMutableCompatEvent() {
        String oldEvent = "net/minecraftforge/event/entity/living/LivingDamageEvent";
        String compatEvent = "net/neoforged/forgestuck/compat/LegacyLivingDamageEvent";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/DamageListener", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PRIVATE, "event", "L" + oldEvent + ";", null, null).visitEnd();
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "setDamage",
                "(L" + oldEvent + ";F)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.FLOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, oldEvent, "setAmount", "(F)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = parse(new ForgeOwnerRemapStep().apply("com.ex.DamageListener", writer.toByteArray()));
        assertEquals("L" + compatEvent + ";", node.fields.get(0).desc);
        assertEquals("(L" + compatEvent + ";F)V", node.methods.get(0).desc);
        MethodInsnNode call = (MethodInsnNode) node.methods.get(0).instructions.toArray()[2];
        assertEquals(compatEvent, call.owner);
        assertEquals("setAmount", call.name);
    }

    @Test
    void legacyLivingTickEventMapsToFunctionalCompatEvent() {
        String oldEvent = "net/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent";
        String compatEvent = "net/neoforged/forgestuck/compat/LegacyLivingTickEvent";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/LivingTickListener", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PRIVATE, "event", "L" + oldEvent + ";", null, null).visitEnd();
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "handle",
                "(L" + oldEvent + ";)Lnet/minecraft/world/entity/LivingEntity;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, oldEvent, "getEntity",
                "()Lnet/minecraft/world/entity/LivingEntity;", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = parse(new ForgeOwnerRemapStep().apply("com.ex.LivingTickListener", writer.toByteArray()));
        assertEquals("L" + compatEvent + ";", node.fields.get(0).desc);
        assertEquals("(L" + compatEvent + ";)Lnet/minecraft/world/entity/LivingEntity;", node.methods.get(0).desc);
        MethodInsnNode call = (MethodInsnNode) node.methods.get(0).instructions.toArray()[1];
        assertEquals(compatEvent, call.owner);
        assertEquals("getEntity", call.name);
    }

    @Test
    void legacyFluidStackTagCallsRedirectToCompatShim() {
        String oldStack = "net/minecraftforge/fluids/FluidStack";
        String newStack = "net/neoforged/neoforge/fluids/FluidStack";
        String compat = "net/neoforged/forgestuck/compat/LegacyFluidStack";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/FluidTagAccess", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "tag",
                "(L" + oldStack + ";)Lnet/minecraft/nbt/CompoundTag;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, oldStack, "getTag", "()Lnet/minecraft/nbt/CompoundTag;", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = parse(new ForgeOwnerRemapStep().apply("com.ex.FluidTagAccess", writer.toByteArray()));
        assertEquals("(L" + newStack + ";)Lnet/minecraft/nbt/CompoundTag;", node.methods.get(0).desc);
        assertEquals(Set.of(), ForgeOwnerMappings.defaults().remainingOwners(nodeBytes(node)));
        MethodInsnNode call = (MethodInsnNode) node.methods.get(0).instructions.toArray()[1];
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals(compat, call.owner);
        assertEquals("getTag", call.name);
        assertEquals("(L" + newStack + ";)Lnet/minecraft/nbt/CompoundTag;", call.desc);
    }

    @Test
    void legacyFluidStackTagMethodHandlesRedirectToCompatShim() {
        String oldStack = "net/minecraftforge/fluids/FluidStack";
        String newStack = "net/neoforged/neoforge/fluids/FluidStack";
        String compat = "net/neoforged/forgestuck/compat/LegacyFluidStack";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/FluidTagHandle", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "tagHandle",
                "()Ljava/lang/invoke/MethodHandle;", null, null);
        method.visitCode();
        method.visitLdcInsn(new org.objectweb.asm.Handle(Opcodes.H_INVOKEVIRTUAL, oldStack, "getTag",
                "()Lnet/minecraft/nbt/CompoundTag;", false));
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = parse(new ForgeOwnerRemapStep().apply("com.ex.FluidTagHandle", writer.toByteArray()));
        assertEquals(Set.of(), ForgeOwnerMappings.defaults().remainingOwners(nodeBytes(node)));
        var handle = (org.objectweb.asm.Handle) ((org.objectweb.asm.tree.LdcInsnNode)
                node.methods.get(0).instructions.getFirst()).cst;
        assertEquals(Opcodes.H_INVOKESTATIC, handle.getTag());
        assertEquals(compat, handle.getOwner());
        assertEquals("getTag", handle.getName());
        assertEquals("(L" + newStack + ";)Lnet/minecraft/nbt/CompoundTag;", handle.getDesc());
    }

    @Test
    void customLoaderBuilderConstructorEnablesInlineElementsOnNeoForge() {
        String oldOwner = "net/minecraftforge/client/model/generators/CustomLoaderBuilder";
        String oldDescriptor = "(Lnet/minecraft/resources/ResourceLocation;"
                + "Lnet/minecraftforge/client/model/generators/ModelBuilder;"
                + "Lnet/minecraftforge/common/data/ExistingFileHelper;)V";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/LoaderBuilder", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create",
                "(L" + oldOwner + ";Lnet/minecraft/resources/ResourceLocation;"
                        + "Lnet/minecraftforge/client/model/generators/ModelBuilder;"
                        + "Lnet/minecraftforge/common/data/ExistingFileHelper;)V",
                null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ALOAD, 3);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, oldOwner, "<init>", oldDescriptor, false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = parse(new ForgeOwnerRemapStep().apply("com.ex.LoaderBuilder", writer.toByteArray()));
        MethodInsnNode call = (MethodInsnNode) node.methods.get(0).instructions.toArray()[5];
        assertEquals(Opcodes.ICONST_1, call.getPrevious().getOpcode());
        assertEquals("net/neoforged/neoforge/client/model/generators/CustomLoaderBuilder", call.owner);
        assertEquals("<init>", call.name);
        assertEquals("(Lnet/minecraft/resources/ResourceLocation;"
                + "Lnet/neoforged/neoforge/client/model/generators/ModelBuilder;"
                + "Lnet/neoforged/neoforge/common/data/ExistingFileHelper;Z)V", call.desc);
    }

    @Test
    void nbtFluidStackConstructorUsesNeoForgeComponentAdapter() {
        String oldStack = "net/minecraftforge/fluids/FluidStack";
        String compatFactory = "net/neoforged/forgestuck/compat/LegacyFluidStack";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/FluidStackFactory", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create",
                "(Lnet/minecraft/world/level/material/Fluid;ILnet/minecraft/nbt/CompoundTag;)L" + oldStack + ";",
                null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, oldStack);
        method.visitInsn(Opcodes.DUP);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, oldStack, "<init>",
                "(Lnet/minecraft/world/level/material/Fluid;ILnet/minecraft/nbt/CompoundTag;)V", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = parse(new ForgeOwnerRemapStep().apply("com.ex.FluidStackFactory", writer.toByteArray()));
        MethodInsnNode call = (MethodInsnNode) node.methods.get(0).instructions.toArray()[3];
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals(compatFactory, call.owner);
        assertEquals("create", call.name);
        assertEquals("(Lnet/minecraft/world/level/material/Fluid;ILnet/minecraft/nbt/CompoundTag;)"
                + "Lnet/neoforged/neoforge/fluids/FluidStack;", call.desc);
        assertEquals(Set.of(), ForgeOwnerMappings.defaults().remainingOwners(nodeBytes(node)));
    }

    private static byte[] nodeBytes(ClassNode node) {
        org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    @Test
    void classWithoutForgeOwnersIsUnchanged() {
        byte[] in = SrgCompatTest.callerClass("com/ex/Plain", LIVING, "m_1_");
        OwnerRemapReport report = new OwnerRemapReport();
        assertSame(in, new ForgeOwnerRemapStep().apply("com.ex.Plain", in, report));
        assertEquals(0, report.total());
    }

    @Test
    void incompatibleMemberLeavesOwnerUnmappedAndMixinIsDropped() {
        byte[] in = caller("com/ex/Bad", true, "noSuchMember", "()Z");
        byte[] out = new ForgeOwnerRemapStep().apply("com.ex.Bad", in);
        assertSame(in, out);
        MixinResourceRewriter rewriter = new MixinResourceRewriter(LegacyNameMappings.defaults(), "x.jar");
        rewriter.inspectClass("com.ex.Bad", out);
        String reason = rewriter.droppedMixins().get("com.ex.Bad");
        assertNotNull(reason);
        assertTrue(reason.contains("unresolved owner") && reason.contains(OLD), reason);
        String cfg = "{\"package\":\"com.ex\",\"mixins\":[\"Bad\",\"Good\"]}";
        String rewritten = new String(rewriter.rewrite("x.mixins.json", cfg.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
        assertFalse(rewritten.contains("Bad"));
        assertTrue(rewritten.contains("Good"));
    }

    @Test
    void remappedMixinIsNotDropped() {
        byte[] out = new ForgeOwnerRemapStep().apply("com.ex.Ok", caller("com/ex/Ok", true, "get", "()Ljava/lang/Object;"));
        MixinResourceRewriter rewriter = new MixinResourceRewriter(LegacyNameMappings.defaults(), "x.jar");
        rewriter.inspectClass("com.ex.Ok", out);
        assertTrue(rewriter.droppedMixins().isEmpty());
    }

    @Test
    void refmapDescriptorsAreRemapped() {
        MixinResourceRewriter rewriter = new MixinResourceRewriter(LegacyNameMappings.defaults(), "x.jar");
        String refmap = "{\"mappings\":{\"com/ex/Ok\":{\"a\":\"L" + OLD + ";get()Ljava/lang/Object;\"}}}";
        String out = new String(rewriter.rewrite("x.refmap.json", refmap.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
        assertTrue(out.contains(NEW));
        assertFalse(out.contains(OLD));
    }

    @Test
    void jarWithoutForgeOwnersIsUntouchedAndCustomTableWorks() {
        ForgeOwnerMappings custom = new ForgeOwnerMappings().add("a/Old", "b/New");
        byte[] in = caller("com/ex/Plain", false, "get", "()Ljava/lang/Object;");
        assertSame(in, new ForgeOwnerRemapStep(custom).apply("com.ex.Plain", in));
    }

    @Test
    void transformVersionInvalidatesOldCache(@TempDir Path dir) throws Exception {
        Path src = JarTransformerTest.buildJar(dir, "adv.jar", JarTransformerTest.MODS_TOML, "1");
        Path cache = Files.createDirectory(dir.resolve("cache"));
        Path stale = cache.resolve(JarTransformer.sha256(src) + "-t5.jar");
        Files.write(stale, new byte[] {1, 2, 3});
        JarTransformer.Result r = new JarTransformer(List.of()).transformCached(src, cache);
        assertFalse(r.cacheHit());
        assertNotEquals(stale, r.output());
    }

    @Test
    void jarTransformerRemapsMixinClassInJar(@TempDir Path dir) throws Exception {
        var files = new java.util.LinkedHashMap<String, byte[]>();
        files.put("com/ex/Ok.class", caller("com/ex/Ok", true, "get", "()Ljava/lang/Object;"));
        files.put("com/ex/Bad.class", caller("com/ex/Bad", true, "noSuchMember", "()Z"));
        files.put("ex.mixins.json", "{\"package\":\"com.ex\",\"mixins\":[\"Ok\",\"Bad\"]}".getBytes(StandardCharsets.UTF_8));
        Path out = dir.resolve("out.jar");
        JarTransformer.withDefaultSteps().transform(SrgCompatTest.jar(dir, "in.jar", files), out);
        // Ok is remapped; Bad is a Mixin using an unsupported member, so it keeps the legacy owner and is dropped
        assertEquals(NEW, ((MethodInsnNode) parse(SrgCompatTest.read(out, "com/ex/Ok.class")).methods.get(0)
                .instructions.toArray()[1]).owner);
        assertEquals(OLD, ((MethodInsnNode) parse(SrgCompatTest.read(out, "com/ex/Bad.class")).methods.get(0)
                .instructions.toArray()[1]).owner);
        String cfg = new String(SrgCompatTest.read(out, "ex.mixins.json"), StandardCharsets.UTF_8);
        assertFalse(cfg.contains("Bad"));
    }

    @Test
    void unsupportedMemberInOneClassDoesNotUnmapTheOwnerElsewhere(@TempDir Path dir) throws Exception {
        var a = new java.util.LinkedHashMap<String, byte[]>();
        a.put("com/ex/Ok.class", caller("com/ex/Ok", false, "get", "()Ljava/lang/Object;"));
        a.put("com/ex/Bad.class", caller("com/ex/Bad", false, "noSuchMember", "()Z"));
        Path outA = dir.resolve("a-out.jar");
        JarTransformer.withDefaultSteps().transform(SrgCompatTest.jar(dir, "a.jar", a), outA);
        assertEquals(NEW, ((MethodInsnNode) parse(SrgCompatTest.read(outA, "com/ex/Ok.class")).methods.get(0)
                .instructions.toArray()[1]).owner);
        byte[] bad = SrgCompatTest.read(outA, "com/ex/Bad.class");
        assertEquals(Set.of(), ForgeOwnerMappings.defaults().remainingOwners(bad));
        assertTrue(new String(bad, StandardCharsets.ISO_8859_1).contains("UnsupportedForgeApi"));
    }
}
