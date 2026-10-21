# AGENTS.md — Diawars Plugin

Single-module Kotlin/JVM Paper plugin. Entrypoint: `src/main/kotlin/de/davidsw/diawars/Diawars.kt` (wires everything in `onEnable`).

## Build (mandatory)

- After **every** code change, run: `./gradlew build`
- `build` already includes `shadowJar` (`tasks.build { dependsOn("shadowJar") }`). No separate jar step needed.
- Toolchain is Java 25 via Gradle `jvmToolchain` — do not change system Java, Gradle provisions it.
- Run Gradle with Java 21 (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk`): the Gradle 8.8 daemon cannot start on system Java 26 (`IllegalArgumentException` in the Kotlin DSL). Compilation still uses the Java 25 toolchain.
- Paper API `26.2` is `compileOnly`; NBTAPI is a hard `depend` (see `plugin.yml`).
- There are **no tests** (`src/test` does not exist). Build is the verification.
- After every build, deploy to the test server: delete `../diawars-server/plugins/Diawars-*.jar` and copy the fresh `build/libs/*-all.jar` (the shadow jar, e.g. `Diawars-5.1-beta-all.jar`) there. Never touch the other jars (NBTAPI, WorldEdit, …).
- Never start `../diawars-server` yourself — deploy only, testing is the user's job.

## Commits

- Before committing, check the plugin works correctly: build must pass and the change must be deployed to `../diawars-server` (see above). If correctness is still uncertain after the build, ask the user instead of committing.
- Follow existing log pattern (`git log --oneline`): one user-visible change per commit, plain sentence, optional `#Tag` prefix:
  - `#Fix <what now works>`, `#Performance <what improved>`, `#Improvement <what changed>` — otherwise no prefix (e.g. `Players now receive 32 Diamonds on first launch`).
  - No conventional-commits / scopes.
- Commit finished logical units promptly; do not bundle unrelated features. Do not commit `build/`, `run/`, `.gradle/` (gitignored). `gradlew` + `gradle/` ARE committed — keep it that way.
- Auto commits are allowed: commit finished logical units without asking the user for approval first.
- Uncommitted work is normal here; check `git status` before starting.

## Wiring a feature (all steps required)

New commands/menus/stores must be registered in **all** of these or they silently do nothing:

1. `Diawars.kt`: instantiate manager/store/menu, `registerEvents(...)`, `getCommand(...)?setExecutor(...)`, add to `Store`/`Menu` data classes.
2. `src/main/resources/plugin.yml`: declare the command + `permission: diawars.use` (players) or `diawars.admin` (admins). `version`/`api-version` are manual — bump together with `build.gradle.kts`.
3. `MenuManager.kt`: new menus need `TITLE_*`, `open*Menu()`, plus cases in `back()`/`next()`.
4. `config.yml` additions: add default + read via `config.getX(...)`, and handle in `reloadPluginConfigs()` (invoked by `/admin reload`) or reload will drop the value.

Packages: `commands/` (executors) → `managers/` (logic) → `stores/` (YAML persistence) + `menu/` (GUIs) + `listeners/` (events) + `util/` (parsers, `MiniMessageHelper`, `MenuUtils`).

## Gotchas

- Char-class regexes: keep `-` at the END of the class (`[A-Za-z0-9 ._()-]`) — a `-` in the middle (e.g. `_-(`) parses as a character range and throws `PatternSyntaxException` at class init (crashed plugin + both mods once).

- Persistence: extend `YamlStore`; register instance in `Store.all` (else never flushed). Use `markDirty()` on hot paths, `saveImmediately()` sparingly, `flushNow()` only for custody transfers. Flush task interval comes from `storage.flush-interval-seconds`.
- Player-originated text must go through `MiniMessageHelper.escape()` before `mm()`/`pmm()` (past XSS fix).
- Menus re-render every 40 ticks — keep `populate*` cheap (no scans/lookups per viewer).
- ItemStack/YAML serialization must stay on the main thread (see `YamlStore` docs); only file writes go async.
- `todo` file at root is the bug/feature backlog — check it for intended behavior, update it when done.
- Sibling repos in parent dir (`diawars-client-fabric`, `diawars-client-forge`, `diawars-server`): image/payload features span plugin + client mods; live test server lives in `../diawars-server`.
- Client mods (Gradle 9.5.1, `sourceCompatibility = 25`) must be built with Java 25 (`JAVA_HOME=/usr/lib/jvm/java-25-openjdk`); Java 21 fails with `invalid source release: 25`.
- Deploy the Fabric mod for testing after every build: replace the `diawars-client-*.jar` in the Modrinth App test profile `~/.local/share/ModrinthApp/profiles/Fabric 26.2/mods/` with the fresh `build/libs/diawars-client-<version>.jar` (version from the mod's `gradle.properties`; pick the newest artifact when several versions exist, and never touch the other jars in `mods/`). Real players still install the jars themselves; the Forge mod has no deploy step.

## Bedrock / Geyser support

- Bedrock players join via Geyser + Floodgate (plugins on the Paper server). Detect them with `util/BedrockUtil.isBedrock(player)` — Floodgate API first, Geyser API fallback, both via reflection so there is no hard dependency. When neither is installed, everyone counts as Java.
- `plugin.yml` has `softdepend: [floodgate, Geyser-Spigot]` for load order; NBTAPI stays a hard `depend`.
- Bedrock players are exempt from the client-mod kick (`ClientInfoListener`) and the whole image feature is disabled for them (`ImageCommand`, `MenuManager.openImageMenu`, `ImageManager.requestUploadUi`, disabled slot in `MainMenu`) — they have no mod for the `diawars:client_info` / `diawars:image` payloads.
- Menu navigation icons: Bedrock viewers get Arrow/Barrier instead of custom skulls (render as Steve) and Structure Void (no Bedrock item) — see `MenuManager.fillBorder`.
- The handbook/manual was removed entirely (written books don't open via Geyser).
- Known parity gaps (document, don't silently "fix"): the bundle diamond check only sees `InventoryClickEvent` (Bedrock tap-insert bypasses it), shulker logic assumes Java click actions, offhand kits don't render on Bedrock, `DUST`/RGB border particles map poorly, scoreboard swaps flicker. `<click:suggest_command:...>` prompts are dead text on Bedrock — the menu text prompts avoid them for Bedrock via `TextInputManager`.
- Text prompts ("Bitte gib ... ein:") go through `managers/TextInputManager` (`TextInputManager.Prompt` registry). It sends a `diawars:text_input` JSON payload (command prefix, title, label, hint, maxLength) that both client mods open as a dialog; on submit the mod runs `command prefix + text` itself via `sendCommand`, so there is no serverbound answer channel. Guarded by `TextInputManager.CHANNEL in player.listeningPluginChannels` — only admins can play mod-less or with an outdated mod, everyone gets the old `<click:suggest_command>` prompt as fallback; Bedrock players get a plain "Tippe im Chat ein: /<command>" instruction instead (no links). Menus must never send those prompts directly.

## Image uploads

- Image file names allow `A-Za-z0-9 ._-()` (max 32 chars, `all` reserved) on both the plugin and the mod `sanitize` rules — spaces are allowed (split segments are `name (1)` style), so `/image get` joins all remaining args into the file name.
- Split uploads: one image uploads as a rows×cols grid ("Aufteilen" toggle + "Zeilen"/"Spalten" steppers live on the crop screen, not the upload screen). Each cell uploads as `base (k)` row-major (k starts at 1) through the normal offer/chunk pipeline, chained when the connection is idle (`ImageUploader` batch mode + `tickBatch`). Rectangular cells are centred on a square canvas with transparent padding (maps are 1:1). Before anything uploads the mods send `diawars:image_check` (serverbound) with every segment name; `ImageManager.handleNameCheck` rejects the whole grid if any name is invalid/taken or the image limit has no headroom, answering `diawars:image_check_ok` `{"ok": false, "error": ...}` — the client cancels the entire upload and shows the (tag-stripped) message in the upload dialog.

## Report system

- One "Melden" main-menu button → client-mod report dialog (`diawars:report_dialog` → `ReportDialogScreen` in both mods; payload JSON `{handImage, reasonMax}`). The dialog collects all aspects separately: optional player name + optional reason (EditBoxes), screenshot checkbox (pre-checked) and hand-image checkbox (pre-checked when the server saw a Diawars map in the main hand). Submit sends everything at once to the serverbound `diawars:report_submit` payload (`ReportListener` → `ReportManager.handleSubmit`); the server re-validates (online player via `getPlayerExact`, server-authoritative hand image, at least one aspect: player/reason/handImage/shot).
- Players without the dialog channel (Bedrock, outdated mod, mod-less admins) are redirected to the command line: `/report <spieler|-> [grund]` (`ReportCommand` → `submitCommandReport`). The screenshot is always taken when the mod can do it and the hand image is appended if possible; targets/reason may be anything — at least one of player/reason/hand image required. Reports record their path (`ReportSource.MENU`/`COMMAND`), shown as `[Menü]`/`[Befehl]` in `/admin report list|view` and the admin join notification.
- Screenshot attachments: after a successful submit the server immediately sends the `diawars:screenshot` JSON payload `{report, file}` (clientbound, guarded by `SHOT_CHANNEL in listeningPluginChannels`), both client mods capture the current frame via `Screenshot.takeScreenshot` and upload it through the regular offer/chunk pipeline — offer and chunk JSON carry a `report` field, and `ImageListener` routes anything tagged like that into `ReportManager` instead of `ImageManager`/the gallery. Any future "upload without a player gallery" feature should reuse this field-tag pattern. Upload errors for report uploads route to a chat sink via `ImageUploader.initErrorFeedback`. Client mods delay the capture while `ReportDialogScreen` is open (note: the active screen is `client.gui.screen()` in 26.2 — the screen moved off `Minecraft` into `Minecraft.gui`) so the shot shows the world, not the overlay.
- Persistence: `data/reports.yml` (`ReportStore`, schema: source + targetPlayer/targetName + targetOwner/targetFile + optional reason — one report may combine several targets), screenshots as `plugins/Diawars/reports/<id>.png`. Admin review via `/admin report list|view|shot|resolve`; `/admin report shot <id>` hands out a map rendered by `ReportShotRenderer` — deliberately NOT in `ImageMapStore` (must never surface in image browsing/adoption). Bedrock players can report (command path, no screenshot possible).

## Maintaining this file

- When you (the AI agent) learn something during a task that you estimate is important for future development, you are allowed to add it to this `AGENTS.md`. You must then report in the chat what you changed and why.
