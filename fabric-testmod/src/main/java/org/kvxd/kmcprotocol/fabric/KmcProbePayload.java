package org.kvxd.kmcprotocol.fabric;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record KmcProbePayload(String nonce, byte[] bytes) implements CustomPayload {
    public static final Id<KmcProbePayload> ID = new Id<>(Identifier.of(KmcProtocolTestMod.MOD_ID, "probe"));
    public static final PacketCodec<PacketByteBuf, KmcProbePayload> CODEC =
        PacketCodec.of(KmcProbePayload::write, KmcProbePayload::new);

    private KmcProbePayload(PacketByteBuf buf) {
        this(buf.readString(128), buf.readByteArray(32767));
    }

    private void write(PacketByteBuf buf) {
        buf.writeString(nonce, 128);
        buf.writeByteArray(bytes);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}