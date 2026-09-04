package org.katacr.kalogin.dialog.spigot

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.dialog.ConfirmationDialog
import net.md_5.bungee.api.dialog.Dialog
import net.md_5.bungee.api.dialog.DialogBase
import net.md_5.bungee.api.dialog.MultiActionDialog
import net.md_5.bungee.api.dialog.NoticeDialog
import net.md_5.bungee.api.dialog.action.ActionButton
import net.md_5.bungee.api.dialog.action.CustomClickAction
import net.md_5.bungee.api.dialog.body.DialogBody
import net.md_5.bungee.api.dialog.body.PlainMessageBody
import net.md_5.bungee.api.dialog.input.BooleanInput
import net.md_5.bungee.api.dialog.input.DialogInput
import net.md_5.bungee.api.dialog.input.TextInput
import org.bukkit.NamespacedKey
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCustomClickEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.katacr.kalogin.DialogBodyElement
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Spigot 1.21.6+ Bungee Dialog 实现。
 */
class SpigotLoginDialogPlatform : LoginDialogPlatform, Listener {
    override val platformName: String = "Spigot"
    override val supportsToast: Boolean = false
    private lateinit var plugin: KaLogin
    private val callbacks = ConcurrentHashMap<String, CallbackSession>()
    private val playerCallbacks = ConcurrentHashMap<UUID, MutableSet<String>>()
    private val itemMapper = SpigotPublicItemMapper()
    private val reportedItemMappingFailures = ConcurrentHashMap.newKeySet<String>()
    private val legacySerializer = LegacyComponentSerializer.legacySection()

    private data class CallbackSession(
        val playerId: UUID,
        val inputKeys: Set<String>,
        val handler: (Map<String, String>) -> Unit
    )

