package de.kvxd.kmcprotocol.network.server

import com.kvxd.eventbus.EventBus
import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.defaultProtocol
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.nio.channels.ClosedChannelException

class Server(
    private val port: Int = 25565
) : EventBus() {

    private lateinit var socket: ServerSocket
    private val selectorManager = SelectorManager()

    private val bindDeferred = CompletableDeferred<Unit>()

    private val sessions = mutableSetOf<Session>()

    init {
        onError = { e -> e.printStackTrace() }
    }

    suspend fun bind() {
        socket = aSocket(selectorManager)
            .tcp()
            .bind(port = port)

        bindDeferred.complete(Unit)
        post(SrvServerBound())

        while (!socket.isClosed) {
            try {
                val sessionSocket = socket.accept()

                val session = Session(sessionSocket, defaultProtocol())
                sessions.add(session)

                post(SrvSessionConnected(session))

            } catch (e: Exception) {
                if (socket.isClosed) break
                e.printStackTrace()
            }
        }
    }

    suspend fun awaitBound() {
        bindDeferred.await()
    }

    fun close() = runBlocking(Dispatchers.IO) {
        sessions.forEach { it.disconnect() }
        sessions.clear()

        socket.close()
        socket.awaitClosed()

        selectorManager.close()
    }

    inner class Session(
        private val socket: Socket,
        val protocol: MinecraftProtocol
    ) : CoroutineScope by CoroutineScope(Dispatchers.IO + SupervisorJob()), EventBus() {

        private val writeChannel = socket.openWriteChannel()
        private val readChannel = socket.openReadChannel()

        val remoteAddress: SocketAddress = socket.remoteAddress

        init {
            onError = { e -> e.printStackTrace() }

            launch {
                while (!socket.isClosed) {
                    val packet = protocol.packetFormat.receive(readChannel, protocol, Direction.SERVERBOUND)

                    if (readChannel.isClosedForRead && packet == null) {
                        disconnect()
                    }

                    packet?.let {
                        post(SPacketReceived(packet))
                    }
                }
            }
        }

        suspend fun send(packet: MinecraftPacket) {
            protocol.packetFormat.send(packet, writeChannel, protocol)
        }

        fun disconnect() = runBlocking {
            if (socket.isClosed) return@runBlocking

            writeChannel.flushAndClose()
            readChannel.cancel()

            socket.close()

            post(SConnectionClosed())
        }
    }

}