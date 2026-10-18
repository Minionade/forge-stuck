package net.neoforged.forgestuck.shim;

import static org.junit.jupiter.api.Assertions.*;

import net.neoforged.forgestuck.compat.LegacyDyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.forgestuck.testsupport.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LegacyDyeableLeatherItemTest {
    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    void dyeColorUsesTheNativeComponent() {
        LegacyDyeableLeatherItem dyeable = new LegacyDyeableLeatherItem() {};
        ItemStack stack = new ItemStack(Items.LEATHER_HELMET);

        assertFalse(dyeable.hasCustomColor(stack));
        assertEquals(LegacyDyeableLeatherItem.DEFAULT_LEATHER_COLOR, dyeable.getColor(stack));
        dyeable.setColor(stack, 0x123456);
        assertTrue(dyeable.hasCustomColor(stack));
        assertEquals(0x123456, dyeable.getColor(stack));
        dyeable.clearColor(stack);
        assertFalse(dyeable.hasCustomColor(stack));
    }
}
