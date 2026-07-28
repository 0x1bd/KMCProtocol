package org.kvxd.kmcprotocol.fabric;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import net.minecraft.network.NetworkState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.state.ConfigurationStates;
import net.minecraft.network.state.HandshakeStates;
import net.minecraft.network.state.LoginStates;
import net.minecraft.network.state.PlayStateFactories;
import net.minecraft.network.state.QueryStates;
import net.minecraft.registry.DynamicRegistryManager;

public final class VanillaCodecProbe {

    public static final byte OK = 0;
    public static final byte FAILED = 1;

    private VanillaCodecProbe() {
    }

    public static byte[] reencode(DynamicRegistryManager registries, byte[] request) {
        try {
            ByteBuf incoming = Unpooled.wrappedBuffer(request);
            int state = incoming.readByte() & 0xff;
            int direction = incoming.readByte() & 0xff;

            byte[] body = new byte[incoming.readableBytes()];
            incoming.readBytes(body);

            NetworkState<?> networkState = resolve(state, direction, registries);
            if (networkState == null) {
                return failure("No vanilla network state for state=" + state + " direction=" + direction);
            }

            @SuppressWarnings("unchecked")
            PacketCodec<ByteBuf, Packet<?>> codec = (PacketCodec<ByteBuf, Packet<?>>) networkState.codec();

            ByteBuf decodeBuf = wrap(networkState, registries, Unpooled.wrappedBuffer(body));
            Packet<?> packet = codec.decode(decodeBuf);
            int trailing = decodeBuf.readableBytes();
            if (trailing != 0) {
                return failure("Vanilla decode left " + trailing + " trailing bytes for " + packet.getClass().getName());
            }

            ByteBuf encodeBuf = wrap(networkState, registries, Unpooled.buffer());
            codec.encode(encodeBuf, packet);

            byte[] reencoded = new byte[encodeBuf.readableBytes()];
            encodeBuf.readBytes(reencoded);

            byte[] response = new byte[reencoded.length + 1];
            response[0] = OK;
            System.arraycopy(reencoded, 0, response, 1, reencoded.length);
            return response;
        } catch (Throwable cause) {
            String name = cause.getClass().getSimpleName();
            String message = cause.getMessage();
            return failure(message == null ? name : name + ": " + message);
        }
    }

    private static ByteBuf wrap(NetworkState<?> state, DynamicRegistryManager registries, ByteBuf raw) {
        if (state.id() == net.minecraft.network.NetworkPhase.PLAY) {
            return new RegistryByteBuf(raw, registries);
        }
        return new PacketByteBuf(raw);
    }

    private static NetworkState<?> resolve(int state, int direction, DynamicRegistryManager registries) {
        boolean serverbound = direction == 0;
        switch (state) {
            case 0:
                return serverbound ? HandshakeStates.C2S : null;
            case 1:
                return serverbound ? QueryStates.C2S : QueryStates.S2C;
            case 2:
                return serverbound ? LoginStates.C2S : LoginStates.S2C;
            case 3:
                return serverbound ? ConfigurationStates.C2S : ConfigurationStates.S2C;
            case 4:
                return serverbound
                    ? PlayStateFactories.C2S.bind(RegistryByteBuf.makeFactory(registries))
                    : PlayStateFactories.S2C.bind(RegistryByteBuf.makeFactory(registries));
            default:
                return null;
        }
    }

    private static byte[] failure(String message) {
        byte[] text = message.getBytes(StandardCharsets.UTF_8);
        byte[] response = new byte[text.length + 1];
        response[0] = FAILED;
        System.arraycopy(text, 0, response, 1, text.length);
        return response;
    }
}
