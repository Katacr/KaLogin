package org.katacr.kalogin.listener

import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * KaLogin API 主类
 * 提供给其他插件使用的接口
 *
 * 使用方法：
 * 1. 获取 API 实例
 * 2. 注册事件监听器
 * 3. 在事件触发时收到通知
 *
 * 示例代码：
 * ```kotlin
 * // 在你的插件 onEnable() 中注册监听器
 * val api = KaLoginAPI.getInstance()
 * if (api.isEnabled()) {
 *     api.registerListener(this, MyKaLoginListener())
 * }
 *
 * // 定义监听器
 * class MyKaLoginListener : KaLoginListener {
 *     override fun onPlayerLoginSuccess(event: PlayerLoginSuccessEvent) {
 *         // 处理玩家登录成功事件
 *         val player = event.player
 *         val isAutoLogin = event.isAutoLogin
 *     }
 * }
 * ```
 */
class KaLoginAPI private constructor() {

    private val listeners = mutableListOf<KaLoginListener>()
    private var isPluginEnabled = false

    companion object {
        private var instance: KaLoginAPI? = null

        /**
         * 获取 KaLoginAPI 单例实例
         * @return KaLoginAPI 实例，如果 KaLogin 未启用则返回 null
         */
        @JvmStatic
        fun getInstance(): KaLoginAPI? {
            if (instance == null) {
                instance = KaLoginAPI()
            }
            return instance
        }

    }

    /**
     * 内部方法：由 KaLogin 插件调用以设置启用状态
     */
    fun setEnabled(enabled: Boolean) {
        isPluginEnabled = enabled
    }

    /**
     * 触发玩家登录成功事件
     * @param player 登录的玩家
     * @param ip 玩家IP地址
     * @param isAutoLogin 是否为自动登录
     */
    fun callPlayerLoginSuccess(player: Player, ip: String, isAutoLogin: Boolean) {
        if (!isPluginEnabled) return
        val event = PlayerLoginSuccessEvent(player, ip, isAutoLogin)
        Bukkit.getPluginManager().callEvent(event)
        listeners.forEach { it.onPlayerLoginSuccess(event) }
    }

    /**
     * 触发玩家登录失败事件
     * @param player 尝试登录的玩家
     * @param remainingAttempts 剩余尝试次数
     */
    fun callPlayerLoginFailed(player: Player, remainingAttempts: Int) {
        if (!isPluginEnabled) return
        val event = PlayerLoginFailedEvent(player, remainingAttempts)
        Bukkit.getPluginManager().callEvent(event)
        listeners.forEach { it.onPlayerLoginFailed(event) }
    }

    /**
     * 触发玩家自动登录事件
     * @param player 自动登录的玩家
     * @param ip 玩家IP地址
     */
    fun callPlayerAutoLogin(player: Player, ip: String) {
        if (!isPluginEnabled) return
        val event = PlayerAutoLoginEvent(player, ip)
        Bukkit.getPluginManager().callEvent(event)
        listeners.forEach { it.onPlayerAutoLogin(event) }
    }

    /**
     * 触发玩家注册成功事件
     * @param player 注册的玩家
     * @param ip 玩家IP地址
     */
    fun callPlayerRegisterSuccess(player: Player, ip: String) {
        if (!isPluginEnabled) return
        val event = PlayerRegisterSuccessEvent(player, ip)
        Bukkit.getPluginManager().callEvent(event)
        listeners.forEach { it.onPlayerRegisterSuccess(event) }
    }

    /**
     * 触发玩家注册失败事件
     * @param player 尝试注册的玩家
     * @param reason 失败原因
     */
    fun callPlayerRegisterFailed(player: Player, reason: String) {
        if (!isPluginEnabled) return
        val event = PlayerRegisterFailedEvent(player, reason)
        Bukkit.getPluginManager().callEvent(event)
        listeners.forEach { it.onPlayerRegisterFailed(event) }
    }

    /**
     * 触发修改密码成功事件
     * @param player 修改密码的玩家
     */
    fun callPlayerChangePasswordSuccess(player: Player) {
        if (!isPluginEnabled) return
        val event = PlayerChangePasswordSuccessEvent(player)
        Bukkit.getPluginManager().callEvent(event)
        listeners.forEach { it.onPlayerChangePasswordSuccess(event) }
    }

    /**
     * 触发修改密码失败事件
     * @param player 尝试修改密码的玩家
     * @param reason 失败原因
     */
    fun callPlayerChangePasswordFailed(player: Player, reason: String) {
        if (!isPluginEnabled) return
        val event = PlayerChangePasswordFailedEvent(player, reason)
        Bukkit.getPluginManager().callEvent(event)
        listeners.forEach { it.onPlayerChangePasswordFailed(event) }
    }

    /**
     * 触发玩家登出事件
     * @param player 登出的玩家
     */
    fun callPlayerLogout(player: Player) {
        if (!isPluginEnabled) return
        val event = PlayerLogoutEvent(player)
        Bukkit.getPluginManager().callEvent(event)
        listeners.forEach { it.onPlayerLogout(event) }
    }

    /**
     * 触发玩家注销账户事件
     * @param player 注销账户的玩家
     */
    fun callPlayerUnregister(player: Player) {
        if (!isPluginEnabled) return
        val event = PlayerUnregisterEvent(player)
        Bukkit.getPluginManager().callEvent(event)
        listeners.forEach { it.onPlayerUnregister(event) }
    }

    /**
     * 触发管理员注销账户事件
     * @param playerName 被注销账户的玩家名称
     */
    fun callPlayerAdminUnregister(playerName: String) {
        if (!isPluginEnabled) return
        val event = PlayerAdminUnregisterEvent(playerName)
        Bukkit.getPluginManager().callEvent(event)
        listeners.forEach { it.onPlayerAdminUnregister(event) }
    }

}
