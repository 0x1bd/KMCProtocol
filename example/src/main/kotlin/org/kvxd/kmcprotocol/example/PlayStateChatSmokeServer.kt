package org.kvxd.kmcprotocol.example

import io.ktor.network.sockets.InetSocketAddress
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.lenni0451.mcstructs.nbt.tags.CompoundTag
import org.kvxd.kmcprotocol.core.KmcProtocolException
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.generated.Protocols
import org.kvxd.kmcprotocol.network.server.Server
import org.kvxd.kmcprotocol.network.server.ServerSession
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.configuration.clientbound.ClientboundCommonSelectKnownPacksPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.configuration.clientbound.ClientboundFeatureFlagsPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.configuration.clientbound.ClientboundFinishConfigurationPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.configuration.clientbound.ClientboundRegistryDataPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.configuration.clientbound.ClientboundTagsPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.configuration.serverbound.ServerboundCommonSelectKnownPacksPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.configuration.serverbound.ServerboundFinishConfigurationPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.handshake.serverbound.ServerboundSetProtocolPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.login.clientbound.ClientboundSuccessPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.login.serverbound.ServerboundLoginAcknowledgedPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.login.serverbound.ServerboundLoginStartPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.play.clientbound.ClientboundLoginPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.play.clientbound.ClientboundPositionPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.play.clientbound.ClientboundSystemChatPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.status.clientbound.ClientboundPingPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.status.clientbound.ClientboundServerInfoPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.status.serverbound.ServerboundPingPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.status.serverbound.ServerboundPingStartPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.types.SpawnInfo

private const val ProtocolVersion = 769
private const val VanillaKnownPackNamespace = "minecraft"
private const val VanillaKnownPackId = "core"
private const val VanillaKnownPackVersion = "1.21.4"
private val VanillaKnownPack = ClientboundCommonSelectKnownPacksPacket.Pack(
    VanillaKnownPackNamespace,
    VanillaKnownPackId,
    VanillaKnownPackVersion,
)

fun main(args: Array<String>) = runBlocking {
    val port = args.getOrNull(0)?.toIntOrNull() ?: 25565
    val server = Server.bind(InetSocketAddress("0.0.0.0", port)) { Protocols.V1_21_4.protocolData() }

    println("KMCProtocol play-state chat smoke server listening on 0.0.0.0:$port")
    println("Use Minecraft Java 1.21.4, Direct Connect to 127.0.0.1:$port")

    while (true) {
        val session = server.accept()
        launch {
            val endpoint = session.remoteAddress
            runCatching { handleSession(session) }
                .onFailure { error ->
                    System.err.println("Session $endpoint failed:")
                    System.err.println(error.describe())
                    session.close()
                }
        }
    }
}

private fun Throwable.describe(): String =
    when (this) {
        is KmcProtocolException -> message ?: toString()
        else -> buildString {
            appendLine("${this@describe::class.qualifiedName}: ${message ?: "no message"}")
            cause?.let {
                appendLine("Caused by: ${it::class.qualifiedName}: ${it.message ?: "no message"}")
            }
        }.trimEnd()
    }

private suspend fun handleSession(session: ServerSession) {
    val handshake = session.expectPacket<ServerboundSetProtocolPacket>("initial handshake")
    when (handshake.nextState) {
        1 -> handleStatus(session)
        2 -> handleLogin(session)
        else -> throw KmcProtocolException(
            """
            Unsupported handshake nextState ${handshake.nextState}.
            Expected: 1 for status or 2 for login.
            Client host field: ${handshake.serverHost}:${handshake.serverPort}
            Protocol version: ${handshake.protocolVersion}
            """.trimIndent()
        )
    }
}

private suspend fun handleStatus(session: ServerSession) {
    when (val packet = session.receive()) {
        is ServerboundPingStartPacket -> {
            session.send(
                ClientboundServerInfoPacket(
                    """
                    {"version":{"name":"1.21.4","protocol":$ProtocolVersion},"players":{"max":20,"online":0},"description":{"text":"KMCProtocol example server"},"enforcesSecureChat":false}
                    """.trimIndent()
                )
            )
        }
        else -> throw KmcProtocolException(
            """
            Unexpected packet during status request.
            Expected: ServerboundPingStartPacket
            Received: ${packet::class.qualifiedName}
            Current state: ${session.data.state}
            """.trimIndent()
        )
    }

    val ping = session.expectPacket<ServerboundPingPacket>("status ping")
    session.send(ClientboundPingPacket(ping.time))
    session.close()
}

