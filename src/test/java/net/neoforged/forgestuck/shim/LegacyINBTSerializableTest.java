package net.neoforged.forgestuck.shim;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.forgestuck.compat.LegacyINBTSerializable;
import org.junit.jupiter.api.Test;

class LegacyINBTSerializableTest {
    @Test
    void registryAwareMethodsDelegateToForgeHooks() {
        CompoundTag expected = new CompoundTag();
        expected.putInt("value", 7);
        CompoundTag[] received = new CompoundTag[1];
        LegacyINBTSerializable<CompoundTag> serializable = new LegacyINBTSerializable<>() {
            @Override
            public CompoundTag serializeNBT() {
                return expected;
            }

            @Override
            public void deserializeNBT(CompoundTag tag) {
                received[0] = tag;
            }
        };

        assertSame(expected, serializable.serializeNBT(null));
        serializable.deserializeNBT(null, expected);
        assertSame(expected, received[0]);
    }
}
