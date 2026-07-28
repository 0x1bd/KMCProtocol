package org.kvxd.kmcprotocol.example

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.kvxd.kmcprotocol.generated.Protocols
import org.kvxd.kmcprotocol.network.client.Client
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.handshake.serverbound.ServerboundSetProtocolPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.status.clientbound.ClientboundPingPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.status.clientbound.ClientboundServerInfoPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.status.serverbound.ServerboundPingPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.status.serverbound.ServerboundPingStartPacket
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

private const val ProtocolVersion = 769

fun main(args: Array<String>) = runBlocking {
    val host = args.getOrNull(0) ?: "127.0.0.1"
    val port = args.getOrNull(1)?.toIntOrNull() ?: 25565

    val client = Client(host, port, Protocols.V1_21_4.protocolData())

    try {
        client.send(ServerboundSetProtocolPacket(ProtocolVersion, host, port, nextState = 1))
        client.send(ServerboundPingStartPacket)

        val info = withTimeout(10.seconds) {
            client.receive() as ClientboundServerInfoPacket
        }
        println(info.response)

        val payload = System.currentTimeMillis()
        val started = System.currentTimeMillis()
        client.send(ServerboundPingPacket(payload))
        val pong = withTimeout(10.seconds) {
            client.receive() as ClientboundPingPacket
        }
        println("Ping payload echoed: ${pong.time}")
        println("Latency: ${System.currentTimeMillis() - started}ms")
    } catch (error: Throwable) {
        System.err.println("Status ping failed: ${error.message}")
        exitProcess(1)
    } finally {
        client.close()
    }
}
