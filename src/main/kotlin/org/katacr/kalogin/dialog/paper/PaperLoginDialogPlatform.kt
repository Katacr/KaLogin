@file:Suppress("UnstableApiUsage")

package org.katacr.kalogin.dialog.paper

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.dialog.DialogResponseView
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.input.TextDialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.katacr.kalogin.DialogBodyElement
import org.katacr.kalogin.GeyserCompat
import org.katacr.kalogin.KaLogin
import org.katacr.kalogin.KaLoginScheduler
import org.katacr.kalogin.LoginUI
import org.katacr.kalogin.TextInputSpec
import org.katacr.kalogin.dialog.BindEmailResponse
import org.katacr.kalogin.dialog.ChangePasswordResponse
import org.katacr.kalogin.dialog.LoginDialogPlatform
import org.katacr.kalogin.dialog.LoginResponse
import org.katacr.kalogin.dialog.RecoverPasswordResponse
import org.katacr.kalogin.dialog.RegisterResponse
import org.katacr.kalogin.dialog.WelcomeResponse
import java.time.Duration

/**
 * Paper/Folia 原生 Dialog 实现。
 */
class PaperLoginDialogPlatform : LoginDialogPlatform {
    override val platformName: String = "Paper"
    private lateinit var plugin: KaLogin

    override fun initialize(plugin: KaLogin) {
        this.plugin = plugin
    }

    override fun showLogin(
        player: Player,
        title: Component,
        description: Component?,
        error: Component?,
        onSubmit: (LoginResponse) -> Unit
    ) = runPlayer(player) {
        val action = callback {
            onSubmit(LoginResponse(it.getText("login_password"), it.getBoolean("auto_login_by_ip") ?: false))
        }
        val button = ActionButton.builder(plugin.messageManager.getComponent("login.dialog-button")).action(action).build()
        val dialog = noticeDialog(
            player,
            title,
            body("login", player, description, error),
            listOf(
                textInput("login_password", plugin.messageManager.getComponent("login.password-input"), "inputs.login.login_password", plugin.config.getInt("settings.max-password-length", 20))
            ) + if (plugin.config.getBoolean("login.show-auto-login-checkbox", true)) {
                listOf(
                    DialogInput.bool("auto_login_by_ip", plugin.messageManager.getComponent("login.auto-login-checkbox"))
                        .initial(LoginUI.boolInitial("inputs.login.auto_login_by_ip"))
                        .build()
                )
            } else {
                emptyList()
            },
            button,
            authDialog = true
        )
        player.showDialog(dialog)
    }

    override fun showRegister(
        player: Player,
        title: Component,
        description: Component?,
        error: Component?,
        onSubmit: (RegisterResponse) -> Unit
    ) = runPlayer(player) {
        val action = callback {
            onSubmit(RegisterResponse(it.getText("reg_password"), it.getText("reg_confirm_password")))
        }
        val button = ActionButton.builder(plugin.messageManager.getComponent("register.dialog-button")).action(action).build()
        val maxLength = plugin.config.getInt("settings.max-password-length", 20)
        player.showDialog(
            noticeDialog(
                player,
                title,
                body("register", player, description, error),
                listOf(
                    textInput("reg_password", plugin.messageManager.getComponent("register.password-input"), "inputs.register.reg_password", maxLength),
                    textInput("reg_confirm_password", plugin.messageManager.getComponent("register.confirm-password-input"), "inputs.register.reg_confirm_password", maxLength)
                ),
                button,
                authDialog = true
            )
        )
    }

    override fun showChangePassword(
        player: Player,
        title: Component,
        description: Component?,
        error: Component?,
        onSubmit: (ChangePasswordResponse) -> Unit,
        onCancel: () -> Unit
    ) = runPlayer(player) {
        val maxLength = plugin.config.getInt("settings.max-password-length", 20)
        val confirmButton = ActionButton.builder(plugin.messageManager.getComponent("change-password.dialog-button"))
            .action(callback {
                onSubmit(
                    ChangePasswordResponse(
                        it.getText("old_password"),
                        it.getText("new_password"),
                        it.getText("confirm_new_password")
                    )
                )
            })
            .build()
        val cancelButton = ActionButton.builder(plugin.messageManager.getComponent("change-password.cancel-button"))
            .action(callback { onCancel() })
            .build()

        player.showDialog(
            confirmationDialog(
                title,
                body("change-password", player, description, error),
                listOf(
                    textInput("old_password", plugin.messageManager.getComponent("change-password.old-password-input"), "inputs.change-password.old_password", maxLength),
                    textInput("new_password", plugin.messageManager.getComponent("change-password.new-password-input"), "inputs.change-password.new_password", maxLength),
                    textInput("confirm_new_password", plugin.messageManager.getComponent("change-password.confirm-new-password-input"), "inputs.change-password.confirm_new_password", maxLength)
                ),
                confirmButton,
                cancelButton
            )
        )
    }

