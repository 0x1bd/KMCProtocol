package org.kvxd.kmcprotocol.conformance

import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object FabricServerFixture {
    private const val Host = "127.0.0.1"

    fun enabled(): Boolean =
        setting("kmc.fabricServer.enabled")?.toBooleanStrictOrNull() == true

    fun protocolVersion(): Int =
        setting("kmc.protocolVersion")?.toInt() ?: 769

    suspend fun withServer(block: suspend (String, Int) -> Unit) {
        val projectDir = File(setting("kmc.projectDir") ?: ".").canonicalFile
        val port = setting("kmc.fabricServer.port")?.toInt() ?: freePort()
        val runDir = projectDir.resolve("build/fabric-conformance-server-$port")
        val loomRunDir = runDir.relativeTo(projectDir.resolve("fabric-testmod")).invariantSeparatorsPath
        val ready = CountDownLatch(1)

        prepareRunDirectory(runDir, port)

        val process = withContext(Dispatchers.IO) {
            ProcessBuilder(
                gradleWrapper(projectDir).absolutePath,
                "--no-daemon",
                ":fabric-testmod:runServer",
                "-PkmcFabricServerPort=$port",
                "-PkmcFabricServerRunDir=$loomRunDir",
            )
                .directory(projectDir)
                .redirectErrorStream(true)
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
            waitForServerReady(Host, port, process, ready)
            block(Host, port)
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

    private fun prepareRunDirectory(runDir: File, port: Int) {
        check(runDir.mkdirs() || runDir.isDirectory) {
            "Could not create Fabric server run directory: ${runDir.absolutePath}"
        }

        runDir.resolve("eula.txt").writeText("eula=true\n")
        runDir.resolve("server.properties").writeText(
            """
            online-mode=false
            server-ip=$Host
            server-port=$port
            enable-query=false
            enable-rcon=false
            network-compression-threshold=1
            max-players=4
            motd=KMCProtocol Fabric conformance server
            """.trimIndent() + "\n"
        )
    }

    private fun gradleWrapper(projectDir: File): File =
        projectDir.resolve(if (System.getProperty("os.name").startsWith("Windows")) "gradlew.bat" else "gradlew")

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
