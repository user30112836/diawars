package de.davidsw.diawars.managers

import com.google.gson.Gson
import com.google.gson.JsonObject
import de.davidsw.diawars.Diawars
import de.davidsw.diawars.listeners.ImageListener
import de.davidsw.diawars.util.BedrockUtil
import de.davidsw.diawars.util.MiniMessageHelper.escape
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.entity.Player

/**
 * Asks the player's client mod to open a text input dialog (via the
 * [CHANNEL] signal, JSON: command prefix, title, label, hint and max length).
 * When the player submits, the mod runs the typed command itself via
 * [net.minecraft.client.multiplayer.ClientPacketListener.sendCommand],
 * so it behaves exactly like typing the command in chat (permissions,
 * console log, suggestions-known errors).
 *
 * Players without a dialog-capable client keep the old chat flow:
 * Bedrock players get a plain instruction what to type (they cannot click
 * links), mod-less Java players (admins) get the old clickable suggestion.
 */
class TextInputManager(private val plugin: Diawars) {
    companion object {
        /** Server -> client signal asking the mod to open the input dialog. */
        const val CHANNEL = "diawars:text_input"

        /** Vanilla drops commands longer than this; keep the field below it. */
        private const val MAX_PACKET_LENGTH = 256

        private val GSON = Gson()
    }

    /**
     * All prompts the plugin currently needs text input for. [headline] is the
     * dialog title and doubles as the fallback button caption; [chatIntro]
     * starts the fallback chat message.
     */
    enum class Prompt(
        val headline: String,
        val chatIntro: String,
        val label: String,
        val hint: String,
        val commandPrefix: String,
        val placeholder: String,
    ) {
        BUG_REPORT(
            headline = "Bug melden",
            chatIntro = "Bitte beschreibe den Fehler:",
            label = "Beschreibe den Fehler",
            hint = "Was funktioniert nicht?",
            commandPrefix = "bug report ",
            placeholder = "<beschreibung>",
        ),
        VAULT_INVITE(
            headline = "Spieler einladen",
            chatIntro = "Bitte gib den Namen des Spielers ein:",
            label = "Spielername",
            hint = "Name eines Teammitglieds",
            commandPrefix = "vault invite ",
            placeholder = "<spieler>",
        ),
        VAULT_BAN(
            headline = "Spieler entfernen",
            chatIntro = "Bitte gib den Namen des Spielers ein:",
            label = "Spielername",
            hint = "Name eines eingeladenen Spielers",
            commandPrefix = "vault ban ",
            placeholder = "<spieler>",
        ),
        EVENT_CREATE(
            headline = "Event erstellen",
            chatIntro = "Bitte gib den Namen deines Events ein:",
            label = "Name des Events",
            hint = "z. B. Bedwars-Turnier",
            commandPrefix = "event create ",
            placeholder = "<name>",
        ),
    }

    /**
     * Opens the input dialog for [prompt] or falls back to a chat message.
     * Closes the menu in every case like the old prompt flow did.
     */
    fun openPrompt(player: Player, prompt: Prompt) {
        player.closeInventory()

        // Bedrock players can neither receive client payloads nor click chat
        // links: plain instruction what to type.
        if (BedrockUtil.isBedrock(player)) {
            player.sendMessage(mm(
                "<yellow>${prompt.chatIntro}</yellow> " +
                        "<white>Tippe im Chat ein: /${prompt.commandPrefix}${escape(prompt.placeholder)}</white>"
            ))
            return
        }

        // Java players always have the Diawars mod (except exempt admins).
        // Without the channel the mod is outdated or missing: old chat prompt.
        if (TextInputManager.CHANNEL !in player.listeningPluginChannels) {
            sendFallback(player, prompt)
            return
        }

        val doc = JsonObject().apply {
            addProperty("command", prompt.commandPrefix)
            addProperty("title", prompt.headline)
            addProperty("label", prompt.label)
            addProperty("hint", prompt.hint)
            addProperty("maxLength", MAX_PACKET_LENGTH - prompt.commandPrefix.length)
        }
        try {
            player.sendPluginMessage(plugin, CHANNEL, ImageListener.writeVarIntPrefixedUtf8(doc.toString()))
        } catch (e: Exception) {
            plugin.logger.warning("Could not open text input dialog for ${player.name}: ${e.message}")
            sendFallback(player, prompt)
        }
    }

    private fun sendFallback(player: Player, prompt: Prompt) {
        player.sendMessage(
            mm(
                "<yellow>${prompt.chatIntro}</yellow>" +
                        "<click:suggest_command:'/${prompt.commandPrefix}'><gold>[${prompt.headline}]</gold></click>"
            )
        )
    }
}
