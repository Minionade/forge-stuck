package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class LegacyConditionCodecStepTest {
    @Test
    void generatesCodecForLegacyImplementers() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "x/Cond", null, "java/lang/Object",
                new String[] {LegacyConditionCodecStep.LEGACY_ICONDITION});
        byte[] out = new LegacyConditionCodecStep().apply("x/Cond", cw.toByteArray());
        org.objectweb.asm.tree.ClassNode n = new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(out).accept(n, 0);
        assertTrue(n.methods.stream().anyMatch(m -> m.name.equals("codec")));
        assertEquals(out.length, new LegacyConditionCodecStep().apply("x/Cond", out).length, "idempotent");
    }

    @Test
    void mapsConditionOwners() {
        ForgeOwnerMappings m = ForgeOwnerMappings.defaults();
        assertNotNull(m.get("net/minecraftforge/common/crafting/CraftingHelper"));
        assertNotNull(m.get("net/minecraftforge/common/crafting/conditions/IConditionSerializer"));
        assertTrue(Arrays.stream(ConditionMappings.entries().toArray()).count() > 5);
    }
}