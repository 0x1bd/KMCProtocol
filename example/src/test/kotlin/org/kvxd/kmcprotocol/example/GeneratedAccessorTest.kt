package org.kvxd.kmcprotocol.example

import org.kvxd.kmcprotocol.data.BlockData
import org.kvxd.kmcprotocol.data.MinecraftData
import org.kvxd.kmcprotocol.generated.Protocols
import org.kvxd.kmcprotocol.data.generated.v1_20_4.BlockStates
import org.kvxd.kmcprotocol.data.generated.v1_20_4.Blocks
import org.kvxd.kmcprotocol.data.generated.v1_20_4.Entities
import org.kvxd.kmcprotocol.data.generated.v1_20_4.Facing
import org.kvxd.kmcprotocol.data.generated.v1_20_4.Items
import org.kvxd.kmcprotocol.data.generated.v1_20_4.Mode
import org.kvxd.kmcprotocol.data.generated.v1_20_4.Type3
import kotlin.test.Test
import kotlin.test.assertEquals

class GeneratedAccessorTest {

    private val data: MinecraftData = Protocols.requireMinecraftVersion("1.20.4").requireMinecraftData()

    @Test
    fun `named constants resolve to the right entries`() {
        assertEquals("stone", Blocks.Stone.name)
        assertEquals("redstone_wire", Blocks.RedstoneWire.name)
        assertEquals("acacia_button", Blocks.AcaciaButton.name)
        assertEquals(data.requireBlock("repeater").defaultState, Blocks.Repeater.defaultState)

        assertEquals("elytra", Items.Elytra.name)
        assertEquals(432, Items.Elytra.maxDurability)
        assertEquals("allay", Entities.Allay.name)
    }

    @Test
    fun `typed builders agree with untyped state construction`() {
        assertEquals(
            Blocks.Repeater.stateOf(
                mapOf("delay" to "3", "facing" to "east", "locked" to "false", "powered" to "true")
            ),
            BlockStates.repeater(delay = 3, facing = Facing.East, locked = false, powered = true),
        )
        assertEquals(
            Blocks.Comparator.stateOf(mapOf("facing" to "north", "mode" to "subtract", "powered" to "false")),
            BlockStates.comparator(facing = Facing.North, mode = Mode.Subtract, powered = false),
        )
        assertEquals(
            Blocks.OakSlab.stateOf(mapOf("type" to "double", "waterlogged" to "false")),
            BlockStates.oakSlab(type = Type3.Double, waterlogged = false),
        )
    }

    @Test
    fun `builder defaults reproduce the default block state`() {
        assertEquals(Blocks.Repeater.defaultState, BlockStates.repeater())
        assertEquals(Blocks.RedstoneWire.defaultState, BlockStates.redstoneWire())
        assertEquals(Blocks.OakStairs.defaultState, BlockStates.oakStairs())
        assertEquals(Blocks.NoteBlock.defaultState, BlockStates.noteBlock())
        assertEquals(Blocks.Hopper.defaultState, BlockStates.hopper())
        assertEquals(Blocks.Chest.defaultState, BlockStates.chest())
        assertEquals(Blocks.AndesiteWall.defaultState, BlockStates.andesiteWall())
        assertEquals(Blocks.StructureBlock.defaultState, BlockStates.structureBlock())
        assertEquals(Blocks.NetherPortal.defaultState, BlockStates.netherPortal())
        assertEquals(Blocks.AcaciaDoor.defaultState, BlockStates.acaciaDoor())
        assertEquals(Blocks.Rail.defaultState, BlockStates.rail())
        assertEquals(Blocks.AcaciaLog.defaultState, BlockStates.acaciaLog())
    }

    @Test
    fun `typed readers agree with untyped property values on every state`() {
        readerChecks().forEach { (block, readers) ->
            for (stateId in block.minStateId..block.maxStateId) {
                readers.forEach { (propertyName, reader) ->
                    val property = block.requireProperty(propertyName)
                    assertEquals(
                        block.valueIndex(stateId, property),
                        reader(stateId),
                        "${block.name}.$propertyName disagreed on state $stateId",
                    )
                }
            }
        }
    }

