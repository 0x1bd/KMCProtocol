package de.kvxd.kmcprotocol.core

import de.kvxd.kmcprotocol.core.encoding.serializers.ComponentSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.reflect.KClass

class ProtocolData {

    internal val serializersModule = SerializersModule {
        contextual(Component::class, ComponentSerializer)

        polymorphic(Component::class) {
            subclass(TextComponent::class)
        }
    }

    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    internal var state = ProtocolState.Handshake

    // By default, register all packets available in ProtocolState
    @Internal
    var registry = PacketRegistry().apply {
        ProtocolState.entries.forEach { state ->

            state.packets.forEach { packetKClass: KClass<out MinecraftPacket> ->
                register(packetKClass, state)
            }
        }
    }

    fun redefineRegistry(registry: PacketRegistry.() -> Unit) {
        this.registry = PacketRegistry().apply(registry)
    }

}