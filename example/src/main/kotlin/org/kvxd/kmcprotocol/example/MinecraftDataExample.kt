package org.kvxd.kmcprotocol.example

import org.kvxd.kmcprotocol.data.BoundingBox
import org.kvxd.kmcprotocol.data.MinecraftData
import org.kvxd.kmcprotocol.data.generated.v1_20_4.BlockStates
import org.kvxd.kmcprotocol.data.generated.v1_20_4.Blocks
import org.kvxd.kmcprotocol.data.generated.v1_20_4.Entities
import org.kvxd.kmcprotocol.data.generated.v1_20_4.Facing
import org.kvxd.kmcprotocol.data.generated.v1_20_4.Half
import org.kvxd.kmcprotocol.data.generated.v1_20_4.Instrument
import org.kvxd.kmcprotocol.data.generated.v1_20_4.Items
import org.kvxd.kmcprotocol.data.generated.v1_20_4.Shape
import org.kvxd.kmcprotocol.generated.Protocols

private val version = Protocols.requireMinecraftVersion("1.20.4")

fun main() {
    val data = version.requireMinecraftData()

    printSummary(data)
    printTypedStates()
    printTypedLookups(data)
    printItemsAndEntities(data)
}

private fun printSummary(data: MinecraftData) {
    println("Minecraft ${data.minecraftVersion} (protocol ${version.protocolVersion})")
    println("  ${data.blocks.size} blocks, ${data.items.size} items, ${data.entities.size} entities")

    val solid = data.blocks.count { it.boundingBox == BoundingBox.Block }
    println("  ${data.blockStateCount} block states, $solid solid, ${data.blocks.size - solid} non-solid")
}

private fun printTypedStates() {
    println()
    println("Typed state builders:")

    val repeater = BlockStates.repeater(delay = 3, facing = Facing.East, powered = true)
    println("  repeater(delay = 3, facing = Facing.East, powered = true) = $repeater")
    println("    -> ${Blocks.Repeater.describe(repeater)}")
    println("    facing back out: ${BlockStates.repeaterFacing(repeater)}, delay ${BlockStates.repeaterDelay(repeater)}")

    val stairs = BlockStates.oakStairs(facing = Facing.West, half = Half.Top, shape = Shape.InnerLeft)
    println("  oakStairs(facing = Facing.West, half = Half.Top, shape = Shape.InnerLeft) = $stairs")
    println("    -> ${Blocks.OakStairs.describe(stairs)}")

    val note = BlockStates.noteBlock(instrument = Instrument.Bell, note = 12, powered = false)
    println("  noteBlock(instrument = Instrument.Bell, note = 12) = $note")
    println("    -> ${Blocks.NoteBlock.describe(note)}")

    println("  defaults reproduce the vanilla default state: " +
        "${BlockStates.repeater() == Blocks.Repeater.defaultState}")
}

private fun printTypedLookups(data: MinecraftData) {
    println()
    println("Blocks:")
    println("  ${Blocks.Glass.name}: boundingBox=${Blocks.Glass.boundingBox}, transparent=${Blocks.Glass.transparent}")
    println("  ${Blocks.RedstoneWire.name}: states ${Blocks.RedstoneWire.minStateId}..${Blocks.RedstoneWire.maxStateId}")
    println("  ${Blocks.Obsidian.name}: hardness=${Blocks.Obsidian.hardness}, solid=${Blocks.Obsidian.boundingBox == BoundingBox.Block}")

    val owner = data.requireBlockByStateId(BlockStates.repeater(delay = 2))
    println("  state ${BlockStates.repeater(delay = 2)} belongs to ${owner.name}")

    val emitters = data.blocks.filter { it.emitLight > 0 }.sortedByDescending { it.emitLight }
    println("  ${emitters.size} light emitting blocks, brightest: " + emitters.take(3).joinToString {
        "${it.name} (${it.emitLight})"
    })
}

private fun printItemsAndEntities(data: MinecraftData) {
    println()
    println("Items:")
    println("  ${Items.Elytra.name}: id=${Items.Elytra.id}, stackSize=${Items.Elytra.stackSize}, durability=${Items.Elytra.maxDurability}")
    println("    repaired with ${Items.Elytra.repairWith.joinToString()}")
    println("  ${data.items.count { it.stackSize == 1 }} of ${data.items.size} items do not stack")

    println()
    println("Entities:")
    println("  ${Entities.Allay.name}: type=${Entities.Allay.type}, category=${Entities.Allay.category}")
    println("    hitbox ${Entities.Allay.width} x ${Entities.Allay.height}, ${Entities.Allay.metadataKeys.size} metadata keys")
    println("  ${Entities.Creeper.name}: hitbox ${Entities.Creeper.width} x ${Entities.Creeper.height}")

    data.entities
        .groupBy { it.type }
        .entries
        .sortedByDescending { it.value.size }
        .take(3)
        .forEach { (type, entities) -> println("  $type: ${entities.size}") }
}
