package org.kvxd.kmcprotocol.build

internal fun String.pascalCase(): String = sanitizeIdentifier()
    .split('_')
    .filter(String::isNotEmpty)
    .joinToString("") { part -> part.replaceFirstChar(Char::uppercaseChar) }
    .ifEmpty { "Unnamed" }
    .let { if (it.first().isDigit()) "N$it" else it }

internal fun String.camelCase(): String = pascalCase().replaceFirstChar(Char::lowercaseChar)

private fun String.sanitizeIdentifier(): String =
    trim().lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")

internal class NameAllocator {
    private val used = HashSet<String>()

    fun allocate(preferred: String): String {
        if (used.add(preferred)) return preferred
        var index = 2
        while (!used.add("$preferred$index")) index++
        return "$preferred$index"
    }
}
