package de.davidsw.diawars

import de.davidsw.diawars.commands.EventCommand
import de.davidsw.diawars.commands.MenuCommand
import de.davidsw.diawars.commands.PvPCommand
import de.davidsw.diawars.commands.ScoresCommand
import de.davidsw.diawars.commands.SelfKillCommand
import de.davidsw.diawars.commands.TeamZonesCommand
import de.davidsw.diawars.listeners.ContainerExplosionListener
import de.davidsw.diawars.listeners.DiamondLimitListener
import de.davidsw.diawars.listeners.EventListener
import de.davidsw.diawars.listeners.MenuListener
import de.davidsw.diawars.listeners.PlayerEventListener
import de.davidsw.diawars.listeners.PvPListener
import de.davidsw.diawars.listeners.WorldProtectionListener
import de.davidsw.diawars.managers.BorderManager
import de.davidsw.diawars.managers.ContainerExplosionManager
import de.davidsw.diawars.managers.DiamondLimitManager
import de.davidsw.diawars.managers.DiamondScoreboardManager
import de.davidsw.diawars.managers.EventManager
import de.davidsw.diawars.managers.MenuManager
import de.davidsw.diawars.managers.MessageManager
import de.davidsw.diawars.stores.PlayerDiamondStore
import de.davidsw.diawars.managers.PvPManager
import de.davidsw.diawars.managers.ScoresManager
import de.davidsw.diawars.managers.TeamManager
import de.davidsw.diawars.managers.ZoneManager
import de.davidsw.diawars.menu.BorderMenu
import de.davidsw.diawars.menu.MainMenu
import de.davidsw.diawars.stores.BorderPreferencesStore
import de.davidsw.diawars.stores.EventStore
import de.davidsw.diawars.stores.PlayerStateStore
import de.davidsw.diawars.stores.PvPStatusStore
import org.bukkit.Bukkit.getWorlds
import org.bukkit.GameRule
import org.bukkit.plugin.java.JavaPlugin

data class Menu(
    var mainMenu: MainMenu,
    var borderMenu: BorderMenu,
)

data class Store(
    var playerDiamondStore: PlayerDiamondStore,
    var borderPreferencesStore: BorderPreferencesStore,
    var pvpStatusStore: PvPStatusStore,
    var eventStore: EventStore,
    var playerStateStore: PlayerStateStore,
)

class Diawars : JavaPlugin() {

    lateinit var messageManager: MessageManager
    lateinit var teamManager: TeamManager
    lateinit var zoneManager: ZoneManager
    lateinit var borderManager: BorderManager
    lateinit var pvpManager: PvPManager
    lateinit var diamondLimitManager: DiamondLimitManager
    lateinit var containerExplosionManager: ContainerExplosionManager
    lateinit var diamondScoreboardManager: DiamondScoreboardManager
    lateinit var menuManager: MenuManager
    lateinit var scoresManager: ScoresManager
    lateinit var eventManager: EventManager

    lateinit var store: Store
    lateinit var menu: Menu

    override fun onEnable() {
        saveDefaultConfig()

        store = Store(
            playerDiamondStore = PlayerDiamondStore(this),
            borderPreferencesStore = BorderPreferencesStore(this),
            pvpStatusStore = PvPStatusStore(this),
            eventStore = EventStore(this),
            playerStateStore = PlayerStateStore(this),
        )

        messageManager = MessageManager(this)
        teamManager = TeamManager(this)
        zoneManager = ZoneManager(this)
        borderManager = BorderManager(this)
        pvpManager = PvPManager(this)
        diamondLimitManager = DiamondLimitManager(this)
        containerExplosionManager = ContainerExplosionManager(this)
        diamondScoreboardManager = DiamondScoreboardManager(this)
        menuManager = MenuManager(this)
        scoresManager = ScoresManager(this)
        eventManager = EventManager(this)

        menu = Menu(
            mainMenu = MainMenu(this),
            borderMenu = BorderMenu(this),
        )

        diamondLimitManager.startTrackingTask()
        diamondLimitManager.trackExistingDiamonds()
        diamondScoreboardManager.start()
        pvpManager.reactivateTasks()

        server.pluginManager.registerEvents(PlayerEventListener(this), this)
        server.pluginManager.registerEvents(PvPListener(this), this)
        server.pluginManager.registerEvents(DiamondLimitListener(this), this)
        server.pluginManager.registerEvents(ContainerExplosionListener(this), this)
        server.pluginManager.registerEvents(WorldProtectionListener(this), this)
        server.pluginManager.registerEvents(MenuListener(this), this)
        server.pluginManager.registerEvents(EventListener(this), this)

        getCommand("teamzones")?.setExecutor(TeamZonesCommand(this))
        getCommand("pvp")?.setExecutor(PvPCommand(this))
        getCommand("scores")?.setExecutor(ScoresCommand(this))
        getCommand("menu")?.setExecutor(MenuCommand(this))
        getCommand("selfkill")?.setExecutor(SelfKillCommand(this))
        getCommand("event")?.setExecutor(EventCommand(this))

        if (config.getBoolean("border.enabled", true)) {
            borderManager.startBorderDisplay()
        }

        val worlds = getWorlds()
        for (world in worlds) {
            world.setGameRule(GameRule.KEEP_INVENTORY, true)
        }

        logger.info("The Diawars-Plugin got activated!")
    }

    override fun onDisable() {
        store.pvpStatusStore.stop()
        server.scheduler.cancelTasks(this)
        logger.info("The Diawars-Plugin got deactivated!")
    }

    fun reloadPlugin() {
        reloadConfig()
        teamManager.loadTeamsFromConfig()
        diamondScoreboardManager.start()
        logger.info("The Diawars-Plugin got reloaded!")
    }
}
