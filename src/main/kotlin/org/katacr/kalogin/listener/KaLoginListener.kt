package org.katacr.kalogin.listener

import org.bukkit.entity.Player

/**
 * KaLogin 事件监听器接口
 * 其他插件可以实现此接口来监听 KaLogin 的各种事件
 *
 * 使用示例：
 * ```kotlin
 * class MyPluginListener : KaLoginListener {
 *     override fun onPlayerLoginSuccess(event: PlayerLoginSuccessEvent) {
 *         plugin.logger.info("玩家 ${event.player.name} 登录成功！")
 *     }
 *
 *     override fun onPlayerRegisterSuccess(event: PlayerRegisterSuccessEvent) {
 *         plugin.logger.info("玩家 ${event.player.name} 注册成功！")
 *     }
 * }
 *
 * // 注册监听器
 * KaLoginAPI.getInstance().registerListener(myPlugin, MyPluginListener())
 *
 * // 注销监听器
 * KaLoginAPI.getInstance().unregisterListener(myPlugin, MyPluginListener())
 * ```
 */
interface KaLoginListener {

    /**
     * 当玩家登录成功时调用
     * @param event 登录成功事件
     */
    open fun onPlayerLoginSuccess(event: PlayerLoginSuccessEvent) {}

    /**
     * 当玩家登录失败时调用
     * @param event 登录失败事件
     */
    open fun onPlayerLoginFailed(event: PlayerLoginFailedEvent) {}

    /**
     * 当玩家自动登录成功时调用
     * @param event 自动登录事件
     */
    open fun onPlayerAutoLogin(event: PlayerAutoLoginEvent) {}

    /**
     * 当玩家注册成功时调用
     * @param event 注册成功事件
     */
    open fun onPlayerRegisterSuccess(event: PlayerRegisterSuccessEvent) {}

    /**
     * 当玩家注册失败时调用
     * @param event 注册失败事件
     */
    open fun onPlayerRegisterFailed(event: PlayerRegisterFailedEvent) {}

    /**
     * 当玩家修改密码成功时调用
     * @param event 修改密码成功事件
     */
    open fun onPlayerChangePasswordSuccess(event: PlayerChangePasswordSuccessEvent) {}

    /**
     * 当玩家修改密码失败时调用
     * @param event 修改密码失败事件
     */
    open fun onPlayerChangePasswordFailed(event: PlayerChangePasswordFailedEvent) {}

    /**
     * 当玩家登出时调用
     * @param event 登出事件
     */
    open fun onPlayerLogout(event: PlayerLogoutEvent) {}

    /**
     * 当玩家注销账户时调用
     * @param event 注销账户事件
     */
    open fun onPlayerUnregister(event: PlayerUnregisterEvent) {}

    /**
     * 当玩家被管理员注销账户时调用
     * @param event 管理员注销账户事件
     */
    open fun onPlayerAdminUnregister(event: PlayerAdminUnregisterEvent) {}
}
