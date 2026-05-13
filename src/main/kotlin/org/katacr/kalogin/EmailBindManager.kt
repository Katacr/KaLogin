@file:Suppress("UnstableApiUsage")

package org.katacr.kalogin

import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback
import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.regex.Pattern

class EmailBindManager(private val plugin: KaLogin) {

    private enum class EmailActionType {
        BIND,
        UNBIND,
        RECOVER_PASSWORD
    }

    private data class PendingCode(
        val type: EmailActionType,
        val email: String,
        val code: String,
        val expireAt: Long
    )

    private val pendingCodes = ConcurrentHashMap<UUID, PendingCode>()
    private val emailPattern = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun showPromptIfNeeded(player: Player) {
        if (!plugin.config.getBoolean("email-binding.enabled", true)) {
            return
        }
        plugin.dbManager.shouldShowBindEmailPrompt(player.uniqueId).thenAccept { shouldShow ->
            if (!shouldShow) return@thenAccept
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (!player.isOnline) return@Runnable
                player.sendMessage(LoginUI.parseClickableText(plugin.messageManager.getMessage("bind-email.prompt-message"), player))
            })
        }
    }

    fun disablePrompt(player: Player) {
        plugin.dbManager.updateBindEmailPrompt(player.uniqueId, false).thenAccept { success ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (success) {
                    player.sendMessage(plugin.messageManager.getComponent("bind-email.prompt-disabled"))
                } else {
                    player.sendMessage(plugin.messageManager.getComponent("bind-email.save-failed"))
                }
            })
        }
    }

    fun openBindDialog(player: Player, errorMessage: String? = null) {
        if (!plugin.config.getBoolean("email-binding.enabled", true)) {
            player.sendMessage(plugin.messageManager.getComponent("bind-email.disabled"))
            return
        }

        plugin.dbManager.getPlayerEmail(player.uniqueId).thenAccept { boundEmail ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (!player.isOnline) return@Runnable

                val pending = pendingCodes[player.uniqueId]?.takeIf {
                    it.type == EmailActionType.BIND || it.type == EmailActionType.UNBIND
                }
                val hasBoundEmail = !boundEmail.isNullOrBlank()
                val description = when {
                    pending?.type == EmailActionType.UNBIND -> plugin.messageManager.getComponent(
                        "bind-email.unbind-code-sent-description",
                        "email" to maskEmail(pending.email)
                    )
                    pending?.type == EmailActionType.BIND -> plugin.messageManager.getComponent(
                        "bind-email.code-sent-description",
                        "email" to maskEmail(pending.email)
                    )
                    hasBoundEmail -> plugin.messageManager.getComponent(
                        "bind-email.bound-description",
                        "email" to maskEmail(boundEmail)
                    )
                    else -> plugin.messageManager.getComponent("bind-email.description")
                }

                val action = DialogAction.customClick(
                    DialogActionCallback { response, _ ->
                        val currentPending = pendingCodes[player.uniqueId]?.takeIf {
                            it.type == EmailActionType.BIND || it.type == EmailActionType.UNBIND
                        }
                        when (currentPending) {
                            null if hasBoundEmail -> sendVerificationCode(player, boundEmail, EmailActionType.UNBIND)
                            null -> {
                                val email = response.getText("bind_email")?.trim().orEmpty()
                                if (!isValidEmail(email)) {
                                    openBindDialog(player, plugin.messageManager.getMessage("bind-email.invalid-email"))
                                    return@DialogActionCallback
                                }
                                sendVerificationCode(player, email, EmailActionType.BIND)
                            }
                            else -> {
                                val code = response.getText("bind_code")?.trim().orEmpty()
                                verifyPendingAction(player, code)
                            }
                        }
                    },
                    ClickCallback.Options.builder().lifetime(Duration.ofMinutes(10)).build()
                )

                val cancelAction = DialogAction.customClick(
                    { _, _ ->
                        player.closeInventory()
                    },
                    ClickCallback.Options.builder().lifetime(Duration.ofMinutes(10)).build()
                )

                val confirmButton = ActionButton.builder(
                    plugin.messageManager.getComponent(
                        when {
                            pending?.type == EmailActionType.UNBIND -> "bind-email.unbind-button"
                            pending?.type == EmailActionType.BIND -> "bind-email.verify-button"
                            hasBoundEmail -> "bind-email.send-unbind-code-button"
                            else -> "bind-email.send-code-button"
                        }
                    )
                ).action(action).build()

                val cancelButton = ActionButton.builder(
                    plugin.messageManager.getComponent("bind-email.cancel-button")
                ).action(cancelAction).build()

                val dialog = LoginUI.buildBindEmailDialog(
                    player,
                    plugin.messageManager.getComponent("bind-email.dialog-title"),
                    description,
                    errorMessage?.let { plugin.messageManager.getComponentFromMessage(it) },
                    confirmButton,
                    cancelButton,
                    showEmailInput = !hasBoundEmail && pending == null,
                    showCodeInput = pending != null
                )

                player.showDialog(dialog)
            })
        }
    }

    fun openRecoverPasswordDialog(player: Player, errorMessage: String? = null) {
        if (!plugin.config.getBoolean("email-binding.enabled", true)) {
            player.sendMessage(plugin.messageManager.getComponent("bind-email.disabled"))
            return
        }

        plugin.dbManager.getPlayerEmail(player.uniqueId).thenAccept { email ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (!player.isOnline) return@Runnable

                if (email.isNullOrBlank()) {
                    player.sendMessage(plugin.messageManager.getComponent("recover-password.no-bound-email"))
                    return@Runnable
                }

                val pending = pendingCodes[player.uniqueId]?.takeIf { it.type == EmailActionType.RECOVER_PASSWORD }
                val description = if (pending == null) {
                    plugin.messageManager.getComponent(
                        "recover-password.description",
                        "email" to maskEmail(email)
                    )
                } else {
                    plugin.messageManager.getComponent(
                        "recover-password.code-sent-description",
                        "email" to maskEmail(email)
                    )
                }

                val action = DialogAction.customClick(
                    DialogActionCallback { response, _ ->
                        val currentPending = pendingCodes[player.uniqueId]?.takeIf { it.type == EmailActionType.RECOVER_PASSWORD }
                        if (currentPending == null) {
                            sendVerificationCode(player, email, EmailActionType.RECOVER_PASSWORD)
                            return@DialogActionCallback
                        }

                        val code = response.getText("recover_code")?.trim().orEmpty()
                        val newPassword = response.getText("recover_new_password")?.trim().orEmpty()
                        val confirmNewPassword = response.getText("recover_confirm_new_password")?.trim().orEmpty()
                        verifyRecoverPassword(player, code, newPassword, confirmNewPassword)
                    },
                    ClickCallback.Options.builder().lifetime(Duration.ofMinutes(10)).build()
                )

                val cancelAction = DialogAction.customClick(
                    { _, _ ->
                        player.closeInventory()
                        plugin.showLoginDialogForPlayer(player)
                    },
                    ClickCallback.Options.builder().lifetime(Duration.ofMinutes(10)).build()
                )

                val dialog = LoginUI.buildRecoverPasswordDialog(
                    player,
                    plugin.messageManager.getComponent("recover-password.dialog-title"),
                    description,
                    errorMessage?.let { plugin.messageManager.getComponentFromMessage(it) },
                    ActionButton.builder(
                        plugin.messageManager.getComponent(
                            if (pending == null) "recover-password.send-code-button" else "recover-password.reset-button"
                        )
                    ).action(action).build(),
                    ActionButton.builder(plugin.messageManager.getComponent("recover-password.cancel-button"))
                        .action(cancelAction)
                        .build(),
                    requireCode = pending != null
                )

                player.showDialog(dialog)
            })
        }
    }

    private fun sendVerificationCode(player: Player, email: String, type: EmailActionType) {
        if (!plugin.config.getBoolean("email-binding.enabled", true)) {
            player.sendMessage(plugin.messageManager.getComponent("bind-email.disabled"))
            return
        }

        if (!isSmtpConfigured()) {
            player.sendMessage(plugin.messageManager.getComponent("bind-email.smtp-not-configured"))
            return
        }

        val code = generateCode()
        val expireSeconds = plugin.config.getLong("email-binding.code-expire-seconds", 300)

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val sendResult = runCatching {
                sendMail(email, code)
            }
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (!player.isOnline) return@Runnable
                if (sendResult.isSuccess) {
                    pendingCodes[player.uniqueId] = PendingCode(type, email, code, System.currentTimeMillis() + expireSeconds * 1000)
                    when (type) {
                        EmailActionType.BIND -> {
                            openBindDialog(player)
                            player.sendMessage(plugin.messageManager.getComponent("bind-email.code-sent", "email" to maskEmail(email)))
                        }
                        EmailActionType.UNBIND -> {
                            openBindDialog(player)
                            player.sendMessage(plugin.messageManager.getComponent("bind-email.unbind-code-sent", "email" to maskEmail(email)))
                        }
                        EmailActionType.RECOVER_PASSWORD -> {
                            openRecoverPasswordDialog(player)
                            player.sendMessage(plugin.messageManager.getComponent("recover-password.code-sent", "email" to maskEmail(email)))
                        }
                    }
                } else {
                    val ex = sendResult.exceptionOrNull()
                    plugin.logger.severe("[EmailBind] Failed to send verification email to $email for player=${player.name}: ${ex?.javaClass?.name}: ${ex?.message}")
                    ex?.printStackTrace()
                    val key = if (type == EmailActionType.RECOVER_PASSWORD) "recover-password.send-failed" else "bind-email.send-failed"
                    player.sendMessage(plugin.messageManager.getComponent(key))
                }
            })
        })
    }

    private fun verifyPendingAction(player: Player, code: String) {
        val pending = pendingCodes[player.uniqueId]
        if (pending == null) {
            openBindDialog(player, plugin.messageManager.getMessage("bind-email.code-not-sent"))
            return
        }

        if (System.currentTimeMillis() > pending.expireAt) {
            pendingCodes.remove(player.uniqueId)
            openBindDialog(player, plugin.messageManager.getMessage("bind-email.code-expired"))
            return
        }

        if (code != pending.code) {
            openBindDialog(player, plugin.messageManager.getMessage("bind-email.code-invalid"))
            return
        }

        val actionFuture = when (pending.type) {
            EmailActionType.BIND -> plugin.dbManager.bindEmail(player.uniqueId, pending.email)
            EmailActionType.UNBIND -> plugin.dbManager.unbindEmail(player.uniqueId)
            EmailActionType.RECOVER_PASSWORD -> return
        }

        actionFuture.thenAccept { success ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (success) {
                    pendingCodes.remove(player.uniqueId)
                    if (pending.type == EmailActionType.BIND) {
                        player.sendMessage(plugin.messageManager.getComponent("bind-email.bind-success", "email" to pending.email))
                    } else {
                        player.sendMessage(plugin.messageManager.getComponent("bind-email.unbind-success"))
                    }
                } else {
                    player.sendMessage(plugin.messageManager.getComponent("bind-email.save-failed"))
                }
            })
        }
    }

    private fun verifyRecoverPassword(player: Player, code: String, newPassword: String, confirmNewPassword: String) {
        val pending = pendingCodes[player.uniqueId]?.takeIf { it.type == EmailActionType.RECOVER_PASSWORD }
        if (pending == null) {
            openRecoverPasswordDialog(player, plugin.messageManager.getMessage("recover-password.code-not-sent"))
            return
        }

        if (System.currentTimeMillis() > pending.expireAt) {
            pendingCodes.remove(player.uniqueId)
            openRecoverPasswordDialog(player, plugin.messageManager.getMessage("recover-password.code-expired"))
            return
        }

        if (code != pending.code) {
            openRecoverPasswordDialog(player, plugin.messageManager.getMessage("recover-password.code-invalid"))
            return
        }

        if (newPassword.isBlank()) {
            openRecoverPasswordDialog(player, plugin.messageManager.getMessage("recover-password.new-password-empty"))
            return
        }

        val validationError = PasswordValidator(plugin).validate(newPassword)
        if (validationError != null) {
            openRecoverPasswordDialog(player, plugin.messageManager.getMessage("recover-password.new-password-invalid", "error" to validationError))
            return
        }

        if (newPassword != confirmNewPassword) {
            openRecoverPasswordDialog(player, plugin.messageManager.getMessage("recover-password.password-mismatch"))
            return
        }

        player.sendMessage(plugin.messageManager.getComponent("recover-password.saving"))
        val task = if (plugin.authMeManager.useAuthMe) {
            java.util.concurrent.CompletableFuture.supplyAsync {
                runCatching {
                    plugin.authMeManager.changePassword(player.name, newPassword)
                    true
                }.getOrDefault(false)
            }
        } else {
            plugin.dbManager.setPassword(player.uniqueId, newPassword)
        }

        task.thenAccept { success ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (success) {
                    pendingCodes.remove(player.uniqueId)
                    player.sendMessage(plugin.messageManager.getComponent("recover-password.success"))
                } else {
                    player.sendMessage(plugin.messageManager.getComponent("recover-password.failed"))
                }
            })
        }
    }

    private fun isValidEmail(email: String): Boolean = emailPattern.matcher(email).matches()

    private fun generateCode(): String = (1..6)
        .map { ThreadLocalRandom.current().nextInt(10) }
        .joinToString("")

    private fun maskEmail(email: String): String {
        val parts = email.split("@", limit = 2)
        if (parts.size != 2) return email
        val name = parts[0]
        val masked = when {
            name.length <= 2 -> name.first() + "***"
            else -> name.take(2) + "***"
        }
        return "$masked@${parts[1]}"
    }

    private fun isSmtpConfigured(): Boolean {
        return !plugin.config.getString("email-binding.smtp.host").isNullOrBlank() &&
            !plugin.config.getString("email-binding.smtp.username").isNullOrBlank() &&
            !plugin.config.getString("email-binding.smtp.password").isNullOrBlank() &&
            !plugin.config.getString("email-binding.smtp.from-email").isNullOrBlank()
    }

    private fun sendMail(targetEmail: String, code: String) {
        val smtp = plugin.config.getConfigurationSection("email-binding.smtp") ?: error("smtp config missing")
        val port = smtp.getInt("port", 587)
        val configuredStarttls = smtp.getBoolean("starttls", true)
        val configuredSsl = smtp.getBoolean("ssl", false)
        val (starttlsEnabled, sslEnabled) = resolveSecurityMode(port, configuredStarttls, configuredSsl)

        val props = Properties().apply {
            put("mail.smtp.host", smtp.getString("host", ""))
            put("mail.smtp.port", port.toString())
            put("mail.smtp.auth", smtp.getBoolean("auth", true).toString())
            put("mail.smtp.starttls.enable", starttlsEnabled.toString())
            put("mail.smtp.ssl.enable", sslEnabled.toString())
            put("mail.smtp.connectiontimeout", "10000")
            put("mail.smtp.timeout", "10000")
        }

        val username = smtp.getString("username", "")!!
        val password = smtp.getString("password", "")!!
        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(username, password)
            }
        })

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(smtp.getString("from-email", username), smtp.getString("from-name", "KaLogin")))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(targetEmail))
            subject = plugin.messageManager.getMessage("email-verification.mail-subject")
            setText(plugin.messageManager.getMessage("email-verification.mail-body", "code" to code), "UTF-8")
        }

        Transport.send(message)
    }

    private fun resolveSecurityMode(port: Int, starttls: Boolean, ssl: Boolean): Pair<Boolean, Boolean> {
        if (starttls && ssl) {
            return if (port == 465) {
                plugin.logger.warning("[EmailBind] Both STARTTLS and SSL are enabled. Port 465 usually uses implicit SSL, so STARTTLS will be disabled.")
                false to true
            } else {
                plugin.logger.warning("[EmailBind] Both STARTTLS and SSL are enabled. Port $port usually expects STARTTLS instead of implicit SSL, so SSL will be disabled.")
                true to false
            }
        }
        return starttls to ssl
    }
}

