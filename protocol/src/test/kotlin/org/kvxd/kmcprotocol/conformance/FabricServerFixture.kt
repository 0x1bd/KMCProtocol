package org.kvxd.kmcprotocol.conformance

import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object FabricServerFixture {
    fun enabled(): Boolean =
        setting("kmc.fabricServer.enabled")?.toBooleanStrictOrNull() == true

    fun protocolVersion(): Int =
        setting("kmc.protocolVersion")?.toInt() ?: 769

    suspend fun withServer(block: suspend (String, Int) -> Unit) {
        val projectDir = File(setting("kmc.projectDir") ?: ".").canonicalFile
        val port = setting("kmc.fabricServer.port")?.toInt() ?: freePort()
        val ready = CountDownLatch(1)

        val process = withContext(Dispatchers.IO) {
            ProcessBuilder(projectDir.resolve("scripts/test_fabric_server.sh").absolutePath)
                .directory(projectDir)
                .redirectErrorStream(true)
                .apply {
                    environment()["KMC_FABRIC_SERVER_PORT"] = port.toString()
                    environment()["KMC_FABRIC_SERVER_DIR"] = "build/fabric-conformance-server-$port"
                }
                .start()
        }

        val outputReader = Thread {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    println("[fabric-server] $line")
                    if (line.contains("Done (") && line.contains("For help")) ready.countDown()
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
            if (!withContext(Dispatchers.IO) { process.waitFor(20, TimeUnit.SECONDS) }) {
                process.destroyForcibly()
            }
            withContext(Dispatchers.IO) {
                outputReader.join(1_000)
            }
        }
    }

    private fun setting(name: String): String? =
        System.getProperty(name)?.takeIf(String::isNotBlank)
            ?: System.getenv(name.uppercase().replace('.', '_'))?.takeIf(String::isNotBlank)

    private fun freePort(): Int =
        ServerSocket(0).use { it.localPort }

    private fun waitForServerReady(host: String, port: Int, process: Process, ready: CountDownLatch) {
        val deadline = System.nanoTime() + 120_000_000_000
        var portReady = false

        while (System.nanoTime() < deadline) {
            check(process.isAlive) { "Fabric server process exited before opening $host:$port" }

            if (!portReady) {
                portReady = runCatching { Socket(host, port).use { true } }.getOrDefault(false)
            }

            if (portReady && ready.await(250, TimeUnit.MILLISECONDS)) return
        }

        error("Timed out waiting for Fabric server readiness on $host:$port")
    }
}
