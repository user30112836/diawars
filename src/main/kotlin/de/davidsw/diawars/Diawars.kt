package de.davidsw.diawars

import de.davidsw.diawars.commands.AdminInvCommand
import de.davidsw.diawars.commands.BugCommand
import de.davidsw.diawars.commands.EventCommand
import de.davidsw.diawars.commands.InvCommand
import de.davidsw.diawars.commands.LobbyCommand
import de.davidsw.diawars.commands.LogCommand
import de.davidsw.diawars.commands.MenuCommand
import de.davidsw.diawars.commands.PvPCommand
import de.davidsw.diawars.commands.ScoresCommand
import de.davidsw.diawars.commands.SelfKillCommand
import de.davidsw.diawars.commands.TeamZonesCommand
import de.davidsw.diawars.commands.VaultCommand
import de.davidsw.diawars.listeners.BugListener
import de.davidsw.diawars.listeners.ContainerExplosionListener
import de.davidsw.diawars.listeners.DiamondLimitListener
import de.davidsw.diawars.listeners.DiamondListener
import de.davidsw.diawars.listeners.EventListener
import de.davidsw.diawars.listeners.InventoryInspectListener
import de.davidsw.diawars.listeners.LobbyListener
import de.davidsw.diawars.listeners.MenuListener
import de.davidsw.diawars.listeners.MessageListener
import de.davidsw.diawars.listeners.PlayerEventListener
import de.davidsw.diawars.listeners.PlayerRespawnListener
import de.davidsw.diawars.listeners.PlayerSpawnChangeListener
import de.davidsw.diawars.listeners.PvPListener
import de.davidsw.diawars.listeners.RewardListener
import de.davidsw.diawars.listeners.VaultListener
import de.davidsw.diawars.listeners.WorldProtectionListener
import de.davidsw.diawars.managers.AfkManager
import de.davidsw.diawars.managers.BorderManager
import de.davidsw.diawars.managers.BugManager
import de.davidsw.diawars.managers.ContainerExplosionManager
import de.davidsw.diawars.managers.DiamondLimitManager
import de.davidsw.diawars.managers.DiamondLogManager
import de.davidsw.diawars.managers.DiamondScoreboardManager
import de.davidsw.diawars.managers.EventManager
import de.davidsw.diawars.managers.InventoryInspectManager
import de.davidsw.diawars.managers.LobbyManager
import de.davidsw.diawars.managers.ManualManager
import de.davidsw.diawars.managers.MenuManager
import de.davidsw.diawars.managers.MessageManager
import de.davidsw.diawars.stores.PlayerDiamondStore
import de.davidsw.diawars.managers.PvPManager
import de.davidsw.diawars.managers.RewardManager
import de.davidsw.diawars.managers.ScoresManager
import de.davidsw.diawars.managers.ShulkerAccessManager
import de.davidsw.diawars.managers.TeamManager
import de.davidsw.diawars.managers.VaultManager
import de.davidsw.diawars.managers.ZoneManager
import de.davidsw.diawars.menu.BorderMenu
import de.davidsw.diawars.menu.EventMenu
import de.davidsw.diawars.menu.MainMenu
import de.davidsw.diawars.menu.ManualMenu
import de.davidsw.diawars.menu.ScoreboardMenu
import de.davidsw.diawars.menu.VaultListMenu
import de.davidsw.diawars.menu.VaultMenu
import de.davidsw.diawars.stores.BorderPreferencesStore
import de.davidsw.diawars.stores.BugStore
import de.davidsw.diawars.stores.EventInventoryStore
import de.davidsw.diawars.stores.EventStore
import de.davidsw.diawars.stores.MessageStore
import de.davidsw.diawars.stores.OnboardingStore
import de.davidsw.diawars.stores.PlayerSpawnStore
import de.davidsw.diawars.stores.PlayerStateStore
import de.davidsw.diawars.stores.PvPStatusStore
import de.davidsw.diawars.stores.RewardStore
import de.davidsw.diawars.stores.ScoreboardPreferencesStore
import de.davidsw.diawars.stores.VaultClaimStore
import de.davidsw.diawars.stores.VaultDiamondStore
import de.davidsw.diawars.stores.YamlStore
import org.bukkit.Bukkit.getWorlds
import org.bukkit.GameRules
import org.bukkit.plugin.java.JavaPlugin