    private fun readerChecks(): List<Pair<BlockData, List<Pair<String, (Int) -> Int>>>> = listOf(
        Blocks.Repeater to listOf(
            "delay" to { id: Int -> BlockStates.repeaterDelay(id) - 1 },
            "facing" to { id: Int -> BlockStates.repeaterFacing(id).ordinal },
            "locked" to { id: Int -> BlockStates.repeaterLocked(id).index() },
            "powered" to { id: Int -> BlockStates.repeaterPowered(id).index() },
        ),
        Blocks.RedstoneWire to listOf(
            "east" to { id: Int -> BlockStates.redstoneWireEast(id).ordinal },
            "north" to { id: Int -> BlockStates.redstoneWireNorth(id).ordinal },
            "power" to { id: Int -> BlockStates.redstoneWirePower(id) },
            "south" to { id: Int -> BlockStates.redstoneWireSouth(id).ordinal },
            "west" to { id: Int -> BlockStates.redstoneWireWest(id).ordinal },
        ),
        Blocks.OakStairs to listOf(
            "facing" to { id: Int -> BlockStates.oakStairsFacing(id).ordinal },
            "half" to { id: Int -> BlockStates.oakStairsHalf(id).ordinal },
            "shape" to { id: Int -> BlockStates.oakStairsShape(id).ordinal },
            "waterlogged" to { id: Int -> BlockStates.oakStairsWaterlogged(id).index() },
        ),
        Blocks.NoteBlock to listOf(
            "instrument" to { id: Int -> BlockStates.noteBlockInstrument(id).ordinal },
            "note" to { id: Int -> BlockStates.noteBlockNote(id) },
            "powered" to { id: Int -> BlockStates.noteBlockPowered(id).index() },
        ),
        Blocks.Hopper to listOf(
            "enabled" to { id: Int -> BlockStates.hopperEnabled(id).index() },
            "facing" to { id: Int -> BlockStates.hopperFacing(id).ordinal },
        ),
        Blocks.Chest to listOf(
            "facing" to { id: Int -> BlockStates.chestFacing(id).ordinal },
            "type" to { id: Int -> BlockStates.chestType(id).ordinal },
            "waterlogged" to { id: Int -> BlockStates.chestWaterlogged(id).index() },
        ),
        Blocks.AndesiteWall to listOf(
            "east" to { id: Int -> BlockStates.andesiteWallEast(id).ordinal },
            "up" to { id: Int -> BlockStates.andesiteWallUp(id).index() },
            "west" to { id: Int -> BlockStates.andesiteWallWest(id).ordinal },
        ),
        Blocks.StructureBlock to listOf(
            "mode" to { id: Int -> BlockStates.structureBlockMode(id).ordinal },
        ),
        Blocks.NetherPortal to listOf(
            "axis" to { id: Int -> BlockStates.netherPortalAxis(id).ordinal },
        ),
        Blocks.AcaciaDoor to listOf(
            "half" to { id: Int -> BlockStates.acaciaDoorHalf(id).ordinal },
            "hinge" to { id: Int -> BlockStates.acaciaDoorHinge(id).ordinal },
            "open" to { id: Int -> BlockStates.acaciaDoorOpen(id).index() },
        ),
        Blocks.Rail to listOf(
            "shape" to { id: Int -> BlockStates.railShape(id).ordinal },
            "waterlogged" to { id: Int -> BlockStates.railWaterlogged(id).index() },
        ),
        Blocks.AcaciaLog to listOf(
            "axis" to { id: Int -> BlockStates.acaciaLogAxis(id).ordinal },
        ),
        Blocks.PointedDripstone to listOf(
            "thickness" to { id: Int -> BlockStates.pointedDripstoneThickness(id).ordinal },
            "vertical_direction" to { id: Int -> BlockStates.pointedDripstoneVerticalDirection(id).ordinal },
        ),
    )

    private fun Boolean.index(): Int = if (this) 0 else 1
}
