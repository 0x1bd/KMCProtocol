package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.core.ProtocolState
import de.kvxd.kmcprotocol.core.encoding.EncodingScope.Companion.encoding
import de.kvxd.kmcprotocol.network.Direction
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EncodingTest {

    @Test
    fun `test encoding and decoding of test packet`() = runTest {
        val packet = generateTestPacket()

        val channel = ByteChannel()

        encoding(channel) {
            protocolData.redefineRegistry {
                register(TestPacket::class, ProtocolState.Handshake)
            }

            encode(packet)

            channel.close()

            assertEquals(packet, decodeDirect(Direction.Serverbound))
        }
    }

}