data class Menu(
    var mainMenu: MainMenu,
    var borderMenu: BorderMenu,
    var scoreboardMenu: ScoreboardMenu,
    var eventMenu: EventMenu,
    var vaultMenu: VaultMenu,
    var vaultListMenu: VaultListMenu,
    var manualMenu: ManualMenu,
)

class Store(
    val playerDiamondStore: PlayerDiamondStore,
    val borderPreferencesStore: BorderPreferencesStore,
    val pvpStatusStore: PvPStatusStore,
    val lobbyStateStore: PlayerStateStore,
    val eventStateStore: PlayerStateStore,
    val eventStore: EventStore,
    val eventInventoryStore: EventInventoryStore,
    val playerSpawnStore: PlayerSpawnStore,
    val rewardStore: RewardStore,
    val scoreboardPreferencesStore: ScoreboardPreferencesStore,
    val messageStore: MessageStore,
    val vaultDiamondStore: VaultDiamondStore,
    val vaultClaimStore: VaultClaimStore,
    val bugStore: BugStore,
    val onboardingStore: OnboardingStore,
) {
    private val all: List<YamlStore> = listOf(
        playerDiamondStore, borderPreferencesStore, pvpStatusStore,
        lobbyStateStore, eventStateStore, eventStore, eventInventoryStore,
        playerSpawnStore, rewardStore, scoreboardPreferencesStore, messageStore,
        vaultDiamondStore, vaultClaimStore, bugStore, onboardingStore,
    )

    fun flushDirty(): Int = all.count { it.flushIfDirty() }

    fun flushAllNow() = all.forEach { it.flushNow(isFinal = true) }
}

class Diawars : JavaPlugin() {

    lateinit var teamManager: TeamManager
    lateinit var zoneManager: ZoneManager
    lateinit var borderManager: BorderManager
    lateinit var pvpManager: PvPManager
    lateinit var containerExplosionManager: ContainerExplosionManager
    lateinit var diamondLimitManager: DiamondLimitManager
    lateinit var diamondScoreboardManager: DiamondScoreboardManager
    lateinit var diamondLogManager: DiamondLogManager
    lateinit var menuManager: MenuManager
    lateinit var scoresManager: ScoresManager
    lateinit var eventManager: EventManager
    lateinit var rewardManager: RewardManager
    lateinit var lobbyManager: LobbyManager
    lateinit var afkManager: AfkManager
    lateinit var shulkerAccessManager: ShulkerAccessManager
    lateinit var messageManager: MessageManager
    lateinit var vaultManager: VaultManager
    lateinit var bugManager: BugManager
    lateinit var inventoryInspectManager: InventoryInspectManager
    lateinit var manualManager: ManualManager

    lateinit var store: Store
    lateinit var menu: Menu

