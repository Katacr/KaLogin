package org.katacr.kalogin.dialog

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.katacr.kalogin.KaLogin

/**
 * 隔离 Paper/Folia 与 Spigot 的原生 Dialog API。
 *
 * 业务监听器只传入标题、正文和回调，避免共享代码直接引用平台专属 Dialog 类型。
 */
interface LoginDialogPlatform {
    val platformName: String

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

    fun close(player: Player)

    fun shutdown()
}

data class LoginResponse(val password: String?, val autoLoginByIp: Boolean)
data class RegisterResponse(val password: String?, val confirmPassword: String?)
data class ChangePasswordResponse(val oldPassword: String?, val newPassword: String?, val confirmNewPassword: String?)
data class BindEmailResponse(val email: String?, val code: String?)
data class RecoverPasswordResponse(val code: String?, val newPassword: String?, val confirmNewPassword: String?)
data class WelcomeResponse(val acceptedTerms: Boolean)