private suspend fun handleLogin(session: ServerSession) {
    val start = session.expectPacket<ServerboundLoginStartPacket>("login start")
    val uuid = UUID.nameUUIDFromBytes("OfflinePlayer:${start.username}".toByteArray(Charsets.UTF_8))

    session.send(
        ClientboundSuccessPacket(
            uuid = uuid,
            username = start.username,
            properties = emptyList(),
        )
    )

    session.receiveUntil<ServerboundLoginAcknowledgedPacket>()
    session.sendConfiguration()
    session.receiveUntil<ServerboundFinishConfigurationPacket>()
    session.sendPlayStart()

    delay(1_000)
    session.sendChat("welcome ${start.username}.")
    delay(1_000)
    session.sendChat("This is a tiny offline-mode kmcp 1.21.4 play-state chat smoke server.")
    delay(1_000)
    session.sendChat("If you can read this in the client log, login, configuration, play, NBT chat and compression are working.")
}

private suspend fun ServerSession.sendConfiguration() {
    sessionState(ProtocolState.Configuration)
    send(ClientboundFeatureFlagsPacket(listOf("minecraft:vanilla")))
    send(ClientboundCommonSelectKnownPacksPacket(listOf(VanillaKnownPack)))

    val selectedPacks = receiveUntil<ServerboundCommonSelectKnownPacksPacket>()
    if (selectedPacks.packs.none {
            it.namespace == VanillaKnownPackNamespace &&
                it.id == VanillaKnownPackId &&
                it.version == VanillaKnownPackVersion
        }
    ) {
        throw KmcProtocolException(
            """
            Client did not select the vanilla 1.21.4 known pack.
            Expected: $VanillaKnownPack
            Received: ${selectedPacks.packs}
            """.trimIndent()
        )
    }

    VanillaRegistryData.registries.forEach { (registryId, entries) ->
        send(ClientboundRegistryDataPacket(registryId, entries))
    }
    send(ClientboundTagsPacket(VanillaRegistryData.tags))
    send(ClientboundFinishConfigurationPacket)
}

private suspend fun ServerSession.sendPlayStart() {
    send(
        ClientboundLoginPacket(
            entityId = 1,
            isHardcore = false,
            worldNames = listOf("minecraft:overworld"),
            maxPlayers = 20,
            viewDistance = 8,
            simulationDistance = 8,
            reducedDebugInfo = false,
            enableRespawnScreen = true,
            doLimitedCrafting = false,
            worldState = SpawnInfo(
                dimension = 0,
                name = "minecraft:overworld",
                hashedSeed = 0L,
                gamemode = SpawnInfo.Gamemode.Creative,
                previousGamemode = 255.toShort(),
                isDebug = false,
                isFlat = false,
                death = null,
                portalCooldown = 0,
                seaLevel = 63,
            ),
            enforcesSecureChat = false,
        )
    )
    send(
        ClientboundPositionPacket(
            teleportId = 1,
            x = 0.0,
            y = 80.0,
            z = 0.0,
            dx = 0.0,
            dy = 0.0,
            dz = 0.0,
            yaw = 0.0f,
            pitch = 0.0f,
            flags = emptySet(),
        )
    )
}

private suspend inline fun <reified T> ServerSession.receiveUntil(): T {
    while (true) {
        val packet = receive()
        if (packet is T) return packet
    }
}

private suspend inline fun <reified T> ServerSession.expectPacket(phase: String): T {
    val packet = receive()
    if (packet is T) return packet
    throw KmcProtocolException(
        """
        Unexpected packet during $phase.
        Expected: ${T::class.qualifiedName}
        Received: ${packet::class.qualifiedName}
        Current state: ${data.state}
        Remote endpoint: $remoteAddress
        """.trimIndent()
    )
}

private fun ServerSession.sessionState(state: ProtocolState) {
    check(data.state == state) { "Expected $state, got ${data.state}" }
}

private suspend fun ServerSession.sendChat(text: String) {
    send(ClientboundSystemChatPacket(content = textComponent(text), isActionBar = false))
}

private fun textComponent(text: String) = CompoundTag().addString("text", text)
