package org.kvxd.kmcprotocol

import org.kvxd.kmcprotocol.core.PacketMetadata
import org.kvxd.kmcprotocol.core.ProtocolData
import org.kvxd.kmcprotocol.core.ProtocolState
import kotlin.reflect.full.findAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeneratedPacketRegistryTest {

    @Test
    fun `all generated packets are registered by metadata`() {
        val data = ProtocolData()
        val allPackets = ProtocolState.entries.flatMap { state -> state.packets.map { state to it } }

        assertTrue(allPackets.isNotEmpty(), "Expected generated packet registry to contain packets")

        ProtocolState.entries.forEach { state ->
            val packetKeys = mutableSetOf<Pair<Int, Any>>()

            state.packets.forEach { packetClass ->
                val metadata = packetClass.findAnnotation<PacketMetadata>()
                assertNotNull(metadata, "${packetClass.qualifiedName} is missing PacketMetadata")
                assertTrue(
                    packetKeys.add(metadata.id to metadata.direction),
                    "Duplicate packet id 0x${metadata.id.toString(16)} for ${metadata.direction} in $state"
                )

                assertNotNull(
                    data.registry.getPacketSerializerByClass(state, packetClass),
                    "${packetClass.qualifiedName} is not registered by class"
                )

                val registeredById = data.registry.getPacketMetadata(state, metadata.id, metadata.direction)
                assertNotNull(
                    registeredById,
                    "${packetClass.qualifiedName} is not registered by id and direction"
                )
                assertEquals(packetClass, registeredById.packetClass)
            }
        }
    }
}
