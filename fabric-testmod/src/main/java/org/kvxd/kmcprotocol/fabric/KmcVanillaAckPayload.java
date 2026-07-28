package org.kvxd.kmcprotocol.fabric;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record KmcVanillaAckPayload(byte[] bytes) implements CustomPayload {
    public static final Id<KmcVanillaAckPayload> ID = new Id<>(Identifier.of("kmcprotocol", "vanilla_ack"));
    public static final PacketCodec<PacketByteBuf, KmcVanillaAckPayload> CODEC = PacketCodec.of(
        (payload, buf) -> buf.writeBytes(payload.bytes),
        buf -> {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new KmcVanillaAckPayload(bytes);
        }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
