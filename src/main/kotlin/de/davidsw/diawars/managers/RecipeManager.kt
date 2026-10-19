package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.tr7zw.nbtapi.NBT
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapedRecipe

/**
 * Custom crafting recipes.
 *
 * <p>Invisible item frames: the classic map-maker trick as a craft - the
 * result frame carries an {@code entity_data} component with
 * {@code Invisible=1b} (built via NBTAPI's component access, which handles
 * the modern data-component system), so the placed frame entity is invisible
 * without any right-click tricks. Registered as 8 glass panes around a
 * (glow) item frame, one normal and one glow variant.</p>
 *
 * <p>Breaks behave like vanilla: the dropped frame item is a plain frame
 * needing a new craft - the invisibility does not dupe.</p>
 */
class RecipeManager(private val plugin: Diawars) : Listener {

    private val invisibleFrameKey = NamespacedKey(plugin, "invisible_item_frame")
    private val invisibleGlowFrameKey = NamespacedKey(plugin, "invisible_glow_item_frame")

    fun registerRecipes() {
        registerPaneRecipe(invisibleFrameKey, Material.ITEM_FRAME)
        registerPaneRecipe(invisibleGlowFrameKey, Material.GLOW_ITEM_FRAME)
    }

    /** Registers "8 glass panes around [frame]" yielding an invisible [frame]. */
    private fun registerPaneRecipe(key: NamespacedKey, frame: Material) {
        val recipe = ShapedRecipe(key, invisible(frame))
        recipe.shape("ppp", "pfp", "ppp")
        recipe.setIngredient('p', RecipeChoice.MaterialChoice(Material.GLASS_PANE))
        recipe.setIngredient('f', RecipeChoice.MaterialChoice(frame))
        Bukkit.addRecipe(recipe)
    }

    /** A frame item whose placed entity is invisible. */
    private fun invisible(frame: Material): ItemStack {
        val stack = ItemStack(frame)
        NBT.modifyComponents(stack) { nbt ->
            val entityData = nbt.getCompound("minecraft:entity_data")
                ?: error("NBTAPI could not create the entity_data component")
            entityData.setString("id", frame.key.toString())
            entityData.setByte("Invisible", 1)
        }
        return stack
    }

    /** Unlocks the matching recipe as soon as the player holds such a frame. */
    @EventHandler
    fun onHeldItemChange(event: PlayerItemHeldEvent) {
        val player: Player = event.player
        val stack = player.inventory.getItem(event.newSlot) ?: return
        when (stack.type) {
            Material.ITEM_FRAME -> player.discoverRecipe(invisibleFrameKey)
            Material.GLOW_ITEM_FRAME -> player.discoverRecipe(invisibleGlowFrameKey)
            else -> return
        }
    }
}
