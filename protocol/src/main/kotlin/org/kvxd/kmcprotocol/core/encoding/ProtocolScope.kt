package org.kvxd.kmcprotocol.core.encoding

internal class ProtocolScope private constructor(
    private val frames: List<MutableMap<String, Any?>>,
) {

    val current: MutableMap<String, Any?>
        get() = frames[frames.lastIndex]

    fun child(): ProtocolScope = ProtocolScope(frames + mutableMapOf())

    fun put(name: String, value: Any?) {
        current[name] = value
    }

    fun putAll(values: Map<String, Any?>) {
        current.putAll(values)
    }

    fun resolve(path: String): Any? {
        var remaining = path
        var up = 0
        while (remaining.startsWith("../")) {
            up += 1
            remaining = remaining.removePrefix("../")
        }
        remaining = remaining.removePrefix("./")

        val parts = remaining.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null

        val head = parts[0]
        var index = frames.lastIndex - up
        while (index >= 0) {
            val frame = frames[index]
            if (frame.containsKey(head)) {
                var value: Any? = frame[head].unwrapDynamicValue()
                for (partIndex in 1 until parts.size) {
                    val container = value as? Map<*, *> ?: return null
                    value = container[parts[partIndex]].unwrapDynamicValue()
                }
                return value
            }
            index -= 1
        }
        return null
    }

    companion object {
        fun root(values: MutableMap<String, Any?> = mutableMapOf()): ProtocolScope = ProtocolScope(listOf(values))
    }
}
