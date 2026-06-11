package de.davidsw.diawars.util

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.net.URI
import java.util.UUID

object MenuUtils {
    fun item(
        material: Material,
        name: Component = Component.text(""),
        lore: List<Component> = emptyList(),
        glow: Boolean = false,
    ): ItemStack {
        val stack = ItemStack(material)
        val meta  = stack.itemMeta!!

        meta.displayName(name)
        meta.lore(lore)
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        if (material == Material.GRAY_STAINED_GLASS_PANE) {
            meta.isHideTooltip = true
        }

        if (glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true)
        }

        stack.itemMeta = meta
        return stack
    }

    fun skullFromUrl(textureUrl: String, name: Component = Component.text(""), lore: List<Component> = emptyList()): ItemStack {
        val stack = ItemStack(Material.PLAYER_HEAD)
        val meta  = stack.itemMeta as SkullMeta
        val profile = Bukkit.createProfile(UUID.randomUUID())

        profile.textures.skin = URI.create(textureUrl).toURL()

        meta.playerProfile = profile
        meta.displayName(name)
        meta.lore(lore)
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)

        stack.itemMeta = meta
        return stack
    }
}