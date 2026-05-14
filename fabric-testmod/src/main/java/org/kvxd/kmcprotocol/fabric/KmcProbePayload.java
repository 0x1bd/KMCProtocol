package org.kvxd.kmcprotocol.fabric;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record KmcProbePayload(byte[] bytes) implements CustomPayload {
    public static final Id<KmcProbePayload> ID = new Id<>(Identifier.of("kmcprotocol", "probe"));
    public static final PacketCodec<PacketByteBuf, KmcProbePayload> CODEC = PacketCodec.of(
        (payload, buf) -> buf.writeBytes(payload.bytes),
        buf -> {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new KmcProbePayload(bytes);
        }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
