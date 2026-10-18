package net.neoforged.forgestuck.compat;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;

/**
 * Receive-side Forge 1.20.1 spawn message data, preserving packed rotation/velocity units.
 * This is not a replacement transport or a client entity factory. The caller owns the
 * decoded additional-data buffer and must release it after consuming the message.
 */
public final class LegacySpawnEntity {
    private final int typeId, entityId, velX, velY, velZ;
    private final UUID uuid;
    private final double posX, posY, posZ;
    private final byte pitch, yaw, headYaw;
    private final FriendlyByteBuf additionalData;

    private LegacySpawnEntity(FriendlyByteBuf input) {
        typeId = input.readVarInt();
        entityId = input.readInt();
        uuid = new UUID(input.readLong(), input.readLong());
        posX = input.readDouble();
        posY = input.readDouble();
        posZ = input.readDouble();
        pitch = input.readByte();
        yaw = input.readByte();
        headYaw = input.readByte();
        velX = input.readShort();
        velY = input.readShort();
        velZ = input.readShort();
        int length = input.readVarInt();
        if (length < 0 || length > input.readableBytes()) {
            throw new IllegalArgumentException("Invalid legacy entity spawn data length: " + length);
        }
        byte[] data = new byte[length];
        input.readBytes(data);
        additionalData = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
    }

    public static LegacySpawnEntity decode(FriendlyByteBuf input) { return new LegacySpawnEntity(input); }
    // Forge's decoded messages also have no server-side Entity instance.
    public Entity getEntity() { return null; }
    public int getTypeId() { return typeId; }
    public int getEntityId() { return entityId; }
    public UUID getUuid() { return uuid; }
    public double getPosX() { return posX; }
    public double getPosY() { return posY; }
    public double getPosZ() { return posZ; }
    public byte getPitch() { return pitch; }
    public byte getYaw() { return yaw; }
    public byte getHeadYaw() { return headYaw; }
    public int getVelX() { return velX; }
    public int getVelY() { return velY; }
    public int getVelZ() { return velZ; }
    public FriendlyByteBuf getAdditionalData() { return additionalData; }
}
