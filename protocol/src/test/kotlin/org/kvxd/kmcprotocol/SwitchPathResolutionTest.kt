package org.kvxd.kmcprotocol

import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import org.kvxd.kmcprotocol.core.KmcProtocolException
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.core.encoding.NumberEncoding
import org.kvxd.kmcprotocol.core.encoding.ProtocolSwitch
import org.kvxd.kmcprotocol.core.encoding.resolvePath
import org.kvxd.kmcprotocol.core.encoding.resolvePathOrUnresolved
import org.kvxd.kmcprotocol.core.encoding.switchBranchPresent
import org.kvxd.kmcprotocol.generated.Protocols
import org.kvxd.kmcprotocol.network.Direction
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundPlayerInfoPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.types.CommandNode
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.types.GameProfile
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private enum class SampleAction {
    @SerialName("add_player")
    AddPlayer,

    @SerialName("update_listed")
    UpdateListed,
}

class SwitchPathResolutionTest {

    private suspend fun roundTrip(packet: ClientboundPlayerInfoPacket): ClientboundPlayerInfoPacket {
        val version = Protocols.requireMinecraftVersion("1.20.4")
        val sender = version.protocolData(ProtocolState.Play)
        val receiver = version.protocolData(ProtocolState.Play)

        val channel = ByteChannel()
        sender.send(packet, channel)
        channel.close()

        return assertNotNull(receiver.receive(channel, Direction.Clientbound) as? ClientboundPlayerInfoPacket)
    }

    @Test
    fun `an add player update keeps its profile gamemode and listed fields`() = runTest {
        val uuid = UUID.fromString("68313846-7b77-3c30-8b97-6299a703e93c")
        val packet = ClientboundPlayerInfoPacket(
            action = setOf(
                ClientboundPlayerInfoPacket.Action.AddPlayer,
                ClientboundPlayerInfoPacket.Action.UpdateGameMode,
                ClientboundPlayerInfoPacket.Action.UpdateListed,
            ),
            data = listOf(
                ClientboundPlayerInfoPacket.DataEntry(
                    uuid = uuid,
                    player = GameProfile("kvxd", emptyList()),
                    chatSession = null,
                    gamemode = 1,
                    listed = 1,
                    latency = null,
                    displayName = null,
                )
            ),
        )

        val entry = roundTrip(packet).data.single()
        assertEquals(uuid, entry.uuid)
        assertEquals("kvxd", assertNotNull(entry.player).name)
        assertEquals(1, entry.gamemode)
        assertEquals(1, entry.listed)
        assertEquals(null, entry.latency)
        assertEquals(null, entry.displayName)
        assertEquals(null, entry.chatSession)
    }

    @Test
    fun `flags absent from the action set omit their switch branches`() = runTest {
        val packet = ClientboundPlayerInfoPacket(
            action = setOf(ClientboundPlayerInfoPacket.Action.UpdateLatency),
            data = listOf(
                ClientboundPlayerInfoPacket.DataEntry(
                    uuid = UUID(0L, 7L),
                    player = null,
                    chatSession = null,
                    gamemode = null,
                    listed = null,
                    latency = 42,
                    displayName = null,
                )
            ),
        )

        val entry = roundTrip(packet).data.single()
        assertEquals(42, entry.latency)
        assertEquals(null, entry.player)
        assertEquals(null, entry.gamemode)
    }

    @Test
    fun `the encoded payload is no longer truncated after the uuid`() = runTest {
        val version = Protocols.requireMinecraftVersion("1.20.4")
        val sender = version.protocolData(ProtocolState.Play)
        val packet = ClientboundPlayerInfoPacket(
            action = setOf(
                ClientboundPlayerInfoPacket.Action.AddPlayer,
                ClientboundPlayerInfoPacket.Action.UpdateGameMode,
                ClientboundPlayerInfoPacket.Action.UpdateListed,
            ),
            data = listOf(
                ClientboundPlayerInfoPacket.DataEntry(
                    uuid = UUID(0L, 1L),
                    player = GameProfile("kvxd", emptyList()),
                    chatSession = null,
                    gamemode = 1,
                    listed = 1,
                    latency = null,
                    displayName = null,
                )
            ),
        )

        val channel = ByteChannel()
        sender.send(packet, channel)
        channel.close()

        val decoded = version.protocolData(ProtocolState.Play)
            .receive(channel, Direction.Clientbound) as ClientboundPlayerInfoPacket
        assertNotNull(decoded.data.single().player)
    }

    @Test
    fun `a parent path resolves a flag set membership to a boolean`() {
        val frames = listOf(
            mapOf<String, Any?>("action" to setOf(SampleAction.AddPlayer)),
            mapOf<String, Any?>("uuid" to UUID(0L, 1L)),
        )

        assertEquals(true, frames.resolvePath("../action/add_player"))
        assertEquals(false, frames.resolvePath("../action/update_listed"))
    }

    @Test
    fun `a path resolves a bitfield sub-field through its nested class`() {
        val flags = CommandNode.Flags(
            unused = 0,
            has_custom_suggestions = 0,
            has_redirect_node = 1,
            has_command = 0,
            command_node_type = 2,
        )
        val frames = listOf(mapOf<String, Any?>("flags" to flags))

        assertEquals(1, frames.resolvePath("flags/has_redirect_node"))
        assertEquals(0, frames.resolvePath("flags/has_command"))
        assertEquals(2, frames.resolvePath("flags/command_node_type"))
    }

    @Test
    fun `a nested path ascends out of the current frame`() {
        val flags = CommandNode.Flags(0, 1, 0, 0, 0)
        val frames = listOf(
            mapOf<String, Any?>("flags" to flags),
            mapOf<String, Any?>("name" to "child"),
        )

        assertEquals(1, frames.resolvePath("../flags/has_custom_suggestions"))
    }

    @Test
    fun `a flat path still resolves a plain sibling`() {
        val frames = listOf(mapOf<String, Any?>("present" to true))
        assertEquals(true, frames.resolvePath("present"))
    }

    @Test
    fun `an unresolvable discriminator fails loudly instead of dropping the field`() {
        val spec = ProtocolSwitch(
            compareTo = "../nope/missing",
            presentKeys = arrayOf("true"),
            voidKeys = emptyArray(),
            defaultPresent = false,
        )

        val frames = listOf(mapOf<String, Any?>("something" to 1))
        val unresolved = frames.resolvePathOrUnresolved(spec.compareTo)

        val failure = assertFailsWith<KmcProtocolException> {
            switchBranchPresent(spec, unresolved, "SomePacket.someField")
        }
        assertTrue(failure.message!!.contains("../nope/missing"), failure.message!!)
        assertTrue(failure.message!!.contains("SomePacket.someField"), failure.message!!)
    }

    @Test
    fun `number encodings still map through switch keys`() {
        assertEquals(NumberEncoding.VarInt, NumberEncoding.VarInt)
    }
}
