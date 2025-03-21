package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.codecs.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IdentifierTest {

    @Test
    fun `test Identifier creation with namespace and path`() {
        val identifier = Identifier.of("customnamespace", "custompath")
        assertEquals("customnamespace:custompath", identifier.toString())
    }

    @Test
    fun `test Identifier creation with default namespace`() {
        val identifier = Identifier.of("custompath")
        assertEquals("minecraft:custompath", identifier.toString())
    }

    @Test
    fun `test Identifier creation from string`() {
        val identifier = Identifier.fromString("customnamespace:custompath")
        assertEquals("customnamespace:custompath", identifier.toString())
    }

    @Test
    fun `test Identifier creation from string without namespace`() {
        val identifier = Identifier.fromString("custompath")
        assertEquals("minecraft:custompath", identifier.toString())
    }

    @Test
    fun `test Identifier with too long string`() {
        val longPath = "a".repeat(32767 - "customnamespace:".length + 1)
        assertFailsWith<IllegalArgumentException> {
            Identifier.of("customnamespace", longPath)
        }
    }

    @Test
    fun `test Identifier without namespace separator`() {
        val identifier = Identifier.of("custompath")
        assertEquals("minecraft:custompath", identifier.toString())
    }

}