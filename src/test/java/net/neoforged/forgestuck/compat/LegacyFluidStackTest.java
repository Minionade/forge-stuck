package net.neoforged.forgestuck.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.forgestuck.testsupport.TestBootstrap;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LegacyFluidStackTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.init();
    }

    @Test
    void packetRoundTripWorksWithPlainFriendlyByteBuf() throws Exception {
        FriendlyByteBuf buffer = newBuffer();
        FluidStack original = new FluidStack(Fluids.LAVA, 1250);
        CompoundTag tag = new CompoundTag();
        tag.putString("marker", "preserved");
        LegacyFluidStack.setTag(original, tag);

        try {
            LegacyFluidStack.writeToPacket(original, buffer);
            FluidStack decoded = LegacyFluidStack.readFromPacket(buffer);

            assertSame(Fluids.LAVA, decoded.getFluid());
            assertEquals(1250, decoded.getAmount());
            assertEquals("preserved", LegacyFluidStack.getTag(decoded).getString("marker"));
        } finally {
            buffer.getClass().getMethod("release").invoke(buffer);
        }
    }

    private static FriendlyByteBuf newBuffer() throws Exception {
        ClassLoader game = FriendlyByteBuf.class.getClassLoader();
        Class<?> byteBuf = Class.forName("io.netty.buffer.ByteBuf", true, game);
        Object raw = Class.forName("io.netty.buffer.Unpooled", true, game).getMethod("buffer").invoke(null);
        return FriendlyByteBuf.class.getConstructor(byteBuf).newInstance(raw);
    }
}
