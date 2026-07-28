package org.kvxd.kmcprotocol

import org.kvxd.kmcprotocol.core.PacketMetadata
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.generated.v1_21_4.GeneratedPacketRegistry
import org.kvxd.kmcprotocol.generated.Protocols
import kotlin.reflect.full.findAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeneratedPacketRegistryTest {

    @Test
    fun `all generated packets are registered by metadata`() = Protocols.versions.forEach { version ->
        val data = version.protocolData()
        val packetsByState = ProtocolState.entries.associateWith { state ->
            version.registrySource.packetsById(state).values.flatMap { it.values }
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
    fun `catalog exposes every generated version and resolves them by name and protocol number`() {
        assertTrue(Protocols.versions.isNotEmpty(), "Expected the generated catalog to list versions")

        Protocols.versions.forEach { version ->
            assertEquals(version, Protocols.requireMinecraftVersion(version.minecraftVersion))
            assertEquals(
                version.protocolVersion,
                Protocols.requireProtocolVersion(version.protocolVersion).protocolVersion,
            )
            assertTrue(
                version in Protocols.allByProtocolVersion(version.protocolVersion),
                "${version.minecraftVersion} missing from allByProtocolVersion",
            )
            assertEquals("pc/${version.minecraftVersion}/protocol.json", version.protocolResourcePath)
        }
    }

    @Test
    fun `each generated version resolves its own protocol type table`() {
        Protocols.versions.forEach { version ->
            assertTrue(
                version.codec.types.aliasCount > 0,
                "Expected ${version.minecraftVersion} to load protocol.json type aliases",
            )
        }
    }

    @Test
    fun `generated registry matches its catalog entry`() {
        assertEquals(GeneratedPacketRegistry.minecraftVersion, Protocols.V1_21_4.minecraftVersion)
        assertEquals(GeneratedPacketRegistry.protocolVersion, Protocols.V1_21_4.protocolVersion)
    }
}
