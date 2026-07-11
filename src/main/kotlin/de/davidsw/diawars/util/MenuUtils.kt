package de.davidsw.diawars.util

import com.destroystokyo.paper.profile.ProfileProperty
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
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

    private val NAV_HEAD_UUID = UUID.fromString("6c39cbf9-8256-4c5c-8fdb-976deb2ca0da")

    fun skullFromValue(value: String, name: Component = Component.text(""), lore: List<Component> = emptyList()): ItemStack {
        val stack = ItemStack(Material.PLAYER_HEAD)
        val meta  = stack.itemMeta as SkullMeta
        val profile = Bukkit.createProfile(NAV_HEAD_UUID)

        profile.setProperty(ProfileProperty("textures", value))

        meta.playerProfile = profile
        meta.displayName(name)
        meta.lore(lore)
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)

        stack.itemMeta = meta
        return stack
    }
}