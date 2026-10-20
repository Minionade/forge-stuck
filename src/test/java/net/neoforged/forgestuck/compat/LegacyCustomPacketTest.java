package net.neoforged.forgestuck.compat;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;

import net.minecraft.network.protocol.Packet;
import org.junit.jupiter.api.Test;

class LegacyCustomPacketTest {
    @Test
    void preservesForgeInterfaceShapeAndPacket() {
        assertTrue(LegacyCustomPacket.class.isInterface());
        Packet<?> packet = (Packet<?>) Proxy.newProxyInstance(
                Packet.class.getClassLoader(), new Class<?>[] {Packet.class}, (proxy, method, args) -> null);

        assertSame(packet, LegacyCustomPacket.wrap(packet).getThis());
    }
}
