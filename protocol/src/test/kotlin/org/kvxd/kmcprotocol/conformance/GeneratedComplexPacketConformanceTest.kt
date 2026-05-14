package org.kvxd.kmcprotocol.conformance

import org.kvxd.kmcprotocol.generated.GeneratedProtocol
import io.ktor.utils.io.ByteChannel
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtInt
import net.benwoodworth.knbt.NbtString
import org.kvxd.kmcprotocol.core.MinecraftPacket
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.core.encoding.ProtocolValue
import org.kvxd.kmcprotocol.network.Direction
import org.kvxd.kmcprotocol.packets.generated.configuration.clientbound.ClientboundCommonSelectKnownPacksPacket
import org.kvxd.kmcprotocol.packets.generated.play.clientbound.ClientboundDeclareCommandsPacket
import org.kvxd.kmcprotocol.packets.generated.play.clientbound.ClientboundDeclareRecipesPacket
import org.kvxd.kmcprotocol.packets.generated.play.clientbound.ClientboundNbtQueryResponsePacket
import org.kvxd.kmcprotocol.packets.generated.play.clientbound.ClientboundSystemChatPacket
import org.kvxd.kmcprotocol.packets.generated.play.clientbound.ClientboundTagsPacket
import org.kvxd.kmcprotocol.packets.generated.types.NodesEntry
import org.kvxd.kmcprotocol.packets.generated.types.NodesEntryFlags
import org.kvxd.kmcprotocol.packets.generated.types.PacksEntry
import org.kvxd.kmcprotocol.packets.generated.types.StoneCutterRecipesEntry
import org.kvxd.kmcprotocol.packets.generated.types.StoneCutterRecipesEntryInput
import org.kvxd.kmcprotocol.packets.generated.types.StoneCutterRecipesEntrySlotDisplay
import org.kvxd.kmcprotocol.packets.generated.types.StoneCutterRecipesEntrySlotDisplayType
import org.kvxd.kmcprotocol.packets.generated.types.TagsEntry
import org.kvxd.kmcprotocol.packets.generated.types.TagsEntryTagsEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class GeneratedComplexPacketConformanceTest {

    @Test
    fun `configuration array of generated containers round trips`() = runTest {
        val packet = ClientboundCommonSelectKnownPacksPacket(
            packs = listOf(
                PacksEntry("minecraft", "core", "1.21.4"),
                PacksEntry("kmcprotocol", "fixture", "1"),
            )
        )

        assertEquals(packet, roundTrip(packet, ProtocolState.Configuration, Direction.Clientbound))
    }

    @Test
    fun `play tags packet with nested arrays round trips`() = runTest {
        val packet = ClientboundTagsPacket(
            tags = listOf(
                TagsEntry(
                    tagType = "minecraft:block",
                    tags = listOf(
                        TagsEntryTagsEntry("minecraft:mineable/pickaxe", listOf(1, 2, 3)),
                        TagsEntryTagsEntry("minecraft:needs_stone_tool", listOf(4, 5)),
                    ),
                )
            )
        )

        assertEquals(packet, roundTrip(packet, ProtocolState.Play, Direction.Clientbound))
    }

    @Test
    fun `anonymous nbt packet round trips`() = runTest {
        val packet = ClientboundSystemChatPacket(
            content = NbtCompound(
                mapOf(
                    "text" to NbtString("KMCProtocol"),
                    "color" to NbtString("green"),
                )
            ),
            isActionBar = false,
        )

        assertEquals(packet, roundTrip(packet, ProtocolState.Play, Direction.Clientbound))
    }

    @Test
    fun `optional nbt packet round trips`() = runTest {
        val packet = ClientboundNbtQueryResponsePacket(
            transactionId = 42,
            nbt = NbtCompound(
                mapOf(
                    "name" to NbtString("KMCProtocol"),
                    "value" to NbtInt(7),
                )
            ),
        )

        assertEquals(packet, roundTrip(packet, ProtocolState.Play, Direction.Clientbound))
    }

    @Test
    fun `recipe holder sets and slot display switch payloads round trip`() = runTest {
        val packet = ClientboundDeclareRecipesPacket(
            recipes = emptyList(),
            stoneCutterRecipes = listOf(
                StoneCutterRecipesEntry(
                    input = StoneCutterRecipesEntryInput(name = null, ids = listOf(1, 2)),
                    slotDisplay = StoneCutterRecipesEntrySlotDisplay(
                        type = StoneCutterRecipesEntrySlotDisplayType.Item,
                        data = ProtocolValue(5),
                    ),
                ),
                StoneCutterRecipesEntry(
                    input = StoneCutterRecipesEntryInput(name = "minecraft:stone_tool_materials", ids = null),
                    slotDisplay = StoneCutterRecipesEntrySlotDisplay(
                        type = StoneCutterRecipesEntrySlotDisplayType.Tag,
                        data = ProtocolValue("minecraft:planks"),
                    ),
                ),
            ),
        )

        assertEquals(packet, roundTrip(packet, ProtocolState.Play, Direction.Clientbound))
    }

    @Test
    fun `command node switch paths round trip`() = runTest {
        val packet = ClientboundDeclareCommandsPacket(
            nodes = listOf(
                NodesEntry(
                    flags = NodesEntryFlags(
                        unused = 0,
                        has_custom_suggestions = 0,
                        has_redirect_node = 0,
                        has_command = 0,
                        command_node_type = 0,
                    ),
                    children = listOf(1),
                    redirectNode = ProtocolValue(null),
                    extraNodeData = ProtocolValue(null),
                ),
                NodesEntry(
                    flags = NodesEntryFlags(
                        unused = 0,
                        has_custom_suggestions = 1,
                        has_redirect_node = 1,
                        has_command = 1,
                        command_node_type = 2,
                    ),
                    children = emptyList(),
                    redirectNode = ProtocolValue(0),
                    extraNodeData = ProtocolValue.obj(
                        mapOf(
                            "name" to ProtocolValue("message"),
                            "parser" to ProtocolValue("brigadier:string"),
                            "properties" to ProtocolValue("SINGLE_WORD"),
                            "suggestionType" to ProtocolValue("minecraft:ask_server"),
                        )
                    ),
                ),
            ),
            rootIndex = 0,
        )

        assertEquals(packet, roundTrip(packet, ProtocolState.Play, Direction.Clientbound))
    }

    private suspend fun roundTrip(
        packet: MinecraftPacket,
        state: ProtocolState,
        direction: Direction,
    ): MinecraftPacket? {
        val data = GeneratedProtocol.protocolData(state)

        val channel = ByteChannel()
        data.send(packet, channel)
        channel.close()

        val receiver = GeneratedProtocol.protocolData(state)
        return receiver.receive(channel, direction)
    }
}
