package de.kvxd.kmcprotocol.network.client

import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.defaultProtocol
import de.kvxd.kmcprotocol.network.Connection
import de.kvxd.kmcprotocol.packet.Direction
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

class Client(
    address: SocketAddress,
    protocol: MinecraftProtocol = defaultProtocol()
) : Connection(
    protocol,
    Direction.CLIENTBOUND,
    createSocket(address)
)

private fun createSocket(address: SocketAddress): Socket = runBlocking {
    val selectorManager = SelectorManager(Dispatchers.IO + SupervisorJob())
    aSocket(selectorManager).tcp().connect(address)
}