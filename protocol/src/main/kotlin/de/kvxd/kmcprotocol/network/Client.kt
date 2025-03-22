package de.kvxd.kmcprotocol.network

import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.codec.codecs.VarIntCodec
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.registry.PacketRegistry
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.io.readByteArray

class Client(
    private val address: SocketAddress = InetSocketAddress("localhost", 25565),
    val protocol: MinecraftProtocol,
    val registry: PacketRegistry
) {

    private val selectorManager = ActorSelectorManager(Dispatchers.IO)

    private lateinit var socket: Socket
    private lateinit var writeChannel: ByteWriteChannel

    suspend fun connect() {
        socket = aSocket(selectorManager)
            .tcp()
            .connect(address)

        writeChannel = socket.openWriteChannel(autoFlush = false)
    }

    suspend fun send(packet: MinecraftPacket) {
        val (packetId, codec) = registry.getPacketData(packet)

        val content = ByteChannel(autoFlush = false).apply {
            VarIntCodec.encode(this, packetId)
            codec.encode(packet, this)
            flush()
            close()
        }

        val contentBytes = content.readRemaining().readByteArray()

        VarIntCodec.encode(writeChannel, contentBytes.size)
        writeChannel.writeFully(contentBytes)
        writeChannel.flush()
    }

    fun disconnect() {
        socket.close()
    }

}