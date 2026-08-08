package org.kvxd.kmcprotocol.core.encoding

import net.lenni0451.mcstructs.nbt.io.NbtIO

enum class NbtFormat(internal val io: NbtIO) {
    V1_0_0(NbtIO.V1_0_0),
    V1_2_1(NbtIO.V1_2_1),
    V1_12(NbtIO.V1_12);

    companion object {
        val Latest: NbtFormat = V1_12

        fun forMinecraftVersion(minecraftVersion: String): NbtFormat {
            val components = versionComponents(minecraftVersion) ?: return Latest
            return when {
                components.atLeast(1, 12) -> V1_12
                components.atLeast(1, 2, 1) -> V1_2_1
                else -> V1_0_0
            }
        }

        private fun versionComponents(minecraftVersion: String): List<Int>? {
            val components = minecraftVersion
                .split('.')
                .map { component -> component.takeWhile(Char::isDigit).toIntOrNull() ?: return null }
            return components.ifEmpty { null }
        }

        private fun List<Int>.atLeast(vararg minimum: Int): Boolean {
            for (index in minimum.indices) {
                val component = getOrNull(index) ?: 0
                if (component != minimum[index]) return component > minimum[index]
            }
            return true
        }
    }
}
