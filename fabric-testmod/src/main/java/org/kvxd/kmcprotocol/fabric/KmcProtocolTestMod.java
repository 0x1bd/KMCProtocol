package org.kvxd.kmcprotocol.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;

public final class KmcProtocolTestMod implements ModInitializer {
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.configurationC2S().register(KmcProbePayload.ID, KmcProbePayload.CODEC);
        PayloadTypeRegistry.configurationS2C().register(KmcAckPayload.ID, KmcAckPayload.CODEC);
        PayloadTypeRegistry.configurationC2S().register(KmcVanillaProbePayload.ID, KmcVanillaProbePayload.CODEC);
        PayloadTypeRegistry.configurationS2C().register(KmcVanillaAckPayload.ID, KmcVanillaAckPayload.CODEC);

        ServerConfigurationNetworking.registerGlobalReceiver(KmcProbePayload.ID, (payload, context) -> {
            context.responseSender().sendPacket(new KmcAckPayload(payload.bytes()));
        });

        ServerConfigurationNetworking.registerGlobalReceiver(KmcVanillaProbePayload.ID, (payload, context) -> {
            byte[] response = VanillaCodecProbe.reencode(
                context.server().getRegistryManager(),
                payload.bytes()
            );
            context.responseSender().sendPacket(new KmcVanillaAckPayload(response));
        });
    }
}
