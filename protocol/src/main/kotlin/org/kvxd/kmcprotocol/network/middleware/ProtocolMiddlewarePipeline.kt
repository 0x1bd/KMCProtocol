package org.kvxd.kmcprotocol.network.middleware

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.kvxd.kmcprotocol.core.KmcProtocolException

internal class ProtocolMiddlewarePipeline(
    middlewares: List<ProtocolMiddleware>,
    private val context: ProtocolMiddlewareContext,
    coroutineScope: CoroutineScope,
    private val emittedPacketHandler: suspend (RawProtocolPacket) -> Unit = {},
    private val failureHandler: (Throwable) -> Unit = {},
) : AutoCloseable {

    private enum class Flow {
        Inbound,
        Outbound,
    }

    private sealed interface Request {
        data class Transform(
            val packet: RawProtocolPacket,
            val flow: Flow,
            val completion: CompletableDeferred<List<RawProtocolPacket>>,
        ) : Request

        data object WakeEmissions : Request
    }

    private data class Emission(
        val packet: RawProtocolPacket,
        val flow: Flow,
        val startIndex: Int,
    )

    private val requests = Channel<Request>(Channel.UNLIMITED)
    private val emissions = ConcurrentLinkedQueue<Emission>()
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val failure = AtomicReference<Throwable?>(null)
    private val sessionsClosed = AtomicBoolean(false)

    private val sessions: List<ProtocolMiddlewareSession> = buildList(middlewares.size) {
        try {
            middlewares.forEachIndexed { index, middleware ->
                add(middleware.createSession(context.withEmitter(StageEmitter(index))))
            }
        } catch (cause: Throwable) {
            asReversed().forEach { session -> runCatching { session.close() } }
            closed.set(true)
            requests.close(cause)
            throw cause
        }
    }

    private val workerJob: Job = coroutineScope.launch(start = CoroutineStart.LAZY) {
        workerLoop()
    }.also { job ->
        job.invokeOnCompletion { cause ->
            if (cause != null && !closed.get()) fail(cause)
            failPendingRequests(failure.get() ?: pipelineClosedException())
        }
    }

    fun start() {
        if (closed.get()) return
        if (started.compareAndSet(false, true)) workerJob.start()
        if (emissions.isNotEmpty()) requests.trySend(Request.WakeEmissions)
    }

    suspend fun outbound(packet: RawProtocolPacket): List<RawProtocolPacket> =
        transformRoot(packet, Flow.Outbound)

    suspend fun inbound(packet: RawProtocolPacket): List<RawProtocolPacket> =
        transformRoot(packet, Flow.Inbound)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        requests.close()
        workerJob.cancel()
        closeSessions()
        failPendingRequests(pipelineClosedException())
        emissions.clear()
    }

    private suspend fun transformRoot(
        packet: RawProtocolPacket,
        flow: Flow,
    ): List<RawProtocolPacket> {
        validateDirection(packet, flow)
        failure.get()?.let { throw it }
        if (closed.get()) throw pipelineClosedException()

        start()
        val completion = CompletableDeferred<List<RawProtocolPacket>>()
        try {
            requests.send(Request.Transform(packet, flow, completion))
        } catch (cause: Throwable) {
            failure.get()?.let { throw it }
            if (closed.get()) throw pipelineClosedException()
            throw cause
        }
        return completion.await()
    }

    private suspend fun workerLoop() {
        for (request in requests) {
            try {
                when (request) {
                    is Request.Transform -> {
                        val startIndex = when (request.flow) {
                            Flow.Outbound -> 0
                            Flow.Inbound -> sessions.lastIndex
                        }
                        val transformed = transformFrom(request.packet, request.flow, startIndex)
                        drainEmissions()
                        request.completion.complete(transformed)
                    }

                    Request.WakeEmissions -> drainEmissions()
                }
            } catch (cause: Throwable) {
                if (request is Request.Transform) request.completion.completeExceptionally(cause)
                fail(cause)
                return
            }
        }
    }

    private suspend fun drainEmissions() {
        while (true) {
            val emission = emissions.poll() ?: return
            val transformed = transformFrom(emission.packet, emission.flow, emission.startIndex)
            transformed.forEach { emittedPacketHandler(it) }
        }
    }

    private suspend fun transformFrom(
        packet: RawProtocolPacket,
        flow: Flow,
        startIndex: Int,
    ): List<RawProtocolPacket> {
        var packets = listOf(packet)
        var index = startIndex
        val step = if (flow == Flow.Outbound) 1 else -1

        while (index in sessions.indices) {
            if (packets.isEmpty()) return emptyList()
            val stage = sessions[index]

            packets = buildList {
                for (current in packets) {
                    val transformedPackets = when (flow) {
                        Flow.Inbound -> stage.inbound(current)
                        Flow.Outbound -> stage.outbound(current)
                    }
                    for (transformed in transformedPackets) {
                        validateTransformedMetadata(current, transformed)
                        add(transformed)
                    }
                }
            }
            index += step
        }

        return packets
    }

    private fun enqueueEmission(
        packet: RawProtocolPacket,
        flow: Flow,
        stageIndex: Int,
    ): Boolean {
        validateDirection(packet, flow)
        if (closed.get()) return false

        val startIndex = when (flow) {
            Flow.Inbound -> stageIndex - 1
            Flow.Outbound -> stageIndex + 1
        }
        val emission = Emission(packet, flow, startIndex)
        emissions.add(emission)

        if (closed.get()) {
            emissions.remove(emission)
            return false
        }

        if (started.get()) requests.trySend(Request.WakeEmissions)
        return true
    }

    private fun validateDirection(packet: RawProtocolPacket, flow: Flow) {
        val expected = when (flow) {
            Flow.Inbound -> context.inboundDirection
            Flow.Outbound -> context.outboundDirection
        }
        if (packet.direction != expected) {
            throw KmcProtocolException(
                "Protocol middleware attempted to emit/transform a ${packet.direction} packet as $flow; " +
                        "this connection expects $expected for that flow.",
            )
        }
    }

    private fun validateTransformedMetadata(
        current: RawProtocolPacket,
        transformed: RawProtocolPacket,
    ) {
        if (transformed.state != current.state || transformed.direction != current.direction) {
            throw KmcProtocolException(
                "Protocol middleware changed canonical packet metadata from " +
                        "${current.state}/${current.direction} to " +
                        "${transformed.state}/${transformed.direction}. " +
                        "Middlewares may transform packet bytes, but KMCP owns state and direction.",
            )
        }
    }

    private fun fail(cause: Throwable) {
        failure.compareAndSet(null, cause)
        if (closed.compareAndSet(false, true)) {
            requests.close(cause)
            emissions.clear()
            closeSessions()
            failPendingRequests(cause)
            runCatching { failureHandler(cause) }
        }
    }

    private fun closeSessions() {
        if (!sessionsClosed.compareAndSet(false, true)) return
        sessions.asReversed().forEach { session -> runCatching { session.close() } }
    }

    private fun failPendingRequests(cause: Throwable) {
        while (true) {
            val request = requests.tryReceive().getOrNull() ?: return
            if (request is Request.Transform) request.completion.completeExceptionally(cause)
        }
    }

    private inner class StageEmitter(
        private val stageIndex: Int,
    ) : ProtocolMiddlewareEmitter {
        override suspend fun emitInbound(packet: RawProtocolPacket) {
            if (!enqueueEmission(packet, Flow.Inbound, stageIndex)) throw pipelineClosedException()
        }

        override suspend fun emitOutbound(packet: RawProtocolPacket) {
            if (!enqueueEmission(packet, Flow.Outbound, stageIndex)) throw pipelineClosedException()
        }

        override fun scheduleInbound(packet: RawProtocolPacket): Boolean =
            enqueueEmission(packet, Flow.Inbound, stageIndex)

        override fun scheduleOutbound(packet: RawProtocolPacket): Boolean =
            enqueueEmission(packet, Flow.Outbound, stageIndex)
    }

    private fun pipelineClosedException(): KmcProtocolException =
        KmcProtocolException("Protocol middleware pipeline is closed.")
}