    override fun onEnable() {
        saveDefaultConfig()

        store = Store(
            playerDiamondStore = PlayerDiamondStore(this),
            borderPreferencesStore = BorderPreferencesStore(this),
            pvpStatusStore = PvPStatusStore(this),
            lobbyStateStore = PlayerStateStore(this, "lobby_player_states.yml"),
            eventStateStore = PlayerStateStore(this, "event_player_states.yml"),
            eventStore = EventStore(this),
            eventInventoryStore = EventInventoryStore(this),
            playerSpawnStore = PlayerSpawnStore(this),
            rewardStore = RewardStore(this),
            scoreboardPreferencesStore = ScoreboardPreferencesStore(this),
            messageStore = MessageStore(this),
            vaultDiamondStore = VaultDiamondStore(this),
            vaultClaimStore = VaultClaimStore(this),
            bugStore = BugStore(this),
            onboardingStore = OnboardingStore(this),
        )

        teamManager = TeamManager(this)
        zoneManager = ZoneManager(this)
        borderManager = BorderManager(this)
        pvpManager = PvPManager(this)
        containerExplosionManager = ContainerExplosionManager(this)
        diamondLimitManager = DiamondLimitManager(this)
        diamondScoreboardManager = DiamondScoreboardManager(this)
        diamondLogManager = DiamondLogManager(this)
        menuManager = MenuManager(this)
        scoresManager = ScoresManager(this)
        eventManager = EventManager(this)
        rewardManager = RewardManager(this)
        lobbyManager = LobbyManager(this)
        afkManager = AfkManager(this)
        shulkerAccessManager = ShulkerAccessManager()
        messageManager = MessageManager(this)
        vaultManager = VaultManager(this)
        bugManager = BugManager(this)
        inventoryInspectManager = InventoryInspectManager(this)
        manualManager = ManualManager(this)

        menu = Menu(
            mainMenu = MainMenu(this),
            borderMenu = BorderMenu(this),
            scoreboardMenu = ScoreboardMenu(this),
            eventMenu = EventMenu(this),
            vaultMenu = VaultMenu(this),
            vaultListMenu = VaultListMenu(this),
            manualMenu = ManualMenu(this),
        )

        startStoreFlushTask()

        diamondLimitManager.startTrackingTask()
        diamondLimitManager.trackExistingDiamonds()
        diamondScoreboardManager.start()
        pvpManager.reactivateTasks()
        eventManager.reactivateSchedules()
        lobbyManager.ensureWorldLoaded()
        afkManager.start()

        server.pluginManager.registerEvents(PlayerEventListener(this), this)
        server.pluginManager.registerEvents(PlayerRespawnListener(this), this)
        server.pluginManager.registerEvents(PlayerSpawnChangeListener(this), this)
        server.pluginManager.registerEvents(PvPListener(this), this)
        server.pluginManager.registerEvents(DiamondLimitListener(this), this)
        server.pluginManager.registerEvents(ContainerExplosionListener(this), this)
        server.pluginManager.registerEvents(WorldProtectionListener(this), this)
        server.pluginManager.registerEvents(MenuListener(this), this)
        server.pluginManager.registerEvents(EventListener(this), this)
        server.pluginManager.registerEvents(RewardListener(this), this)
        server.pluginManager.registerEvents(DiamondListener(this), this)
        server.pluginManager.registerEvents(LobbyListener(this), this)
        server.pluginManager.registerEvents(MessageListener(this), this)
        server.pluginManager.registerEvents(VaultListener(this), this)
        server.pluginManager.registerEvents(BugListener(this), this)
        server.pluginManager.registerEvents(InventoryInspectListener(), this)

        getCommand("teamzones")?.setExecutor(TeamZonesCommand(this))
        getCommand("pvp")?.setExecutor(PvPCommand(this))
        getCommand("scores")?.setExecutor(ScoresCommand(this))
        getCommand("menu")?.setExecutor(MenuCommand(this))
        getCommand("selfkill")?.setExecutor(SelfKillCommand(this))
        getCommand("event")?.setExecutor(EventCommand(this))
        getCommand("lobby")?.setExecutor(LobbyCommand(this))
        getCommand("inv")?.setExecutor(InvCommand(this))
        getCommand("vault")?.setExecutor(VaultCommand(this))
        getCommand("log")?.setExecutor(LogCommand(this))
        getCommand("bug")?.setExecutor(BugCommand(this))
        getCommand("admininv")?.setExecutor(AdminInvCommand(this))

        if (config.getBoolean("border.enabled", true)) {
            borderManager.startBorderDisplay()
        }

        val worlds = getWorlds()
        for (world in worlds) {
            world.setGameRule(GameRules.KEEP_INVENTORY, true)
        }

        logger.info("The Diawars-Plugin got activated!")
    }

    private fun startStoreFlushTask() {
        val intervalTicks = config.getInt("storage.flush-interval-seconds", 30).coerceAtLeast(1) * 20L
        server.scheduler.runTaskTimer(this, Runnable { store.flushDirty() }, intervalTicks, intervalTicks)
    }

    override fun onDisable() {
        eventManager.saveActiveInventories()
        store.flushAllNow()
        server.scheduler.cancelTasks(this)
        logger.info("The Diawars-Plugin got deactivated!")
    }
}
