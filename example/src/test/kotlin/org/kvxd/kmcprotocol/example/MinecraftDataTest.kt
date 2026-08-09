package org.kvxd.kmcprotocol.example

import org.kvxd.kmcprotocol.core.ProtocolVersion
import org.kvxd.kmcprotocol.data.BoundingBox
import org.kvxd.kmcprotocol.data.StatePropertyType
import org.kvxd.kmcprotocol.generated.Protocols
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MinecraftDataTest {

    @Test
    fun `every generated version exposes block, item and entity data`() {
        Protocols.versions.forEach { version ->
            val data = version.requireMinecraftData()
            assertEquals(version.minecraftVersion, data.minecraftVersion)
            assertTrue(data.blocks.isNotEmpty(), "${version.minecraftVersion} has no blocks")
            assertTrue(data.items.isNotEmpty(), "${version.minecraftVersion} has no items")
            assertTrue(data.entities.isNotEmpty(), "${version.minecraftVersion} has no entities")
        }
    }

    @Test
    fun `block state ids round trip through property values`() {
        Protocols.versions.filter { it.requireMinecraftData().supportsBlockStates }.forEach { version ->
            val data = version.requireMinecraftData()
            data.blocks.forEach { block ->
                for (stateId in block.minStateId..block.maxStateId) {
                    assertEquals(
                        stateId,
                        block.stateOf(block.propertiesOf(stateId)),
                        "${version.minecraftVersion} ${block.name} state $stateId did not round trip",
                    )
                }
            }
        }
    }

    @Test
    fun `state property strides span exactly the block state range`() {
        Protocols.versions.filter { it.requireMinecraftData().supportsBlockStates }.forEach { version ->
            version.requireMinecraftData().blocks.forEach { block ->
                val combinations = block.states.fold(1) { total, state -> total * state.numValues }
                assertEquals(
                    combinations,
                    block.stateCount,
                    "${version.minecraftVersion} ${block.name} state count does not match its properties",
                )
                assertTrue(
                    block.defaultState in block.minStateId..block.maxStateId,
                    "${version.minecraftVersion} ${block.name} default state is out of range",
                )
            }
        }
    }

    @Test
    fun `every block state id resolves back to its block`() {
        Protocols.versions.filter { it.requireMinecraftData().supportsBlockStates }.forEach { version ->
            val data = version.requireMinecraftData()
            for (stateId in 0 until data.blockStateCount) {
                val block = assertNotNull(
                    data.blockByStateId(stateId),
                    "${version.minecraftVersion} state $stateId has no block",
                )
                assertTrue(stateId in block.minStateId..block.maxStateId)
            }
        }
    }

    @Test
    fun `pre flattening versions report no block states`() {
        val legacy = Protocols.byMinecraftVersion("1.8")?.requireMinecraftData()
        assertNotNull(legacy)
        assertFalse(legacy.supportsBlockStates)
        assertEquals(0, legacy.blockStateCount)
        assertNull(legacy.blockByStateId(0))
        assertEquals(198, legacy.blocks.size)
    }

    @Test
    fun `1_20_4 block data matches minecraft-data`() {
        val data = version("1.20.4").requireMinecraftData()

        assertEquals(1058, data.blocks.size)
        assertEquals(1312, data.items.size)
        assertEquals(126, data.entities.size)
        assertEquals(26644, data.blockStateCount)

        val button = data.requireBlock("acacia_button")
        assertEquals(389, button.id)
        assertEquals("Acacia Button", button.displayName)
        assertEquals(8707, button.minStateId)
        assertEquals(8730, button.maxStateId)
        assertEquals(8716, button.defaultState)
        assertEquals(BoundingBox.Empty, button.boundingBox)
        assertEquals(listOf("face", "facing", "powered"), button.states.map { it.name })
        assertEquals(listOf(8, 2, 1), button.states.map { it.stride })
        assertEquals(listOf("true", "false"), button.requireProperty("powered").values)
        assertEquals(StatePropertyType.Bool, button.requireProperty("powered").type)
        assertEquals(listOf("floor", "wall", "ceiling"), button.requireProperty("face").values)

        val stone = data.requireBlock("minecraft:stone")
        assertEquals(1, stone.defaultState)
        assertEquals(BoundingBox.Block, stone.boundingBox)
        assertEquals("mineable/pickaxe", stone.material)
        assertEquals(1.5, stone.hardness)
        assertEquals(listOf(35), stone.drops.map { it.itemId })
        assertTrue(816 in stone.harvestTools)

        assertEquals(535, data.requireBlock("sandstone").defaultState)
        assertEquals(2978, data.requireBlock("redstone_wire").minStateId)
        assertEquals(5881, data.requireBlock("repeater").minStateId)
        assertEquals(9175, data.requireBlock("comparator").minStateId)
    }

    @Test
    fun `1_20_4 solid blocks cover the full vanilla set`() {
        val data = version("1.20.4").requireMinecraftData()
        val solid = data.blocks.count { it.boundingBox == BoundingBox.Block }

        assertEquals(833, solid)
        assertEquals(225, data.blocks.count { it.boundingBox == BoundingBox.Empty })
        listOf("dirt", "obsidian", "oak_planks", "smooth_stone", "stone").forEach { name ->
            assertEquals(BoundingBox.Block, data.requireBlock(name).boundingBox, "$name should be solid")
        }
        assertEquals(BoundingBox.Empty, data.requireBlock("redstone_wire").boundingBox)
    }

    @Test
    fun `1_20_4 item and entity data matches minecraft-data`() {
        val data = version("1.20.4").requireMinecraftData()

        val sandstone = data.requireItem("sandstone")
        assertEquals(64, sandstone.stackSize)
        assertEquals(data.requireBlock("sandstone").defaultState, data.requireBlock(sandstone.name).defaultState)

        val elytra = data.requireItem("elytra")
        assertEquals(432, elytra.maxDurability)
        assertEquals(listOf("phantom_membrane"), elytra.repairWith)
        assertEquals(1, elytra.stackSize)

        val allay = data.requireEntity("allay")
        assertEquals("mob", allay.type)
        assertEquals("Passive mobs", allay.category)
        assertEquals(0.35, allay.width)
        assertEquals(0.6, allay.height)
        assertTrue(allay.metadataKeys.isNotEmpty())
    }

    @Test
    fun `block state parsing resolves properties`() {
        val data = version("1.20.4").requireMinecraftData()
        val repeater = data.requireBlock("repeater")

        val parsed = assertNotNull(data.blockState("minecraft:repeater[delay=3,facing=east]"))
        assertEquals("3", repeater.value(parsed, repeater.requireProperty("delay")))
        assertEquals("east", repeater.value(parsed, repeater.requireProperty("facing")))
        assertEquals(repeater.defaultState, data.blockState("repeater"))
        assertEquals(repeater.describe(parsed), data.requireBlockByStateId(parsed).describe(parsed))
    }

    @Test
    fun `legacy drops keep their metadata`() {
        val data = version("1.8").requireMinecraftData()
        val drops = data.blocks.flatMap { it.drops }

        assertTrue(drops.any { it.metadata != null }, "expected 1.8 drops to carry metadata")
        assertTrue(drops.any { it.minCount != null }, "expected 1.8 drops to carry counts")
        assertTrue(data.blocks.any { it.variations.isNotEmpty() }, "expected 1.8 blocks to carry variations")
    }

    private fun version(minecraftVersion: String): ProtocolVersion =
        Protocols.requireMinecraftVersion(minecraftVersion)
}
