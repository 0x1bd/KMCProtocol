package org.kvxd.kmcprotocol.example

import io.ktor.utils.io.ByteChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.kvxd.kmcprotocol.core.ProtocolData
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.network.Direction
import org.kvxd.kmcprotocol.packets.generated.configuration.clientbound.ClientboundRegistryDataPacket
import org.kvxd.kmcprotocol.packets.generated.configuration.clientbound.ClientboundTagsPacket

class VanillaRegistryDataTest {

    @Test
    fun `cached vanilla registry data contains enchantment payloads`() {
        val registries = VanillaRegistryData.registries.toMap()
        val enchantments = assertNotNull(registries["minecraft:enchantment"])

        assertTrue(VanillaRegistryData.registries.all { (_, entries) -> entries.isNotEmpty() })
        assertNotNull(enchantments.singleOrNull { it.key == "minecraft:aqua_affinity" })
        assertTrue(enchantments.all { it.value == null })
    }

    @Test
    fun `cached vanilla tags bind enchantment exclusive sets`() {
        val enchantmentTags = assertNotNull(VanillaRegistryData.tags.singleOrNull { it.tagType == "minecraft:enchantment" })
        val tagNames = enchantmentTags.tags.map { it.tagName }.toSet()

        assertTrue("minecraft:exclusive_set/armor" in tagNames)
        assertTrue("minecraft:exclusive_set/boots" in tagNames)
        assertTrue("minecraft:exclusive_set/bow" in tagNames)
        assertTrue("minecraft:exclusive_set/crossbow" in tagNames)
        assertTrue("minecraft:exclusive_set/damage" in tagNames)
        assertTrue("minecraft:exclusive_set/mining" in tagNames)
        assertTrue("minecraft:exclusive_set/riptide" in tagNames)
        assertTrue(enchantmentTags.tags.all { it.entries.isNotEmpty() })
    }

    @Test
    fun `real enchantment registry packet round trips`() = runTest {
        val enchantments = VanillaRegistryData.registries.toMap().getValue("minecraft:enchantment")
        val packet = ClientboundRegistryDataPacket("minecraft:enchantment", enchantments)

        val sender = ProtocolData(ProtocolState.Configuration)

        val channel = ByteChannel()
        sender.send(packet, channel)
        channel.close()

        val receiver = ProtocolData(ProtocolState.Configuration)

        assertEquals(packet, receiver.receive(channel, Direction.Clientbound))
    }

    @Test
    fun `vanilla tags packet round trips`() = runTest {
        val packet = ClientboundTagsPacket(VanillaRegistryData.tags)

        val sender = ProtocolData(ProtocolState.Configuration)

        val channel = ByteChannel()
        sender.send(packet, channel)
        channel.close()

        val receiver = ProtocolData(ProtocolState.Configuration)

        assertEquals(packet, receiver.receive(channel, Direction.Clientbound))
    }
}
