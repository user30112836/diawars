package de.davidsw.diawars

import de.davidsw.diawars.commands.AdminCommand
import de.davidsw.diawars.commands.BugCommand
import de.davidsw.diawars.commands.EventCommand
import de.davidsw.diawars.commands.ImageCommand
import de.davidsw.diawars.commands.InvCommand
import de.davidsw.diawars.commands.LobbyCommand
import de.davidsw.diawars.commands.MenuCommand
import de.davidsw.diawars.commands.ModsCommand
import de.davidsw.diawars.commands.NightVisionCommand
import de.davidsw.diawars.commands.PvPCommand
import de.davidsw.diawars.commands.ReportCommand
import de.davidsw.diawars.commands.ScoresCommand
import de.davidsw.diawars.commands.SelfKillCommand
import de.davidsw.diawars.commands.TeamZonesCommand
import de.davidsw.diawars.commands.VaultCommand
import de.davidsw.diawars.listeners.AdminModListener
import de.davidsw.diawars.listeners.AfkActivityListener
import de.davidsw.diawars.listeners.BugListener
import de.davidsw.diawars.listeners.ClientInfoListener
import de.davidsw.diawars.listeners.ContainerExplosionListener
import de.davidsw.diawars.listeners.DiamondBundleListener
import de.davidsw.diawars.listeners.DiamondLimitListener
import de.davidsw.diawars.listeners.DiamondListener
import de.davidsw.diawars.listeners.EventListener
import de.davidsw.diawars.listeners.HandshakeListener
import de.davidsw.diawars.listeners.ImageListener
import de.davidsw.diawars.listeners.InventoryInspectListener
import de.davidsw.diawars.listeners.LobbyListener
import de.davidsw.diawars.listeners.MenuListener
import de.davidsw.diawars.listeners.MessageListener
import de.davidsw.diawars.listeners.NightVisionListener
import de.davidsw.diawars.listeners.PlayerEventListener
import de.davidsw.diawars.listeners.PlayerRespawnListener
import de.davidsw.diawars.listeners.PlayerSpawnChangeListener
import de.davidsw.diawars.listeners.PvPListener
import de.davidsw.diawars.listeners.ReportListener
import de.davidsw.diawars.listeners.RewardListener
import de.davidsw.diawars.listeners.VaultListener
import de.davidsw.diawars.listeners.WhitelistListener
import de.davidsw.diawars.listeners.WorldProtectionListener
import de.davidsw.diawars.managers.AdminGuiManager
import de.davidsw.diawars.managers.AfkManager
import de.davidsw.diawars.managers.BorderManager
import de.davidsw.diawars.managers.BugManager
import de.davidsw.diawars.managers.ContainerExplosionManager
import de.davidsw.diawars.managers.DiamondLimitManager
import de.davidsw.diawars.managers.DiamondLogManager
import de.davidsw.diawars.managers.DiamondScoreboardManager
import de.davidsw.diawars.managers.EventManager
import de.davidsw.diawars.managers.ImageManager
import de.davidsw.diawars.managers.InventoryInspectManager
import de.davidsw.diawars.managers.LobbyManager
import de.davidsw.diawars.managers.MenuManager
import de.davidsw.diawars.managers.MessageManager
import de.davidsw.diawars.managers.ModWhitelistManager
import de.davidsw.diawars.managers.NightVisionManager
import de.davidsw.diawars.managers.ReportManager
import de.davidsw.diawars.stores.PlayerDiamondStore
import de.davidsw.diawars.managers.PvPManager
import de.davidsw.diawars.managers.RecipeManager
import de.davidsw.diawars.managers.RewardManager
import de.davidsw.diawars.managers.ScoresManager
import de.davidsw.diawars.managers.ShulkerAccessManager
import de.davidsw.diawars.managers.TeamManager
import de.davidsw.diawars.managers.TextInputManager
import de.davidsw.diawars.managers.VaultManager
import de.davidsw.diawars.managers.ZoneManager
import de.davidsw.diawars.menu.BorderMenu
import de.davidsw.diawars.menu.EventConfigMenu
import de.davidsw.diawars.menu.EventMenu
import de.davidsw.diawars.menu.ImageMenu
import de.davidsw.diawars.menu.MainMenu
import de.davidsw.diawars.menu.ScoreboardMenu
import de.davidsw.diawars.menu.SettingsMenu
import de.davidsw.diawars.menu.TogglesMenu
import de.davidsw.diawars.menu.VaultListMenu
import de.davidsw.diawars.menu.VaultMenu
import de.davidsw.diawars.stores.BorderPreferencesStore
import de.davidsw.diawars.stores.BugStore
import de.davidsw.diawars.stores.ClientInfoStore
import de.davidsw.diawars.stores.EventConfigStore
import de.davidsw.diawars.stores.EventInventoryStore
import de.davidsw.diawars.stores.EventStore
import de.davidsw.diawars.stores.ImageBanStore
import de.davidsw.diawars.stores.ImageLimitStore
import de.davidsw.diawars.stores.ImageMapStore
import de.davidsw.diawars.stores.MessageStore
import de.davidsw.diawars.stores.ModRequestStore
import de.davidsw.diawars.stores.NightVisionStore
import de.davidsw.diawars.stores.OnboardingStore
import de.davidsw.diawars.stores.PlayerSpawnStore
import de.davidsw.diawars.stores.PlayerStateStore
import de.davidsw.diawars.stores.PvPStatusStore
import de.davidsw.diawars.stores.RewardStore
import de.davidsw.diawars.stores.ReportStore
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
    var settingsMenu: SettingsMenu,
    var togglesMenu: TogglesMenu,
    var eventMenu: EventMenu,
    var eventConfigMenu: EventConfigMenu,
    var vaultMenu: VaultMenu,
    var vaultListMenu: VaultListMenu,
    var imageMenu: ImageMenu,
)

