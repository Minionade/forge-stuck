package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.world.item.ArmorItem;
import net.neoforged.forgestuck.compat.LegacyArmorMaterial;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class ArmorMaterialAndModelDataTest {
    private static final String ARMOR = "net/minecraft/world/item/ArmorMaterial";
    private static final String DATA = "net/minecraftforge/client/model/data/ModelData";
    private static final String PROPERTY = "net/minecraftforge/client/model/data/ModelProperty";

    @Test
    void legacyArmorImplementationsLoadAndDispatchEveryMethod() throws Exception {
        for (boolean srg : new boolean[] {true, false}) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/DummyArmor", null, "java/lang/Object",
                    new String[] {ARMOR});
            var ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            ctor.visitCode();
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            ctor.visitInsn(Opcodes.RETURN);
            ctor.visitMaxs(0, 0);
            ctor.visitEnd();
            addArmorMethod(writer, srg, "m_266425_", "(Lnet/minecraft/world/item/ArmorItem$Type;)I", 100);
            addArmorMethod(writer, srg, "m_7366_", "(Lnet/minecraft/world/item/ArmorItem$Type;)I", 5);
            addArmorMethod(writer, srg, "m_6646_", "()I", 15);
            addArmorMethod(writer, srg, "m_7344_", "()Lnet/minecraft/sounds/SoundEvent;", null);
            addArmorMethod(writer, srg, "m_6230_", "()Lnet/minecraft/world/item/crafting/Ingredient;", null);
            addArmorMethod(writer, srg, "m_6082_", "()Ljava/lang/String;", "legacy");
            addArmorMethod(writer, srg, "m_6651_", "()F", 2.0f);
            addArmorMethod(writer, srg, "m_6649_", "()F", 0.1f);

            var call = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "name",
                    "(L" + ARMOR + ";)Ljava/lang/String;", null, null);
            call.visitCode();
            call.visitVarInsn(Opcodes.ALOAD, 0);
            call.visitMethodInsn(Opcodes.INVOKEINTERFACE, ARMOR, srg ? "m_6082_" : "getName",
                    "()Ljava/lang/String;", true);
            call.visitInsn(Opcodes.ARETURN);
            call.visitMaxs(0, 0);
            call.visitEnd();
            writer.visitEnd();

            Class<?> type = transformAndLoad("com.ex.DummyArmor", writer.toByteArray());
            LegacyArmorMaterial material = (LegacyArmorMaterial) type.getConstructor().newInstance();
            assertEquals(100, material.getDurabilityForType(ArmorItem.Type.CHESTPLATE));
            assertEquals(5, material.getDefenseForType(ArmorItem.Type.CHESTPLATE));
            assertEquals(15, material.getEnchantmentValue());
            assertNull(material.getEquipSound());
            assertNull(material.getRepairIngredient());
            assertEquals("legacy", material.getName());
            assertEquals(2.0f, material.getToughness());
            assertEquals(0.1f, material.getKnockbackResistance());
            assertEquals("legacy", type.getMethod("name", LegacyArmorMaterial.class).invoke(null, material));
            assertEquals(Object.class, type.getSuperclass());
        }
    }

    @Test
    void transformedModelDataBuildsDerivesAndValidatesProperties() throws Exception {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/ModelDataUser", null, "java/lang/Object", null);
        var build = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "build",
                "(L" + PROPERTY + ";Ljava/lang/Object;)L" + DATA + ";", null, null);
        build.visitCode();
        build.visitMethodInsn(Opcodes.INVOKESTATIC, DATA, "builder", "()L" + DATA + "$Builder;", false);
        build.visitVarInsn(Opcodes.ALOAD, 0);
        build.visitVarInsn(Opcodes.ALOAD, 1);
        build.visitMethodInsn(Opcodes.INVOKEVIRTUAL, DATA + "$Builder", "with",
                "(L" + PROPERTY + ";Ljava/lang/Object;)L" + DATA + "$Builder;", false);
        build.visitMethodInsn(Opcodes.INVOKEVIRTUAL, DATA + "$Builder", "build", "()L" + DATA + ";", false);
        build.visitInsn(Opcodes.ARETURN);
        build.visitMaxs(0, 0);
        build.visitEnd();
        var property = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "property",
                "(Ljava/util/function/Predicate;)L" + PROPERTY + ";", null, null);
        property.visitCode();
        property.visitTypeInsn(Opcodes.NEW, PROPERTY);
        property.visitInsn(Opcodes.DUP);
        property.visitVarInsn(Opcodes.ALOAD, 0);
        property.visitMethodInsn(Opcodes.INVOKESPECIAL, PROPERTY, "<init>",
                "(Ljava/util/function/Predicate;)V", false);
        property.visitInsn(Opcodes.ARETURN);
        property.visitMaxs(0, 0);
        property.visitEnd();
        var empty = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "empty", "()L" + DATA + ";",
                null, null);
        empty.visitCode();
        empty.visitFieldInsn(Opcodes.GETSTATIC, DATA, "EMPTY", "L" + DATA + ";");
        empty.visitInsn(Opcodes.ARETURN);
        empty.visitMaxs(0, 0);
        empty.visitEnd();
        writer.visitEnd();

        Class<?> type = transformAndLoad("com.ex.ModelDataUser", writer.toByteArray());
        ModelProperty<?> key = (ModelProperty<?>) type.getMethod("property", Predicate.class)
                .invoke(null, (Predicate<Object>) value -> value instanceof String);
        ModelData data = (ModelData) type.getMethod("build", ModelProperty.class, Object.class)
                .invoke(null, key, "original");
        assertTrue(data.has(key));
        assertEquals("original", data.get(key));
        assertEquals(Set.of(key), data.getProperties());
        assertFalse(data.has(new ModelProperty<>()));
        assertSame(ModelData.EMPTY, type.getMethod("empty").invoke(null));
        ModelProperty<String> extra = new ModelProperty<>();
        ModelData derived = data.derive().with(extra, "derived").build();
        assertEquals("original", derived.get(key));
        assertEquals("derived", derived.get(extra));
        assertFalse(data.has(extra));
        assertThrows(IllegalStateException.class, () -> data.derive().with(key, null));
        assertThrows(IllegalStateException.class, () -> ModelData.builder().with(key, null));
    }

    private static void addArmorMethod(ClassWriter writer, boolean srg, String name, String desc, Object value) {
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC,
                srg ? name : LegacyNameMappings.defaults().map(name), desc, null, null);
        method.visitCode();
        if (value == null) method.visitInsn(Opcodes.ACONST_NULL);
        else method.visitLdcInsn(value);
        method.visitInsn(org.objectweb.asm.Type.getReturnType(desc).getOpcode(Opcodes.IRETURN));
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static Class<?> transformAndLoad(String name, byte[] input) {
        byte[] mapped = new ForgeOwnerRemapStep().apply(name, input);
        assertEquals(Set.of(), ForgeOwnerMappings.defaults().remainingOwners(mapped));
        byte[] output = new SrgMemberRemapStep().apply(name, mapped);
        return new ClassLoader(ArmorMaterialAndModelDataTest.class.getClassLoader()) {
            Class<?> define() {
                return defineClass(name, output, 0, output.length);
            }
        }.define();
    }
}
