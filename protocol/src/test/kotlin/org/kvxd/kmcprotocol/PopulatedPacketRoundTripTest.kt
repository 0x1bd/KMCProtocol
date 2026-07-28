package org.kvxd.kmcprotocol

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import org.kvxd.kmcprotocol.core.MinecraftPacket
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.generated.Protocols
import org.kvxd.kmcprotocol.network.Direction
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.configuration.clientbound.ClientboundCommonSelectKnownPacksPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.configuration.clientbound.ClientboundFeatureFlagsPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.login.clientbound.ClientboundSuccessPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.play.serverbound.ServerboundQueryBlockNbtPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.types.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PopulatedPacketRoundTripTest {

    @Test
    fun `arrays carry their elements through a round trip`() = runTest {
        val packet = ClientboundFeatureFlagsPacket(
            features = listOf("minecraft:vanilla", "minecraft:bundle", "minecraft:trade_rebalance"),
        )

        assertEquals(packet, roundTrip(packet, ProtocolState.Configuration, Direction.Clientbound))
    }

    @Test
    fun `arrays of nested containers carry every field`() = runTest {
        val packet = ClientboundCommonSelectKnownPacksPacket(
            packs = listOf(
                ClientboundCommonSelectKnownPacksPacket.Pack("minecraft", "core", "1.21.4"),
                ClientboundCommonSelectKnownPacksPacket.Pack("kmcprotocol", "test", "0.1"),
            ),
        )

        assertEquals(packet, roundTrip(packet, ProtocolState.Configuration, Direction.Clientbound))
    }

    @Test
    fun `uuid strings and present and absent options survive a round trip`() = runTest {
        val packet = ClientboundSuccessPacket(
            uuid = java.util.UUID.fromString("f84c6a79-0a4e-45e0-879b-cd49ebd4c4e2"),
            username = "KmcProtocol",
            properties = listOf(
                ClientboundSuccessPacket.Property("textures", "payload", "signed"),
                ClientboundSuccessPacket.Property("unsigned", "payload", null),
            ),
        )

        val decoded = roundTrip(packet, ProtocolState.Login, Direction.Clientbound)
        assertEquals(packet, decoded)
        assertTrue(decoded is ClientboundSuccessPacket && decoded.properties[1].signature == null)
    }

    @Test
    fun `signed bitfield components survive a round trip`() = runTest {
        val packet = ServerboundQueryBlockNbtPacket(
            transactionId = 7,
            location = Position(x = -33_554_431, z = 33_554_431, y = -2047),
        )

        assertEquals(packet, roundTrip(packet, ProtocolState.Play, Direction.Serverbound))
    }

    @Test
    fun `out of range bitfield components are rejected instead of silently truncated`() {
        assertFailsWith<IllegalArgumentException> { Position(x = 999_999_999, z = 0, y = 0) }
        assertFailsWith<IllegalArgumentException> { Position(x = 0, z = 0, y = 2048) }
        assertFailsWith<IllegalArgumentException> { Position(x = -33_554_433, z = 0, y = 0) }

        Position(x = -33_554_432, z = 33_554_431, y = -2048)
    }

    private suspend fun roundTrip(
        packet: MinecraftPacket,
        state: ProtocolState,
        direction: Direction,
    ): MinecraftPacket? {
        val data = Protocols.V1_21_4.protocolData(initialState = state, automaticProtocolFlow = false)
        val channel = ByteChannel()
        data.send(packet, channel)
        channel.close()
        return data.receive(ByteReadChannel(channel.readRemaining().readByteArray()), direction)
    }
}
