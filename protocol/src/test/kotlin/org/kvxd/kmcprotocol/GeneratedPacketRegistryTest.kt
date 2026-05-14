package org.kvxd.kmcprotocol

import org.kvxd.kmcprotocol.core.PacketMetadata
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.generated.GeneratedPacketRegistry
import org.kvxd.kmcprotocol.generated.GeneratedProtocol
import kotlin.reflect.full.findAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeneratedPacketRegistryTest {

    @Test
    fun `all generated packets are registered by metadata`() {
        val data = GeneratedProtocol.protocolData()
        val packetsByState = ProtocolState.entries.associateWith { state ->
            GeneratedPacketRegistry.packetsById(state).values.flatMap { it.values }
        }
        val allPackets = packetsByState.values.flatten()

        assertTrue(allPackets.isNotEmpty(), "Expected generated packet registry to contain packets")

        ProtocolState.entries.forEach { state ->
            val packetKeys = mutableSetOf<Pair<Int, Any>>()

            packetsByState.getValue(state).forEach { packetClass ->
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

    @Test
    fun `generated protocol data carries generated protocol resource path`() {
        val data = GeneratedProtocol.protocolData()

        assertEquals(GeneratedPacketRegistry.protocolResourcePath, data.protocolResourcePath)
    }
}
