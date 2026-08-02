package org.katacr.kalogin

import org.bukkit.entity.Player
import org.katacr.kalogin.dialog.WelcomeResponse

class WelcomeManager(private val plugin: KaLogin) {

    private val pendingCallbacks = mutableMapOf<java.util.UUID, () -> Unit>()

    fun showWelcomeIfNeeded(player: Player, onAccepted: () -> Unit) {
        if (!plugin.config.getBoolean("welcome-dialog.enabled", true)) {
            onAccepted()
            return
        }

        plugin.dbManager.hasAcceptedTerms(player.uniqueId).thenAccept { accepted ->
            KaLoginScheduler.runPlayer(player, Runnable {
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

        val confirmAction: (WelcomeResponse) -> Unit = action@{ response ->
            if (!response.acceptedTerms) {
                showWelcomeDialog(player, plugin.messageManager.getMessage("welcome.must-accept"), callback)
                return@action
            }

            plugin.dbManager.updateAcceptedTerms(player.uniqueId, true).thenAccept { success ->
                KaLoginScheduler.runPlayer(player, Runnable {
                    if (!player.isOnline) return@Runnable
                    if (success) {
                        plugin.antiCheatManager.markProgrammaticClose(player)
                        plugin.dialogPlatform.close(player)
                        pendingCallbacks.remove(player.uniqueId)
                        callback()
                    } else {
                        showWelcomeDialog(player, plugin.messageManager.getMessage("welcome.save-failed"), callback)
                    }
                })
            }
        }

        plugin.antiCheatManager.markDialogOpened(player)
        plugin.dialogPlatform.showWelcome(
            player,
            plugin.messageManager.getComponent("welcome.dialog-title"),
            plugin.resolveDialogErrorComponent(player, errorMessage),
            confirmAction
        )
    }
}
