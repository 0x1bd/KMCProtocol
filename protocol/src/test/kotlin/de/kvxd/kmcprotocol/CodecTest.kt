package de.kvxd.kmcprotocol

import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class CodecTest {

    private val packet = TestPacket.generateTestPacket()

    @Test
    fun `test example packet codec`() = runBlocking {
        val channel = ByteChannel()

        TestPacket.CODEC.encode(packet, channel, true)

        val decoded = TestPacket.CODEC.decode(channel)

        assertEquals(packet, decoded)
    }

    @Test
    fun `test example packet codec with registry`() = runBlocking {
        val protocol = MinecraftProtocol {
            registerPacket(TestPacket::class, TestPacket.CODEC)
        }

        val channel = ByteChannel()

        protocol.registry.getPacketDataById(0).first.encode(packet, channel, true)

        val decoded = protocol.registry.getPacketDataById(0).first.decode(channel)

        assertEquals(packet, decoded)
    }

}