package org.kvxd.kmcprotocol.fabric;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record KmcVanillaProbePayload(byte[] bytes) implements CustomPayload {
    public static final Id<KmcVanillaProbePayload> ID = new Id<>(Identifier.of("kmcprotocol", "vanilla_probe"));
    public static final PacketCodec<PacketByteBuf, KmcVanillaProbePayload> CODEC = PacketCodec.of(
        (payload, buf) -> buf.writeBytes(payload.bytes),
        buf -> {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new KmcVanillaProbePayload(bytes);
        }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
