package de.kvxd.kmcprotocol.network.client

import com.kvxd.eventbus.EventBus
import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.IOException

class Client(
    private val address: SocketAddress = InetSocketAddress("localhost", 25565),
    val protocol: MinecraftProtocol
) : EventBus() {

    private val selectorManager = ActorSelectorManager(Dispatchers.IO)

    private lateinit var socket: Socket
    private lateinit var writeChannel: ByteWriteChannel
    private lateinit var readChannel: ByteReadChannel

    private val connectedDeferred = CompletableDeferred<Unit>()

    init {
        onError = { e -> e.printStackTrace() }
    }

    suspend fun connect() = withContext(Dispatchers.IO) {
        socket = aSocket(selectorManager)
            .tcp()
            .connect(address)

        writeChannel = socket.openWriteChannel(autoFlush = false)
        readChannel = socket.openReadChannel()

        connectedDeferred.complete(Unit)
        post(CConnected())

        while (!socket.isClosed) {
            try {
                val packet = protocol.packetFormat.receive(readChannel, protocol, Direction.CLIENTBOUND)

                if (readChannel.isClosedForRead && packet == null) {
                    post(CDisconnected(CDisconnected.Reason.ByServer))
                    socket.close()
                    break
                }

                packet?.let {
                    post(CPacketReceived(it))
                }
            } catch (e: Exception) {
                if (e is EOFException || e is IOException) {
                    // Server closed the connection
                    post(CDisconnected(CDisconnected.Reason.ByServer))
                    println("Closed")
                    socket.close()
                } else {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun awaitConnected() {
        connectedDeferred.await()
    }

    suspend fun send(packet: MinecraftPacket) {
        protocol.packetFormat.send(packet, writeChannel, protocol)
    }

    fun disconnect() = runBlocking(Dispatchers.IO) {
        if (socket.isClosed) return@runBlocking

        post(CDisconnected(CDisconnected.Reason.ByClient))

        socket.close()
    }
}