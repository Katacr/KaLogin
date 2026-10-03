package org.katacr.kalogin.dialog

import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.katacr.kalogin.KaLogin

/**
 * 隔离 Paper/Folia 与 Spigot 的原生 Dialog API。
 *
 * 业务监听器只传入标题、正文和回调，避免共享代码直接引用平台专属 Dialog 类型。
 */
interface LoginDialogPlatform {
    val platformName: String

    /** 当前平台是否支持原生 Toast 通知。 */
    val supportsToast: Boolean

    fun initialize(plugin: KaLogin)

    fun showLogin(
        player: Player,
        title: Component,
        description: Component?,
        error: Component?,
        onSubmit: (LoginResponse) -> Unit
    )

    fun showRegister(
        player: Player,
        title: Component,
        description: Component?,
        error: Component?,
        onSubmit: (RegisterResponse) -> Unit
    )

    fun showChangePassword(
        player: Player,
        title: Component,
        description: Component?,
        error: Component?,
        onSubmit: (ChangePasswordResponse) -> Unit,
        onCancel: () -> Unit
    )

    fun showBindEmail(
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
    )

    fun showRecoverPassword(
        player: Player,
        title: Component,
        description: Component?,
        error: Component?,
        requireCode: Boolean,
        confirmLabel: Component,
        cancelLabel: Component,
        onSubmit: (RecoverPasswordResponse) -> Unit,
        onCancel: () -> Unit
    )

    fun showWelcome(
        player: Player,
        title: Component,
        error: Component?,
        onSubmit: (WelcomeResponse) -> Unit
    )

    /**
     * 显示"是否返回上次位置"确认对话框（确定/取消，无输入项）。
     * 用于手动模式下登录完成后询问玩家。
     */
    fun showLastSeen(
        player: Player,
        title: Component,
        body: List<Component>,
        confirmLabel: Component,
        cancelLabel: Component,
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    )

    fun showUserCenter(
        player: Player,
        title: Component,
        body: List<Component>,
        emailButtonLabel: Component,
        changePasswordButtonLabel: Component,
        closeButtonLabel: Component,
        onEmail: () -> Unit,
        onChangePassword: () -> Unit,
        onClose: () -> Unit
    )

    /**
     * 显示"请稍候"加载对话框，用于在数据库查询期间遮罩玩家界面。
     * 该对话框不可关闭、无输入项、无回调。
     */
    fun showLoading(
        player: Player,
        title: Component,
        body: List<Component>
    )

    fun close(player: Player)

    /** 使用当前平台可用的文本协议发送 Adventure 组件。 */
    fun sendMessage(sender: CommandSender, message: Component)

    /** 使用当前平台可用的 API 踢出玩家并显示组件消息。 */
    fun kick(player: Player, message: Component)

    fun shutdown()
}

data class LoginResponse(val password: String?, val autoLoginByIp: Boolean)
data class RegisterResponse(val password: String?, val confirmPassword: String?)
data class ChangePasswordResponse(val oldPassword: String?, val newPassword: String?, val confirmNewPassword: String?)
data class BindEmailResponse(val email: String?, val code: String?)
data class RecoverPasswordResponse(val code: String?, val newPassword: String?, val confirmNewPassword: String?)
data class WelcomeResponse(val acceptedTerms: Boolean)
