package de.davidsw.diawars.util

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.profile.PlayerProfile
import org.bukkit.profile.PlayerTextures
import java.net.URL
import java.util.UUID

object MenuUtils {
    fun item(
        material: Material,
        name: String,
        lore: List<String>,
        glow: Boolean = false,
    ): ItemStack {
        val stack = ItemStack(material)
        val meta  = stack.itemMeta!!

        meta.setDisplayName(name)
        meta.lore = lore.map { ChatColor.translateAlternateColorCodes('§', it) }
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

    fun skullFromUrl(textureUrl: String, name: String, lore: List<String>): ItemStack {
        val stack = ItemStack(Material.PLAYER_HEAD)
        val meta  = stack.itemMeta as SkullMeta

        val profile: PlayerProfile = Bukkit.createPlayerProfile(UUID.randomUUID())
        val textures: PlayerTextures = profile.textures
        textures.skin = URL(textureUrl)
        profile.setTextures(textures)

        meta.ownerProfile = profile
        meta.setDisplayName(name)
        meta.lore = lore
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)

        stack.itemMeta = meta
        return stack
    }
}