package org.kvxd.kmcprotocol.conformance

import org.kvxd.kmcprotocol.generated.Protocols
import io.ktor.utils.io.ByteChannel
import net.lenni0451.mcstructs.nbt.tags.CompoundTag
import org.kvxd.kmcprotocol.core.MinecraftPacket
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.network.Direction
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.configuration.clientbound.ClientboundCommonSelectKnownPacksPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.play.clientbound.ClientboundDeclareCommandsPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.play.clientbound.ClientboundDeclareRecipesPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.play.clientbound.ClientboundNbtQueryResponsePacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.play.clientbound.ClientboundSystemChatPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.play.clientbound.ClientboundTagsPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.types.CommandNode
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.types.IDSet
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.types.SlotDisplay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class GeneratedComplexPacketConformanceTest {

    @Test
    fun `configuration array of generated containers round trips`() = runTest {
        val packet = ClientboundCommonSelectKnownPacksPacket(
            packs = listOf(
                ClientboundCommonSelectKnownPacksPacket.Pack("minecraft", "core", "1.21.4"),
                ClientboundCommonSelectKnownPacksPacket.Pack("kmcprotocol", "fixture", "1"),
            )
        )

        assertEquals(packet, roundTrip(packet, ProtocolState.Configuration, Direction.Clientbound))
    }

    @Test
    fun `play tags packet with nested arrays round trips`() = runTest {
        val packet = ClientboundTagsPacket(
            tags = listOf(
                ClientboundTagsPacket.Tag(
                    tagType = "minecraft:block",
                    tags = listOf(
                        ClientboundTagsPacket.Tag.Entry("minecraft:mineable/pickaxe", listOf(1, 2, 3)),
                        ClientboundTagsPacket.Tag.Entry("minecraft:needs_stone_tool", listOf(4, 5)),
                    ),
                )
            )
        )

        assertEquals(packet, roundTrip(packet, ProtocolState.Play, Direction.Clientbound))
    }

    @Test
    fun `anonymous nbt packet round trips`() = runTest {
        val packet = ClientboundSystemChatPacket(
            content = CompoundTag()
                .addString("text", "KMCProtocol")
                .addString("color", "green"),
            isActionBar = false,
        )

        assertEquals(packet, roundTrip(packet, ProtocolState.Play, Direction.Clientbound))
    }

    @Test
    fun `optional nbt packet round trips`() = runTest {
        val packet = ClientboundNbtQueryResponsePacket(
            transactionId = 42,
            nbt = CompoundTag()
                .addString("name", "KMCProtocol")
                .addInt("value", 7),
        )

        assertEquals(packet, roundTrip(packet, ProtocolState.Play, Direction.Clientbound))
    }

    @Test
    fun `recipe holder sets and slot display switch payloads round trip`() = runTest {
        val packet = ClientboundDeclareRecipesPacket(
            recipes = emptyList(),
            stoneCutterRecipes = listOf(
                ClientboundDeclareRecipesPacket.StoneCutterRecipe(
                    input = IDSet(name = null, ids = listOf(1, 2)),
                    slotDisplay = SlotDisplay(
                        type = SlotDisplay.Type.Item,
                        data = SlotDisplay.Data(
                            item = 5,
                            item_stack = null,
                            tag = null,
                            smithing_trim = null,
                            with_remainder = null,
                            composite = null,
                        ),
                    ),
                ),
                ClientboundDeclareRecipesPacket.StoneCutterRecipe(
                    input = IDSet(name = "minecraft:stone_tool_materials", ids = null),
                    slotDisplay = SlotDisplay(
                        type = SlotDisplay.Type.Tag,
                        data = SlotDisplay.Data(
                            item = null,
                            item_stack = null,
                            tag = "minecraft:planks",
                            smithing_trim = null,
                            with_remainder = null,
                            composite = null,
                        ),
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
                CommandNode(
                    flags = CommandNode.Flags(
                        unused = 0,
                        has_custom_suggestions = 0,
                        has_redirect_node = 0,
                        has_command = 0,
                        command_node_type = 0,
                    ),
                    children = listOf(1),
                    redirectNode = null,
                    extraNodeData = CommandNode.ExtraNodeData(
                        field_1 = null,
                        field_2 = null,
                    ),
                ),
                CommandNode(
                    flags = CommandNode.Flags(
                        unused = 0,
                        has_custom_suggestions = 1,
                        has_redirect_node = 1,
                        has_command = 1,
                        command_node_type = 2,
                    ),
                    children = emptyList(),
                    redirectNode = 0,
                    extraNodeData = CommandNode.ExtraNodeData(
                        field_1 = null,
                        field_2 = CommandNode.ExtraNodeData2(
                            name = "message",
                            parser = CommandNode.ExtraNodeData2.Parser.BrigadierString,
                            properties = CommandNode.ExtraNodeData2.Properties(
                                brigadier_float = null,
                                brigadier_double = null,
                                brigadier_integer = null,
                                brigadier_long = null,
                                brigadier_string = CommandNode.ExtraNodeData2.PropertiesBrigadierString.SINGLEWORD,
                                minecraft_entity = null,
                                minecraft_score_holder = null,
                                minecraft_time = null,
                                minecraft_resource_or_tag = null,
                                minecraft_resource_or_tag_key = null,
                                minecraft_resource = null,
                                minecraft_resource_key = null,
                            ),
                            suggestionType = "minecraft:ask_server",
                        ),
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
        val data = Protocols.V1_21_4.protocolData(state)

        val channel = ByteChannel()
        data.send(packet, channel)
        channel.close()

        val receiver = Protocols.V1_21_4.protocolData(state)
        return receiver.receive(channel, direction)
    }
}
