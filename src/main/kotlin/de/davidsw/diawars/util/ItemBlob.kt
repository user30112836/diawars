package de.davidsw.diawars.util

import org.bukkit.inventory.ItemStack
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Compact binary form for ItemStack columns, so inventories survive in a BLOB
 * instead of YAML text. Slot lists may contain nulls ([List] of `ItemStack?`),
 * which is why every slot carries its own presence flag.
 *
 * Like every other Bukkit serialization this must run on the main thread.
 */
object ItemBlob {
    private const val VERSION = 1

    fun encodeItem(item: ItemStack?): ByteArray? {
        if (item == null) return null
        return ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeByte(VERSION)
                out.writeItem(item)
            }
        }.toByteArray()
    }

    fun decodeItem(bytes: ByteArray?): ItemStack? {
        if (bytes == null || bytes.isEmpty()) return null
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            input.readByte() // version, reserved for future changes
            return input.readItem()
        }
    }

    fun encodeItems(items: List<ItemStack?>): ByteArray =
        ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeByte(VERSION)
                out.writeInt(items.size)
                items.forEach { out.writeItem(it) }
            }
        }.toByteArray()

    fun decodeItems(bytes: ByteArray?): List<ItemStack?> {
        if (bytes == null || bytes.isEmpty()) return emptyList()
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            input.readByte()
            val size = input.readInt()
            return ArrayList<ItemStack?>(size).also { items ->
                repeat(size) { items.add(input.readItem()) }
            }
        }
    }

    private fun DataOutputStream.writeItem(item: ItemStack?) {
        if (item == null) {
            writeBoolean(false)
            return
        }
        val payload = item.serializeAsBytes()
        writeBoolean(true)
        writeInt(payload.size)
        write(payload)
    }

    private fun DataInputStream.readItem(): ItemStack? {
        if (!readBoolean()) return null
        val payload = ByteArray(readInt())
        readFully(payload)
        return ItemStack.deserializeBytes(payload)
    }
}
