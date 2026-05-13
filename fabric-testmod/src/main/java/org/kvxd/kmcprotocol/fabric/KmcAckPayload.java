package org.kvxd.kmcprotocol.fabric;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record KmcAckPayload(String message) implements CustomPayload {
    public static final Id<KmcAckPayload> ID = new Id<>(Identifier.of(KmcProtocolTestMod.MOD_ID, "ack"));
    public static final PacketCodec<PacketByteBuf, KmcAckPayload> CODEC =
        PacketCodec.of(KmcAckPayload::write, KmcAckPayload::new);

    private KmcAckPayload(PacketByteBuf buf) {
        this(buf.readString(256));
    }

    private void write(PacketByteBuf buf) {
        buf.writeString(message, 256);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}