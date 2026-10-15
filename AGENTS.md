# AGENTS.md — Diawars Plugin

Single-module Kotlin/JVM Paper plugin. Entrypoint: `src/main/kotlin/de/davidsw/diawars/Diawars.kt` (wires everything in `onEnable`).

## Build (mandatory)

- After **every** code change, run: `./gradlew build`
- `build` already includes `shadowJar` (`tasks.build { dependsOn("shadowJar") }`). No separate jar step needed.
- Toolchain is Java 25 via Gradle `jvmToolchain` — do not change system Java, Gradle provisions it.
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
- Uncommitted work is normal here; check `git status` before starting.

## Wiring a feature (all steps required)

New commands/menus/stores must be registered in **all** of these or they silently do nothing:

1. `Diawars.kt`: instantiate manager/store/menu, `registerEvents(...)`, `getCommand(...)?setExecutor(...)`, add to `Store`/`Menu` data classes.
2. `src/main/resources/plugin.yml`: declare the command + `permission: diawars.use` (players) or `diawars.admin` (admins). `version`/`api-version` are manual — bump together with `build.gradle.kts`.
3. `MenuManager.kt`: new menus need `TITLE_*`, `open*Menu()`, plus cases in `back()`/`next()`.
4. `config.yml` additions: add default + read via `config.getX(...)`, and handle in `reloadPluginConfigs()` (invoked by `/admin reload`) or reload will drop the value.

Packages: `commands/` (executors) → `managers/` (logic) → `stores/` (YAML persistence) + `menu/` (GUIs) + `listeners/` (events) + `util/` (parsers, `MiniMessageHelper`, `MenuUtils`).

## Gotchas

- Persistence: extend `YamlStore`; register instance in `Store.all` (else never flushed). Use `markDirty()` on hot paths, `saveImmediately()` sparingly, `flushNow()` only for custody transfers. Flush task interval comes from `storage.flush-interval-seconds`.
- Player-originated text must go through `MiniMessageHelper.escape()` before `mm()`/`pmm()` (past XSS fix).
- Menus re-render every 40 ticks — keep `populate*` cheap (no scans/lookups per viewer).
- ItemStack/YAML serialization must stay on the main thread (see `YamlStore` docs); only file writes go async.
- `todo` file at root is the bug/feature backlog — check it for intended behavior, update it when done.
- Sibling repos in parent dir (`diawars-client-fabric`, `diawars-client-forge`, `diawars-server`): image/payload features span plugin + client mods; live test server lives in `../diawars-server`.

## Bedrock / Geyser support

- Bedrock players join via Geyser + Floodgate (plugins on the Paper server). Detect them with `util/BedrockUtil.isBedrock(player)` — Floodgate API first, Geyser API fallback, both via reflection so there is no hard dependency. When neither is installed, everyone counts as Java.
- `plugin.yml` has `softdepend: [floodgate, Geyser-Spigot]` for load order; NBTAPI stays a hard `depend`.
- Bedrock players are exempt from the client-mod kick (`ClientInfoListener`) and the whole image feature is disabled for them (`ImageCommand`, `MenuManager.openImageMenu`, `ImageManager.requestUploadUi`, disabled slot in `MainMenu`) — they have no mod for the `diawars:client_info` / `diawars:image` payloads.
- Menu navigation icons: Bedrock viewers get Arrow/Barrier instead of custom skulls (render as Steve) and Structure Void (no Bedrock item) — see `MenuManager.fillBorder`.
- The handbook/manual was removed entirely (written books don't open via Geyser).
- Known parity gaps (document, don't silently "fix"): the bundle diamond check only sees `InventoryClickEvent` (Bedrock tap-insert bypasses it), shulker logic assumes Java click actions, offhand kits don't render on Bedrock, `DUST`/RGB border particles map poorly, scoreboard swaps flicker, `<click:suggest_command:...>` prompts are dead text on Bedrock.

## Maintaining this file

- When you (the AI agent) learn something during a task that you estimate is important for future development, you are allowed to add it to this `AGENTS.md`. You must then report in the chat what you changed and why.
