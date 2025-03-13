package de.kvxd.kmcprotocol.network

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TCPClient(private val host: String, private val port: Int) {

    private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    private var socket: Socket? = null
    private var output: ByteWriteChannel? = null

    suspend fun connect() {
        socket = aSocket(selectorManager).tcp().connect(host, port)
        output = socket?.openWriteChannel(autoFlush = true)
    }

    suspend fun sendPacket(packet: ByteArray) {
        withContext(Dispatchers.IO) {
            output?.writeFully(packet)
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        socket?.close()
        selectorManager.close()
    }
}