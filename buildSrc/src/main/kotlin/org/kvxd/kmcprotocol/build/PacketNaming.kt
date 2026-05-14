package org.kvxd.kmcprotocol.build

internal class PacketNaming {
    private val reserved = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
        "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
        "true", "try", "typealias", "typeof", "val", "var", "when", "while",
    )

    fun packetClassName(direction: DirectionSource, packetTypeName: String): String =
        "${direction.classPrefix}${toPascalCase(packetTypeName)}Packet"

    fun generatedTypeName(hint: String): String =
        toPascalCase(hint).ifEmpty { "GeneratedProtocolType" }

    fun toPascalCase(value: String): String =
        value.removePrefix("packet_")
            .split(Regex("[^A-Za-z0-9]+|_"))
            .filter(String::isNotEmpty)
            .joinToString("") { word -> word.replaceFirstChar { it.uppercase() } }

    fun toPropertyName(value: String): String {
        val cleaned = value
            .replace(Regex("[^A-Za-z0-9_]"), "_")
            .replace(Regex("^_+$"), "field")
            .replace(Regex("^([0-9])"), "field_$1")
        val identifier = cleaned.ifEmpty { "field" }
        return if (identifier in reserved) "${identifier}Value" else identifier
    }

    fun uniqueName(base: String, used: MutableSet<String>): String {
        if (used.add(base)) return base

        var index = 2
        while (!used.add("$base$index")) index += 1
        return "$base$index"
    }
}
