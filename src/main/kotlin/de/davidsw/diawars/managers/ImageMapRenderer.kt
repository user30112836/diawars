package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import org.bukkit.entity.Player
import org.bukkit.map.MapCanvas
import org.bukkit.map.MapRenderer
import org.bukkit.map.MapView
import java.util.UUID

/**
 * Paints a stored player image onto a map.
 *
 * Custom renderers are NOT persisted by the server: after a restart every
 * loaded MapView only carries the vanilla terrain renderer again, which would
 * repaint the map as a normal map on first view. The plugin therefore records
 * every issued map in [de.davidsw.diawars.stores.ImageMapStore] and re-attaches
 * this renderer on [org.bukkit.event.server.MapInitializeEvent] and startup.
 *
 * Paints exactly once per server session: the pixels persist in the map data
 * afterwards, and repainting identical pixels every view would only spam disk
 * writes. Render may run off the main thread (Paper renders maps async), so
 * the flag is volatile and loading stays on files only.
 */
class ImageMapRenderer(
    private val plugin: Diawars,
    val owner: UUID,
    val file: String,
    /** True for maps whose image was deleted: paints the placeholder instead. */
    private val placeholder: Boolean = false,
) : MapRenderer() {
    @Volatile
    private var done = false

    override fun render(view: MapView, canvas: MapCanvas, player: Player) {
        if (done) return
        done = true
        if (placeholder) {
            canvas.drawImage(0, 0, plugin.imageManager.placeholderImage())
            return
        }
        val image = plugin.imageManager.loadImage(owner, file) ?: return
        canvas.drawImage(0, 0, image)
    }
}
