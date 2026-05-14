package org.kvxd.kmcprotocol.example

import io.ktor.network.sockets.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.kvxd.kmcprotocol.network.client.Client
import org.kvxd.kmcprotocol.packets.generated.handshake.serverbound.ServerboundSetProtocolPacket
import org.kvxd.kmcprotocol.packets.generated.status.clientbound.ClientboundPingPacket
import org.kvxd.kmcprotocol.packets.generated.status.clientbound.ClientboundServerInfoPacket
import org.kvxd.kmcprotocol.packets.generated.status.serverbound.ServerboundPingPacket
import org.kvxd.kmcprotocol.packets.generated.status.serverbound.ServerboundPingStartPacket
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds

private const val ProtocolVersion = 769

fun main(args: Array<String>) = runBlocking {
    val host = args.getOrNull(0) ?: "127.0.0.1"
    val port = args.getOrNull(1)?.toIntOrNull() ?: 25565

    val client = Client(InetSocketAddress(host, port))

    try {
        client.send(ServerboundSetProtocolPacket(ProtocolVersion, host, port, nextState = 1))
        client.send(ServerboundPingStartPacket)

        val info = withTimeout(10_000.milliseconds) {
            client.receive() as ClientboundServerInfoPacket
        }
        println(info.response)

        val started = System.currentTimeMillis()
        client.send(ServerboundPingPacket(started))
        val pong = withTimeout(10_000.milliseconds) {
            client.receive() as ClientboundPingPacket
        }
        println("Ping payload echoed: ${pong.time}")
    } catch (error: Throwable) {
        System.err.println("Status ping failed: ${error.message}")
        exitProcess(1)
    } finally {
        client.close()
    }
}