class Store(
    val playerDiamondStore: PlayerDiamondStore,
    val borderPreferencesStore: BorderPreferencesStore,
    val pvpStatusStore: PvPStatusStore,
    val lobbyStateStore: PlayerStateStore,
    val eventStateStore: PlayerStateStore,
    val eventStore: EventStore,
    val eventInventoryStore: EventInventoryStore,
    val eventConfigStore: EventConfigStore,
    val playerSpawnStore: PlayerSpawnStore,
    val rewardStore: RewardStore,
    val scoreboardPreferencesStore: ScoreboardPreferencesStore,
    val messageStore: MessageStore,
    val vaultDiamondStore: VaultDiamondStore,
    val vaultClaimStore: VaultClaimStore,
    val bugStore: BugStore,
    val onboardingStore: OnboardingStore,
    val clientInfoStore: ClientInfoStore,
    val imageLimitStore: ImageLimitStore,
    val imageMapStore: ImageMapStore,
    val imageBanStore: ImageBanStore,
    val nightVisionStore: NightVisionStore,
    val reportStore: ReportStore,
    val modRequestStore: ModRequestStore,
) {
    private val all: List<YamlStore> = listOf(
        playerDiamondStore, borderPreferencesStore, pvpStatusStore,
        lobbyStateStore, eventStateStore, eventStore, eventInventoryStore, eventConfigStore,
        playerSpawnStore, rewardStore, scoreboardPreferencesStore, messageStore,
        vaultDiamondStore, vaultClaimStore, bugStore, onboardingStore, clientInfoStore,
        imageLimitStore, imageMapStore, imageBanStore, nightVisionStore, reportStore,
        modRequestStore,
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
    lateinit var modWhitelistManager: ModWhitelistManager
    lateinit var imageManager: ImageManager
    lateinit var textInputManager: TextInputManager
    lateinit var reportManager: ReportManager
    lateinit var nightVisionManager: NightVisionManager
    lateinit var recipeManager: RecipeManager
    lateinit var adminGuiManager: AdminGuiManager

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
            eventConfigStore = EventConfigStore(this),
            playerSpawnStore = PlayerSpawnStore(this),
            rewardStore = RewardStore(this),
            scoreboardPreferencesStore = ScoreboardPreferencesStore(this),
            messageStore = MessageStore(this),
            vaultDiamondStore = VaultDiamondStore(this),
            vaultClaimStore = VaultClaimStore(this),
            bugStore = BugStore(this),
            onboardingStore = OnboardingStore(this),
            clientInfoStore = ClientInfoStore(this),
            imageLimitStore = ImageLimitStore(this),
            imageMapStore = ImageMapStore(this),
            imageBanStore = ImageBanStore(this),
            nightVisionStore = NightVisionStore(this),
            reportStore = ReportStore(this),
            modRequestStore = ModRequestStore(this),
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
        modWhitelistManager = ModWhitelistManager(this)
        imageManager = ImageManager(this)
        textInputManager = TextInputManager(this)
        // ReportManager needs imageManager (image lookups, thumbnail scaling)
        // and textInputManager (reason dialog).
        reportManager = ReportManager(this)
        nightVisionManager = NightVisionManager(this)
        recipeManager = RecipeManager(this)
        // Needs the managers and stores above (GUI data pulls).
        adminGuiManager = AdminGuiManager(this)

        menu = Menu(
            mainMenu = MainMenu(this),
            borderMenu = BorderMenu(this),
            scoreboardMenu = ScoreboardMenu(this),
            settingsMenu = SettingsMenu(this),
            togglesMenu = TogglesMenu(this),
            eventMenu = EventMenu(this),
            eventConfigMenu = EventConfigMenu(this),
            vaultMenu = VaultMenu(this),
            vaultListMenu = VaultListMenu(this),
            imageMenu = ImageMenu(this),
        )

        startStoreFlushTask()

        diamondLimitManager.startTrackingTask()
        diamondLimitManager.trackExistingDiamonds()
        diamondScoreboardManager.start()
        pvpManager.reactivateTasks()
        eventManager.reactivateSchedules()
        lobbyManager.ensureWorldLoaded()
        afkManager.start()
        imageManager.restoreMapRenderers()
        recipeManager.registerRecipes()

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
        server.pluginManager.registerEvents(DiamondBundleListener(this), this)
        server.pluginManager.registerEvents(AfkActivityListener(this), this)
        server.pluginManager.registerEvents(InventoryInspectListener(), this)
        server.pluginManager.registerEvents(ClientInfoListener(this), this)
        server.pluginManager.registerEvents(ImageListener(this), this)
        server.pluginManager.registerEvents(ReportListener(this), this)
        server.pluginManager.registerEvents(WhitelistListener(this), this)
        server.pluginManager.registerEvents(NightVisionListener(this), this)
        server.pluginManager.registerEvents(AdminModListener(this), this)
        server.pluginManager.registerEvents(recipeManager, this)

        server.messenger.registerIncomingPluginChannel(this, ClientInfoListener.CHANNEL, ClientInfoListener(this))
        server.messenger.registerIncomingPluginChannel(this, HandshakeListener.CHANNEL, HandshakeListener(this))
        server.messenger.registerIncomingPluginChannel(this, ImageListener.CHANNEL, ImageListener(this))
        server.messenger.registerIncomingPluginChannel(this, ImageListener.REQ_CHANNEL, ImageListener(this))
        server.messenger.registerIncomingPluginChannel(this, ImageListener.OFFER_CHANNEL, ImageListener(this))
        server.messenger.registerIncomingPluginChannel(this, ImageListener.CHECK_CHANNEL, ImageListener(this))
        // Diawars Admin client mod image inspection (see ImageListener).
        server.messenger.registerIncomingPluginChannel(this, ImageListener.ADMIN_REQ_CHANNEL, ImageListener(this))
        server.messenger.registerIncomingPluginChannel(this, ReportManager.SUBMIT_CHANNEL, ReportListener(this))
        server.messenger.registerOutgoingPluginChannel(this, ImageListener.OPEN_CHANNEL)
        server.messenger.registerOutgoingPluginChannel(this, ImageListener.DATA_CHANNEL)
        server.messenger.registerOutgoingPluginChannel(this, ImageListener.ADMIN_DATA_CHANNEL)
        server.messenger.registerOutgoingPluginChannel(this, ImageListener.ACCEPT_CHANNEL)
        server.messenger.registerOutgoingPluginChannel(this, ImageListener.CHECK_OK_CHANNEL)
        server.messenger.registerOutgoingPluginChannel(this, HandshakeListener.ACK_CHANNEL)
        server.messenger.registerOutgoingPluginChannel(this, TextInputManager.CHANNEL)
        server.messenger.registerOutgoingPluginChannel(this, ReportManager.SHOT_CHANNEL)
        server.messenger.registerOutgoingPluginChannel(this, ReportManager.DIALOG_CHANNEL)
        server.messenger.registerOutgoingPluginChannel(this, ModWhitelistManager.DIALOG_CHANNEL)
        server.messenger.registerIncomingPluginChannel(
            this,
            ModWhitelistManager.REQUEST_CHANNEL,
            WhitelistListener(this),
        )
        // Diawars Admin client mod: handshake, admin-only registration and GUI data pulls.
        server.messenger.registerIncomingPluginChannel(this, AdminModListener.HANDSHAKE_CHANNEL, AdminModListener(this))
        server.messenger.registerIncomingPluginChannel(this, AdminModListener.CLIENT_INFO_CHANNEL, AdminModListener(this))
        server.messenger.registerIncomingPluginChannel(this, AdminModListener.FETCH_CHANNEL, AdminModListener(this))
        server.messenger.registerOutgoingPluginChannel(this, AdminModListener.ACK_CHANNEL)
        server.messenger.registerOutgoingPluginChannel(this, AdminModListener.OPEN_CHANNEL)
        server.messenger.registerOutgoingPluginChannel(this, AdminModListener.DATA_CHANNEL)

        getCommand("teamzones")?.setExecutor(TeamZonesCommand(this))
        getCommand("pvp")?.setExecutor(PvPCommand(this))
        getCommand("scores")?.setExecutor(ScoresCommand(this))
        getCommand("menu")?.setExecutor(MenuCommand(this))
        getCommand("selfkill")?.setExecutor(SelfKillCommand(this))
        getCommand("event")?.setExecutor(EventCommand(this))
        getCommand("lobby")?.setExecutor(LobbyCommand(this))
        getCommand("inv")?.setExecutor(InvCommand(this))
        getCommand("vault")?.setExecutor(VaultCommand(this))
        getCommand("bug")?.setExecutor(BugCommand(this))
        getCommand("admin")?.setExecutor(AdminCommand(this))
        getCommand("image")?.setExecutor(ImageCommand(this))
        getCommand("report")?.setExecutor(ReportCommand(this))
        getCommand("nightvision")?.setExecutor(NightVisionCommand(this))
        getCommand("mods")?.setExecutor(ModsCommand(this))

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

    fun reloadPluginConfigs() {
        reloadConfig()
        teamManager.loadTeamsFromConfig()
        vaultManager.loadFromConfig()
        modWhitelistManager.loadFromConfig()
        nightVisionManager.refreshGlobalState()

        if (config.getBoolean("border.enabled", true)) {
            borderManager.startBorderDisplay()
        } else {
            borderManager.stopBorderDisplay()
        }
    }

    override fun onDisable() {
        eventManager.saveActiveInventories()
        store.flushAllNow()
        diamondLogManager.flushSync()
        server.scheduler.cancelTasks(this)
        logger.info("The Diawars-Plugin got deactivated!")
    }
}