    override fun showBindEmail(
        player: Player,
        title: Component,
        description: Component?,
        error: Component?,
        showEmailInput: Boolean,
        showCodeInput: Boolean,
        confirmLabel: Component,
        cancelLabel: Component,
        onSubmit: (BindEmailResponse) -> Unit,
        onCancel: () -> Unit
    ) = runPlayer(player) {
        val inputs = buildList {
            if (showEmailInput) add(textInput("bind_email", plugin.messageManager.getComponent("bind-email.email-input"), "inputs.bind-email.email", 255))
            if (showCodeInput) add(textInput("bind_code", plugin.messageManager.getComponent("bind-email.code-input"), "inputs.bind-email.code", 16))
        }
        player.showDialog(
            confirmationDialog(
                title,
                simpleBody(description, error),
                inputs,
                ActionButton.builder(confirmLabel).action(callback {
                    onSubmit(BindEmailResponse(it.getText("bind_email"), it.getText("bind_code")))
                }).build(),
                ActionButton.builder(cancelLabel).action(callback { onCancel() }).build()
            )
        )
    }

    override fun showRecoverPassword(
        player: Player,
        title: Component,
        description: Component?,
        error: Component?,
        requireCode: Boolean,
        confirmLabel: Component,
        cancelLabel: Component,
        onSubmit: (RecoverPasswordResponse) -> Unit,
        onCancel: () -> Unit
    ) = runPlayer(player) {
        val maxLength = plugin.config.getInt("settings.max-password-length", 20)
        val inputs = if (requireCode) {
            listOf(
                textInput("recover_code", plugin.messageManager.getComponent("recover-password.code-input"), "inputs.recover-password.code", 16),
                textInput("recover_new_password", plugin.messageManager.getComponent("recover-password.new-password-input"), "inputs.recover-password.new_password", maxLength),
                textInput("recover_confirm_new_password", plugin.messageManager.getComponent("recover-password.confirm-new-password-input"), "inputs.recover-password.confirm_new_password", maxLength)
            )
        } else {
            emptyList()
        }
        player.showDialog(
            confirmationDialog(
                title,
                simpleBody(description, error),
                inputs,
                ActionButton.builder(confirmLabel).action(callback {
                    onSubmit(
                        RecoverPasswordResponse(
                            it.getText("recover_code"),
                            it.getText("recover_new_password"),
                            it.getText("recover_confirm_new_password")
                        )
                    )
                }).build(),
                ActionButton.builder(cancelLabel).action(callback { onCancel() }).build()
            )
        )
    }

    override fun showWelcome(player: Player, title: Component, error: Component?, onSubmit: (WelcomeResponse) -> Unit) =
        runPlayer(player) {
            val button = ActionButton.builder(plugin.messageManager.getComponent("welcome.confirm-button"))
                .action(callback { onSubmit(WelcomeResponse(it.getBoolean("welcome_accept_terms") ?: false)) })
                .build()
            player.showDialog(
                noticeDialog(
                    player,
                    title,
                    body("welcome", player, null, error),
                    listOf(
                        DialogInput.bool("welcome_accept_terms", plugin.messageManager.getComponent("welcome.accept-checkbox"))
                            .initial(LoginUI.boolInitial("inputs.welcome.accept_terms"))
                            .build()
                    ),
                    button,
                    authDialog = true
                )
            )
        }

    override fun showUserCenter(
        player: Player,
        title: Component,
        body: List<Component>,
        emailButtonLabel: Component,
        changePasswordButtonLabel: Component,
        closeButtonLabel: Component,
        onEmail: () -> Unit,
        onChangePassword: () -> Unit,
        onClose: () -> Unit
    ) = runPlayer(player) {
        val bodyList = body.map { DialogBody.plainMessage(it) }
        val emailButton = ActionButton.builder(emailButtonLabel).action(callback { onEmail() }).build()
        val changePasswordButton = ActionButton.builder(changePasswordButtonLabel).action(callback { onChangePassword() }).build()
        val closeButton = ActionButton.builder(closeButtonLabel).action(callback { onClose() }).build()
        player.showDialog(
            Dialog.create { builder ->
                builder.empty()
                    .base(
                        DialogBase.builder(title)
                            .pause(false)
                            .body(bodyList)
                            .canCloseWithEscape(true)
                            .afterAction(DialogBase.DialogAfterAction.CLOSE)
                            .build()
                    )
                    .type(DialogType.multiAction(listOf(emailButton, changePasswordButton)).columns(2).exitAction(closeButton).build())
            }
        )
    }

