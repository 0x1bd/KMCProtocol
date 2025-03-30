package de.kvxd.kmcprotocol

import com.kvxd.eventbus.handler
import de.kvxd.kmcprotocol.network.PacketReceived
import de.kvxd.kmcprotocol.network.client.Client
import de.kvxd.kmcprotocol.network.server.Server
import de.kvxd.kmcprotocol.network.server.SessionConnected
import de.kvxd.kmcprotocol.packet.format.Encrypted
import de.kvxd.kmcprotocol.packets.handshake.serverbound.NextState
import de.kvxd.kmcprotocol.packets.handshake.serverbound.ServerboundHandshakePacket
import de.kvxd.kmcprotocol.packets.login.clientbound.ClientboundEncryptionRequestPacket
import de.kvxd.kmcprotocol.packets.login.serverbound.ServerboundEncryptionResponsePacket
import de.kvxd.kmcprotocol.packets.status.clientbound.ClientboundPongResponsePacket
import de.kvxd.kmcprotocol.packets.status.clientbound.ClientboundStatusResponsePacket
import de.kvxd.kmcprotocol.packets.status.clientbound.StatusResponse
import de.kvxd.kmcprotocol.packets.status.serverbound.ServerboundPingRequestPacket
import de.kvxd.kmcprotocol.packets.status.serverbound.ServerboundStatusRequestPacket
import io.ktor.network.sockets.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class NetworkingTest {

    @Test
    fun `test vanilla ping flow`() = runTest(timeout = 5.seconds) {
        val server = Server { defaultProtocol() }
        val serverJob = launch(Dispatchers.IO) { server.bind() }

        try {
            val statusReceived = CompletableDeferred<StatusResponse>()
            val pongReceived = CompletableDeferred<Long>()

            server.bus.handler<SessionConnected> { event ->
                event.session.bus.handler<PacketReceived> { packetEvent ->
                    when (val packet = packetEvent.packet) {
                        is ServerboundHandshakePacket -> {
                            event.session.state(ProtocolState.STATUS)
                        }

                        is ServerboundStatusRequestPacket -> {
                            event.session.send(ClientboundStatusResponsePacket(StatusResponse.VANILLA))
                        }

                        is ServerboundPingRequestPacket -> {
                            event.session.send(ClientboundPongResponsePacket(packet.timestamp))
                            event.session.disconnect()
                        }
                    }
                }
            }

            val client = Client(InetSocketAddress("localhost", 25565))

            try {
                client.bus.handler<PacketReceived> { event ->
                    when (val packet = event.packet) {
                        is ClientboundStatusResponsePacket -> {
                            statusReceived.complete(packet.response)
                        }

                        is ClientboundPongResponsePacket -> {
                            pongReceived.complete(packet.timestamp)
                        }
                    }
                }

                client.send(
                    ServerboundHandshakePacket(
                        protocolVersion = 769,
                        serverAddress = "localhost",
                        serverPort = 25565,
                        nextState = NextState.Status
                    )
                )
                client.state(ProtocolState.STATUS)
                client.send(ServerboundStatusRequestPacket())
                client.send(ServerboundPingRequestPacket(69L))

                val status = statusReceived.await()
                val pongTimestamp = pongReceived.await()

                assertNotNull(status, "Status response not received")
                assertNotNull(pongTimestamp, "Pong timestamp not received")
                assertEquals(status, StatusResponse.VANILLA, "Status response not matching original")
                assertEquals(69L, pongTimestamp, "Incorrect pong timestamp")
            } finally {
                client.disconnect()
            }
        } finally {
            serverJob.cancelAndJoin()
            server.close()
        }
    }

    @Test
    fun `test simple encryption`() = runTest(timeout = 5.seconds) {
        val server = Server { defaultProtocol() }
        val serverJob = launch(Dispatchers.IO) { server.bind() }

        val key = Encrypted.generateKey()

        try {
            val encryptionComplete = CompletableDeferred<Unit>()

            server.bus.handler<SessionConnected> { event ->

                event.session.bus.handler<PacketReceived> { packetEvent ->
                    when (val packet = packetEvent.packet) {
                        is ServerboundHandshakePacket -> {
                            event.session.state(ProtocolState.LOGIN)

                            event.session.send(
                                ClientboundEncryptionRequestPacket(
                                    publicKey = key.public.encoded.toTypedArray(),
                                    verifyToken = Encrypted.generateVerifyToken(),
                                )
                            )
                        }

                        is ServerboundEncryptionResponsePacket -> {
                            event.session.enableEncryption(Encrypted.decryptSecret(key, packet.sharedSecret))

                            encryptionComplete.complete(Unit)
                        }
                    }
                }
            }

            val client = Client(InetSocketAddress("localhost", 25565))

            try {
                client.bus.handler<PacketReceived> { event ->
                    when (val packet = event.packet) {
                        is ClientboundEncryptionRequestPacket -> {
                            val secret = Encrypted.generateSecret()

                            val (encryptedSecret, encryptedToken) = Encrypted.decryptCombo(
                                secret,
                                packet.verifyToken,
                                packet.publicKey
                            )

                            client.send(
                                ServerboundEncryptionResponsePacket(
                                    sharedSecret = encryptedSecret,
                                    verifyToken = encryptedToken
                                )
                            )

                            client.enableEncryption(secret)
                        }
                    }
                }

                client.send(
                    ServerboundHandshakePacket(
                        protocolVersion = 769,
                        serverAddress = "localhost",
                        serverPort = 25565,
                        nextState = NextState.Login
                    )
                )
                client.state(ProtocolState.LOGIN)

                encryptionComplete.await()
                println("Encryption handshake completed successfully")
            } finally {
                client.disconnect()
            }
        } finally {
            serverJob.cancelAndJoin()
            server.close()
        }
    }

}