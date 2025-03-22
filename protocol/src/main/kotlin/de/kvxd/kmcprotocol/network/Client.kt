package de.kvxd.kmcprotocol.network

import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.codec.codecs.VarIntCodec
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.io.readByteArray

class Client(
    private val address: SocketAddress = InetSocketAddress("localhost", 25565),
    val protocol: MinecraftProtocol
) {

    private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var socket: Socket
    private lateinit var writeChannel: ByteWriteChannel
    private lateinit var readChannel: ByteReadChannel

    suspend fun connect() {
        socket = aSocket(selectorManager)
            .tcp()
            .connect(address)

        writeChannel = socket.openWriteChannel(autoFlush = false)
        readChannel = socket.openReadChannel()

        scope.launch {
            while (true) {
                VarIntCodec.decodeOrNull(readChannel)?.let { len ->
                    VarIntCodec.decodeOrNull(readChannel)?.let { id ->
                        println("Len: $len")
                        println("id: $id")

                        val (codec, metadata) = protocol.registry.getPacketDataById(id)

                        val packet = codec.decode(readChannel)

                        println(packet)
                    }
                }
            }
        }
    }

    suspend fun send(packet: MinecraftPacket) {
        val (packetId, codec) = protocol.registry.getPacketData(packet)

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
        scope.cancel() // Cancel the coroutine scope
        socket.close()
        selectorManager.close()
    }
}