    override fun initialize(plugin: KaLogin) {
        this.plugin = plugin
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    override fun showLogin(
        player: Player,
        title: Component,
        description: Component?,
        error: Component?,
        onSubmit: (LoginResponse) -> Unit
    ) = runPlayer(player) {
        val inputs = mutableListOf<DialogInput>(
            textInput("login_password", plugin.messageManager.getComponent("login.password-input"), "inputs.login.login_password", plugin.config.getInt("settings.max-password-length", 20))
        )
        if (plugin.config.getBoolean("login.show-auto-login-checkbox", true)) {
            inputs.add(BooleanInput("auto_login_by_ip", text(plugin.messageManager.getComponent("login.auto-login-checkbox")), LoginUI.boolInitial("inputs.login.auto_login_by_ip"), "true", "false"))
        }
        val action = registerCallback(player, inputKeys(inputs)) {
            onSubmit(LoginResponse(it["login_password"], parseBooleanInput(it["auto_login_by_ip"])))
        }
        player.showDialog(noticeDialog(title, body("login", player, description, error), inputs, button(plugin.messageManager.getComponent("login.dialog-button"), action), authDialog = true))
    }

    override fun showRegister(
        player: Player,
        title: Component,
        description: Component?,
        error: Component?,
        onSubmit: (RegisterResponse) -> Unit
    ) = runPlayer(player) {
        val maxLength = plugin.config.getInt("settings.max-password-length", 20)
        val inputs = listOf(
            textInput("reg_password", plugin.messageManager.getComponent("register.password-input"), "inputs.register.reg_password", maxLength),
            textInput("reg_confirm_password", plugin.messageManager.getComponent("register.confirm-password-input"), "inputs.register.reg_confirm_password", maxLength)
        )
        val action = registerCallback(player, inputKeys(inputs)) {
            onSubmit(RegisterResponse(it["reg_password"], it["reg_confirm_password"]))
        }
        player.showDialog(noticeDialog(title, body("register", player, description, error), inputs, button(plugin.messageManager.getComponent("register.dialog-button"), action), authDialog = true))
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
        val inputs = listOf(
            textInput("old_password", plugin.messageManager.getComponent("change-password.old-password-input"), "inputs.change-password.old_password", maxLength),
            textInput("new_password", plugin.messageManager.getComponent("change-password.new-password-input"), "inputs.change-password.new_password", maxLength),
            textInput("confirm_new_password", plugin.messageManager.getComponent("change-password.confirm-new-password-input"), "inputs.change-password.confirm_new_password", maxLength)
        )
        val confirm = registerCallback(player, inputKeys(inputs)) {
            onSubmit(ChangePasswordResponse(it["old_password"], it["new_password"], it["confirm_new_password"]))
        }
        val cancel = registerCallback(player, emptySet()) { onCancel() }
        player.showDialog(confirmationDialog(title, body("change-password", player, description, error), inputs, button(plugin.messageManager.getComponent("change-password.dialog-button"), confirm), button(plugin.messageManager.getComponent("change-password.cancel-button"), cancel)))
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
        val confirm = registerCallback(player, inputKeys(inputs)) {
            onSubmit(BindEmailResponse(it["bind_email"], it["bind_code"]))
        }
        val cancel = registerCallback(player, emptySet()) { onCancel() }
        player.showDialog(confirmationDialog(title, simpleBody(description, error), inputs, button(confirmLabel, confirm), button(cancelLabel, cancel)))
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
        val confirm = registerCallback(player, inputKeys(inputs)) {
            onSubmit(RecoverPasswordResponse(it["recover_code"], it["recover_new_password"], it["recover_confirm_new_password"]))
        }
        val cancel = registerCallback(player, emptySet()) { onCancel() }
        player.showDialog(confirmationDialog(title, simpleBody(description, error), inputs, button(confirmLabel, confirm), button(cancelLabel, cancel)))
    }

    override fun showWelcome(player: Player, title: Component, error: Component?, onSubmit: (WelcomeResponse) -> Unit) =
        runPlayer(player) {
            val inputs = listOf(
                BooleanInput("welcome_accept_terms", text(plugin.messageManager.getComponent("welcome.accept-checkbox")), LoginUI.boolInitial("inputs.welcome.accept_terms"), "true", "false")
            )
            val confirm = registerCallback(player, inputKeys(inputs)) {
                onSubmit(WelcomeResponse(parseBooleanInput(it["welcome_accept_terms"])))
            }
            player.showDialog(noticeDialog(title, body("welcome", player, null, error), inputs, button(plugin.messageManager.getComponent("welcome.confirm-button"), confirm), authDialog = true))
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
        val email = registerCallback(player, emptySet()) { onEmail() }
        val changePassword = registerCallback(player, emptySet()) { onChangePassword() }
        val close = registerCallback(player, emptySet()) { onClose() }
        player.showDialog(
            MultiActionDialog(
                base(title, body.map { plainMessage(it) }, emptyList(), canClose = true),
                listOf(button(emailButtonLabel, email), button(changePasswordButtonLabel, changePassword)),
                2,
                button(closeButtonLabel, close)
            )
        )
    }

    override fun showLoading(player: Player, title: Component, body: List<Component>) =
        runPlayer(player) {
            val bodyList = body.map { plainMessage(it) }
            player.showDialog(
                NoticeDialog(
                    base(title, bodyList, emptyList(), canClose = false),
                    ActionButton(text(Component.empty()), null, 150, CustomClickAction("kalogin:loading_noop"))
                )
            )
        }

    override fun close(player: Player) {
        runPlayer(player) {
            clearCallbacks(player.uniqueId)
            player.clearDialog()
        }
    }

    override fun sendMessage(sender: CommandSender, message: Component) {
        sender.spigot().sendMessage(*BungeeComponentSerializer.get().serialize(message))
    }

    override fun kick(player: Player, message: Component) {
        val legacyMessage = BaseComponent.toLegacyText(*BungeeComponentSerializer.get().serialize(message))
        player.kickPlayer(legacyMessage)
    }

    override fun shutdown() {
        callbacks.clear()
        playerCallbacks.clear()
    }

    @EventHandler
    fun onCustomClick(event: PlayerCustomClickEvent) {
        val key = event.id.toString()
        val session = callbacks[key] ?: return
        if (session.playerId != event.player.uniqueId) return
        if (!callbacks.remove(key, session)) return
        playerCallbacks[session.playerId]?.remove(key)
        session.handler(captureInputs(event.data, session.inputKeys))
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        clearCallbacks(event.player.uniqueId)
    }

    private fun runPlayer(player: Player, action: () -> Unit) {
        if (KaLoginScheduler.isPlayerThread(player)) action() else KaLoginScheduler.runPlayer(player, Runnable(action))
    }

    private fun noticeDialog(title: Component, body: List<DialogBody>, inputs: List<DialogInput>, button: ActionButton, authDialog: Boolean): NoticeDialog =
        NoticeDialog(base(title, body, inputs, canClose = !authDialog), button)

    private fun confirmationDialog(title: Component, body: List<DialogBody>, inputs: List<DialogInput>, confirm: ActionButton, cancel: ActionButton): ConfirmationDialog =
        ConfirmationDialog(base(title, body, inputs, canClose = true), confirm, cancel)

    private fun base(title: Component, body: List<DialogBody>, inputs: List<DialogInput>, canClose: Boolean): DialogBase =
        DialogBase(text(title))
            .body(body)
            .inputs(inputs)
            .canCloseWithEscape(canClose)
            .pause(false)
            .afterAction(DialogBase.AfterAction.NONE)

    private fun body(fileName: String, player: Player, description: Component?, error: Component?): List<DialogBody> =
        buildList {
            LoginUI.bodyElements(player, fileName).forEach { element ->
                when (element) {
                    is DialogBodyElement.Message -> add(plainMessage(element.text, element.width))
                    is DialogBodyElement.Item -> addItemBody(fileName, element, player)
                }
            }
            description?.let { add(plainMessage(it)) }
            error?.let { add(plainMessage(it)) }
        }

    private fun MutableList<DialogBody>.addItemBody(fileName: String, element: DialogBodyElement.Item, player: Player) {
        val itemStack = ItemStack(element.material, element.amount)
        val meta = itemStack.itemMeta
        if (meta != null) {
            val name = element.name
            if (!name.isNullOrEmpty()) {
                meta.setDisplayName(legacySerializer.serialize(LoginUI.parseText(name, player)))
            }
            if (element.lore.isNotEmpty()) {
                meta.lore = element.lore.map { legacySerializer.serialize(LoginUI.parseText(it, player)) }
            }
            val itemModel = element.itemModel
            if (!itemModel.isNullOrEmpty()) {
                val key = NamespacedKey.fromString(itemModel)
                if (key != null) meta.setItemModel(key) else plugin.logger.warning("Invalid item_model: $itemModel")
            }
            element.customModelData?.let { meta.setCustomModelData(it) }
            itemStack.itemMeta = meta
        }

        val descriptionBody = if (element.description.isNotEmpty()) {
            plainMessage(LoginUI.parseText(element.description.joinToString("\n"), player), element.descriptionWidth)
        } else {
            null
        }

        add(
            SpigotItemDialogBody(
                mapItem(fileName, element, itemStack),
                descriptionBody,
                element.showOverlays,
                element.showTooltip,
                element.width,
                element.height
            )
        )
    }

    /** 使用 Bukkit 公共 API 映射物品；单个属性异常时回退到基础物品。 */
    private fun mapItem(fileName: String, element: DialogBodyElement.Item, itemStack: ItemStack): JsonObject =
        try {
            itemMapper.map(itemStack)
        } catch (error: RuntimeException) {
            warnItemMappingOnce(fileName, element, error.message)
            SpigotItemDialogBody.basicItem(itemStack.type.key.toString(), itemStack.amount)
        }

    /** 同一 UI 物品的公共属性映射错误只记录一次，避免登录界面反复刷屏。 */
    private fun warnItemMappingOnce(fileName: String, element: DialogBodyElement.Item, error: String?) {
        val key = "$fileName:${element.material.key}:${element.itemModel}:${element.customModelData}"
        if (reportedItemMappingFailures.add(key)) {
            plugin.logger.warning(
                "Spigot Dialog item mapping failed for $fileName ${element.material.key}" +
                    (error?.let { ": $it" } ?: "")
            )
        }
    }

    private fun simpleBody(description: Component?, error: Component?): List<DialogBody> = buildList {
        description?.let { add(plainMessage(it)) }
        error?.let { add(plainMessage(it)) }
    }

    private fun button(label: Component, callbackId: String): ActionButton =
        ActionButton(text(label), null, 150, CustomClickAction(callbackId))

    private fun textInput(key: String, label: Component, sectionPath: String, maxLength: Int): TextInput =
        textInput(LoginUI.textInput(key, label, sectionPath, maxLength))

    private fun textInput(spec: TextInputSpec): TextInput =
        TextInput(spec.key, spec.width, text(spec.label), spec.labelVisible, spec.initial, spec.maxLength).apply {
            if (spec.height > 0) multiline(TextInput.Multiline(1, spec.height))
        }

    private fun plainMessage(component: Component, width: Int = -1): PlainMessageBody =
        if (width in 1..1024) PlainMessageBody(text(component), width) else PlainMessageBody(text(component))

    private fun text(component: Component): BaseComponent =
        TextComponent(*BungeeComponentSerializer.get().serialize(component))

    private fun registerCallback(player: Player, inputKeys: Set<String>, handler: (Map<String, String>) -> Unit): String {
        val key = "kalogin:dialog_${UUID.randomUUID().toString().replace("-", "")}"
        callbacks[key] = CallbackSession(player.uniqueId, inputKeys, handler)
        playerCallbacks.computeIfAbsent(player.uniqueId) { ConcurrentHashMap.newKeySet() }.add(key)
        return key
    }

    private fun inputKeys(inputs: List<DialogInput>): Set<String> =
        inputs.map { it.key() }.toSet()

    private fun captureInputs(payload: JsonElement?, inputKeys: Set<String>): Map<String, String> {
        val objectValue = if (payload?.isJsonObject == true) payload.asJsonObject else return emptyMap()
        return buildMap {
            inputKeys.forEach { key ->
                val element = objectValue.get(key)
                if (element?.isJsonPrimitive == true) put(key, element.asString)
            }
        }
    }

    /** 兼容 Spigot 不同版本将布尔输入编码为字符串或 NBT 数字的情况。 */
    private fun parseBooleanInput(value: String?): Boolean = when (value?.trim()?.lowercase()) {
        "true", "1", "yes", "on" -> true
        else -> false
    }

    private fun clearCallbacks(playerId: UUID) {
        playerCallbacks.remove(playerId)?.forEach(callbacks::remove)
    }
}
