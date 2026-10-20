package de.davidsw.diawars.managers

import org.bukkit.entity.Player
import org.bukkit.map.MapCanvas
import org.bukkit.map.MapRenderer
import org.bukkit.map.MapView

/**
 * Paints a report screenshot attachment onto a map (admin review handout
 * via {@code /admin report shot <id>}).
 *
 * Like [ImageMapRenderer] it paints exactly once per server session: the
 * pixels persist in the map data afterwards. Report shot maps are deliberately
 * NOT tracked in ImageMapStore — they are not player images, so they must
 * never surface in image browsing, adoption or the vanilla restore path.
 */
class ReportShotRenderer(
    private val manager: ReportManager,
    private val reportId: String,
) : MapRenderer() {
    @Volatile
    private var done = false

    override fun render(view: MapView, canvas: MapCanvas, player: Player) {
        if (done) return
        done = true
        val image = manager.loadShotImage(reportId) ?: return
        canvas.drawImage(0, 0, image)
    }
}