    override fun close(player: Player) {
        runPlayer(player) { player.closeDialog() }
    }

    override fun shutdown() = Unit

    private fun runPlayer(player: Player, action: () -> Unit) {
        if (KaLoginScheduler.isPlayerThread(player)) action() else KaLoginScheduler.runPlayer(player, Runnable(action))
    }

    private fun callback(action: (DialogResponseView) -> Unit): DialogAction =
        DialogAction.customClick(
            DialogActionCallback { response, _ -> action(response) },
            ClickCallback.Options.builder().lifetime(Duration.ofMinutes(5)).build()
        )

    private fun noticeDialog(
        player: Player,
        title: Component,
        body: List<DialogBody>,
        inputs: List<DialogInput>,
        button: ActionButton,
        authDialog: Boolean
    ): Dialog {
        val closeable = authDialog && GeyserCompat.isBedrockPlayer(player)
        val afterAction = if (closeable) DialogBase.DialogAfterAction.CLOSE else DialogBase.DialogAfterAction.NONE
        return Dialog.create { builder ->
            builder.empty()
                .base(
                    DialogBase.builder(title)
                        .pause(false)
                        .body(body)
                        .inputs(inputs)
                        .canCloseWithEscape(closeable || !authDialog)
                        .afterAction(afterAction)
                        .build()
                )
                .type(DialogType.notice(button))
        }
    }

    private fun confirmationDialog(
        title: Component,
        body: List<DialogBody>,
        inputs: List<DialogInput>,
        confirmButton: ActionButton,
        cancelButton: ActionButton
    ): Dialog = Dialog.create { builder ->
        builder.empty()
            .base(
                DialogBase.builder(title)
                    .pause(false)
                    .body(body)
                    .inputs(inputs)
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.NONE)
                    .build()
            )
            .type(DialogType.confirmation(confirmButton, cancelButton))
    }

    private fun simpleBody(description: Component?, error: Component?): List<DialogBody> = buildList {
        description?.let { add(DialogBody.plainMessage(it)) }
        error?.let { add(DialogBody.plainMessage(it)) }
    }

    private fun body(fileName: String, player: Player, description: Component?, error: Component?): List<DialogBody> =
        buildList {
            LoginUI.bodyElements(player, fileName).forEach { element ->
                when (element) {
                    is DialogBodyElement.Message -> add(
                        if (element.width > 0) DialogBody.plainMessage(element.text, element.width)
                        else DialogBody.plainMessage(element.text)
                    )
                    is DialogBodyElement.Item -> addItemBody(element, player)
                }
            }
            description?.let { add(DialogBody.plainMessage(it)) }
            error?.let { add(DialogBody.plainMessage(it)) }
    }

    private fun MutableList<DialogBody>.addItemBody(element: DialogBodyElement.Item, player: Player) {
        val itemStack = ItemStack(element.material, element.amount)
        itemStack.editMeta { meta ->
            if (!element.name.isNullOrEmpty()) meta.displayName(LoginUI.parseText(element.name, player))
            if (element.lore.isNotEmpty()) meta.lore(element.lore.map { LoginUI.parseText(it, player) })
            if (!element.itemModel.isNullOrEmpty()) {
                val key = NamespacedKey.fromString(element.itemModel)
                if (key != null) meta.itemModel = key else plugin.logger.warning("Invalid item_model: ${element.itemModel}")
            }
            element.customModelData?.let { meta.setCustomModelData(it) }
        }
        val descriptionBody = if (element.description.isNotEmpty()) {
            val component = LoginUI.parseText(element.description.joinToString("\n"), player)
            if (element.descriptionWidth > 0) DialogBody.plainMessage(component, element.descriptionWidth)
            else DialogBody.plainMessage(component)
        } else {
            null
        }
        DialogBody.item(itemStack)
            .description(descriptionBody)
            .showDecorations(element.showOverlays)
            .showTooltip(element.showTooltip)
            .width(element.width)
            .height(element.height)
            .build()
            .let { add(it) }
    }

    private fun textInput(key: String, label: Component, sectionPath: String, maxLength: Int): DialogInput {
        val spec = LoginUI.textInput(key, label, sectionPath, maxLength)
        return textInput(spec)
    }

    private fun textInput(spec: TextInputSpec): DialogInput {
        val builder = DialogInput.text(spec.key, spec.label)
            .width(spec.width)
            .labelVisible(spec.labelVisible)
            .initial(spec.initial)
            .maxLength(spec.maxLength)
        return if (spec.height > 0) builder.multiline(TextDialogInput.MultilineOptions.create(1, spec.height)).build()
        else builder.build()
    }
}
