package org.kvxd.kmcprotocol.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;

public final class KmcProtocolTestMod implements ModInitializer {
    public static final String MOD_ID = "kmcprotocol";

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.configurationC2S().register(KmcProbePayload.ID, KmcProbePayload.CODEC);
        PayloadTypeRegistry.configurationS2C().register(KmcAckPayload.ID, KmcAckPayload.CODEC);

        ServerConfigurationNetworking.registerGlobalReceiver(KmcProbePayload.ID, (payload, context) -> {
            String message = "OK:" + payload.nonce() + ":" + payload.bytes().length;
            context.responseSender().sendPacket(new KmcAckPayload(message));
        });
    }
}
