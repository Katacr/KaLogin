package org.katacr.kalogin

import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.entity.Player
import java.time.Duration

class WelcomeManager(private val plugin: KaLogin) {

    private val pendingCallbacks = mutableMapOf<java.util.UUID, () -> Unit>()

    fun showWelcomeIfNeeded(player: Player, onAccepted: () -> Unit) {
        if (!plugin.config.getBoolean("welcome-dialog.enabled", true)) {
            onAccepted()
            return
        }

        plugin.dbManager.hasAcceptedTerms(player.uniqueId).thenAccept { accepted ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (!player.isOnline) return@Runnable
                if (accepted) {
                    onAccepted()
                } else {
                    showWelcomeDialog(player, null, onAccepted)
                }
            })
        }
    }

    fun showWelcomeDialog(player: Player, errorMessage: String? = null, onAccepted: (() -> Unit)? = null) {
        if (onAccepted != null) {
            pendingCallbacks[player.uniqueId] = onAccepted
        }
        val callback = pendingCallbacks[player.uniqueId] ?: {}
        plugin.antiCheatManager.setPlayerDialogType(player, "welcome")

        val confirmAction = DialogAction.customClick(
            DialogActionCallback { response, _ ->
                val accepted = response.getBoolean("welcome_accept_terms") ?: false
                if (!accepted) {
                    showWelcomeDialog(player, plugin.messageManager.getMessage("welcome.must-accept"), callback)
                    return@DialogActionCallback
                }

                plugin.dbManager.updateAcceptedTerms(player.uniqueId, true).thenAccept { success ->
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        if (!player.isOnline) return@Runnable
                        if (success) {
                            plugin.antiCheatManager.markProgrammaticClose(player)
                            player.closeInventory()
                            pendingCallbacks.remove(player.uniqueId)
                            callback()
                        } else {
                            showWelcomeDialog(player, plugin.messageManager.getMessage("welcome.save-failed"), callback)
                        }
                    })
                }
            },
            ClickCallback.Options.builder().lifetime(Duration.ofMinutes(10)).build()
        )

        val confirmButton = ActionButton.builder(plugin.messageManager.getComponent("welcome.confirm-button"))
            .action(confirmAction)
            .build()

        val dialog = LoginUI.buildWelcomeDialog(
            player,
            plugin.messageManager.getComponent("welcome.dialog-title"),
            errorMessage?.let { plugin.messageManager.getComponentFromMessage(it) },
            confirmButton
        )

        plugin.antiCheatManager.markDialogOpened(player)
        player.showDialog(dialog)
    }
}