class BindEmailCommand(private val plugin: KaLogin) : org.bukkit.command.CommandExecutor, TabCompleter {
    override fun onCommand(
        sender: org.bukkit.command.CommandSender,
        command: org.bukkit.command.Command,
        label: String,
        args: Array<String>
    ): Boolean {
        if (sender !is Player) {
            plugin.messageManager.sendMessage(sender, "authme.player-only")
            return true
        }

        if (args.isNotEmpty() && args[0].equals("dismiss", ignoreCase = true)) {
            plugin.emailBindManager.disablePrompt(sender)
            return true
        }

        plugin.emailBindManager.openBindDialog(sender)
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>
    ): List<String> {
        if (!sender.hasPermission("kalogin.bindemail")) {
            return emptyList()
        }
        return when (args.size) {
            1 -> listOf("dismiss").filter { it.startsWith(args[0], ignoreCase = true) }
            else -> emptyList()
        }
    }
}

class RecoverPasswordCommand(private val plugin: KaLogin) : org.bukkit.command.CommandExecutor, TabCompleter {
    override fun onCommand(
        sender: org.bukkit.command.CommandSender,
        command: org.bukkit.command.Command,
        label: String,
        args: Array<String>
    ): Boolean {
        if (sender !is Player) {
            plugin.messageManager.sendMessage(sender, "authme.player-only")
            return true
        }

        val alreadyLoggedIn = if (plugin.authMeManager.useAuthMe) {
            plugin.authMeManager.isAuthenticated(sender)
        } else {
            plugin.loginListener.isLoggedIn(sender.uniqueId)
        }
        if (alreadyLoggedIn) {
            plugin.messageManager.sendMessage(sender, "recover-password.already-logged-in")
            return true
        }

        plugin.emailBindManager.openRecoverPasswordDialog(sender)
        return true
    }
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>
    ): List<String> = emptyList()

}