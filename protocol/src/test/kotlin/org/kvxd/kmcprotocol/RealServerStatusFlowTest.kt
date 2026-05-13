package org.kvxd.kmcprotocol

import org.kvxd.kmcprotocol.core.MinecraftPacket
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.network.client.Client
import org.kvxd.kmcprotocol.network.conn.ConnectionCallback
import org.kvxd.kmcprotocol.packets.generated.handshake.serverbound.ServerboundSetProtocolPacket
import org.kvxd.kmcprotocol.packets.generated.status.clientbound.ClientboundPingPacket
import org.kvxd.kmcprotocol.packets.generated.status.clientbound.ClientboundServerInfoPacket
import org.kvxd.kmcprotocol.packets.generated.status.serverbound.ServerboundPingPacket
import org.kvxd.kmcprotocol.packets.generated.status.serverbound.ServerboundPingStartPacket
import io.ktor.network.sockets.InetSocketAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class RealServerStatusFlowTest {

    @Test
    fun `status packets round trip against a real server`() = runBlocking {
        if (!realServerEnabled()) return@runBlocking

        withRealServerEndpoint { host, port ->
            val serverInfo = CompletableDeferred<ClientboundServerInfoPacket>()
            val pong = CompletableDeferred<ClientboundPingPacket>()
            val clientError = CompletableDeferred<Throwable>()

            val client = Client(InetSocketAddress(host, port))
            client.addCallback(object : ConnectionCallback() {
                override fun onPacketReceived(packet: MinecraftPacket) {
                    when (packet) {
                        is ClientboundServerInfoPacket -> serverInfo.complete(packet)
                        is ClientboundPingPacket -> pong.complete(packet)
                    }
                }

                override fun onError(error: Throwable) {
                    clientError.complete(error)
                }
            })

            try {
                client.send(ServerboundSetProtocolPacket(protocolVersion(), host, port, 1))
                client.data.state = ProtocolState.Status

                client.send(ServerboundPingStartPacket)
                val info = withTimeout(10_000.milliseconds) {
                    select {
                        serverInfo.onAwait { it }
                        clientError.onAwait { throw it }
                    }
                }
                assertTrue(info.response.contains("version"), "Expected a Minecraft status JSON response")

                val payload = System.currentTimeMillis()
                client.send(ServerboundPingPacket(payload))
                val ping = withTimeout(10_000.milliseconds) {
                    select {
                        pong.onAwait { it }
                        clientError.onAwait { throw it }
                    }
                }
                assertEquals(payload, ping.time)
            } finally {
                client.disconnect()
            }
        }
    }

    private suspend fun withRealServerEndpoint(block: suspend (String, Int) -> Unit) {
        val configuredHost = setting("kmc.realServer.host")
        val configuredPort = setting("kmc.realServer.port")?.toInt()
        if (configuredHost != null && configuredPort != null) {
            block(configuredHost, configuredPort)
            return
        }

        val jar = setting("kmc.realServer.jar")?.let(::File)
        if (jar == null || !jar.isFile) {
            throw IllegalArgumentException(
                "Enable real server tests with kmc.realServer.host/kmc.realServer.port or kmc.realServer.jar"
            )
        }

        val port = freePort()
        val serverDir = Files.createTempDirectory("kmc-real-server").toFile()
        serverDir.resolve("eula.txt").writeText("eula=true")
        serverDir.resolve("server.properties").writeText(
            """
            online-mode=false
            server-ip=127.0.0.1
            server-port=$port
            enable-query=false
            enable-rcon=false
            max-players=1
            motd=KMCProtocol test server
            """.trimIndent()
        )

        val ready = CountDownLatch(1)
        val process = ProcessBuilder(
            "java",
            "-Xms512M",
            "-Xmx1024M",
            "-jar",
            jar.absolutePath,
            "nogui",
        )
            .directory(serverDir)
            .redirectErrorStream(true)
            .start()
        val outputReader = Thread {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach {
                    println("[real-server] $it")
                    if (it.contains("]: Done (")) {
                        ready.countDown()
                    }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        try {
            waitForServerReady("127.0.0.1", port, process, ready)
            block("127.0.0.1", port)
        } finally {
            runCatching {
                process.outputStream.write("stop\n".toByteArray())
                process.outputStream.flush()
            }
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
            outputReader.join(1_000)
        }
    }

    private fun realServerEnabled(): Boolean =
        setting("kmc.realServer.enabled")?.toBooleanStrictOrNull() == true

    private fun protocolVersion(): Int =
        setting("kmc.realServer.protocolVersion")?.toInt() ?: 769

    private fun setting(name: String): String? =
        System.getProperty(name)?.takeIf(String::isNotBlank)
            ?: System.getenv(name.uppercase().replace('.', '_'))?.takeIf(String::isNotBlank)

    private fun freePort(): Int =
        java.net.ServerSocket(0).use { it.localPort }

    private fun waitForServerReady(host: String, port: Int, process: Process, ready: CountDownLatch) {
        val deadline = System.nanoTime() + 90_000_000_000
        var portReady = false

        while (System.nanoTime() < deadline) {
            check(process.isAlive) { "Real server process exited before opening $host:$port" }

            if (!portReady) {
                portReady = runCatching { Socket(host, port).use { true } }.getOrDefault(false)
            }

            if (portReady && ready.await(250, TimeUnit.MILLISECONDS)) return
        }

        error("Timed out waiting for real server readiness on $host:$port")
    }
}
