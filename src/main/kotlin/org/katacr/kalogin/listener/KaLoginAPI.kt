package org.katacr.kalogin.listener

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

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
     * 检查 KaLogin 插件是否已启用
     * @return true 如果 KaLogin 已启用
     */
    fun isEnabled(): Boolean = isPluginEnabled

    /**
     * 注册事件监听器
     * @param plugin 注册监听器的插件
     * @param listener 事件监听器实例
     */
    fun registerListener(plugin: Plugin, listener: KaLoginListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            plugin.logger.info("已注册 KaLogin 事件监听器: ${listener.javaClass.simpleName}")
        }
    }

    /**
     * 注销事件监听器
     * @param plugin 注销监听器的插件
     * @param listener 事件监听器实例
     */
    fun unregisterListener(plugin: Plugin, listener: KaLoginListener) {
        if (listeners.remove(listener)) {
            plugin.logger.info("已注销 KaLogin 事件监听器: ${listener.javaClass.simpleName}")
        }
    }

    /**
     * 获取所有已注册的监听器数量
     * @return 监听器数量
     */
    fun getListenerCount(): Int = listeners.size

    /**
     * 检查玩家是否已登录
     * @param playerName 玩家名称
     * @return true 如果玩家已登录
     */
    fun isPlayerLoggedIn(playerName: String): Boolean {
        val player = Bukkit.getPlayer(playerName) ?: return false
        return isPlayerLoggedIn(player)
    }

    /**
     * 检查玩家是否已登录
     * @param player 玩家对象
     * @return true 如果玩家已登录
     */
    fun isPlayerLoggedIn(player: Player): Boolean {
        val plugin = Bukkit.getPluginManager().getPlugin("KaLogin") ?: return false
        if (plugin is org.katacr.kalogin.KaLogin) {
            return if (plugin.authMeManager.useAuthMe) {
                plugin.authMeManager.isAuthenticated(player)
            } else {
                plugin.loginListener.isLoggedIn(player.uniqueId)
            }
        }
        return false
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
        listeners.forEach { it.onPlayerRegisterFailed(event) }
    }

    /**
     * 触发修改密码成功事件
     * @param player 修改密码的玩家
     */
    fun callPlayerChangePasswordSuccess(player: Player) {
        if (!isPluginEnabled) return
        val event = PlayerChangePasswordSuccessEvent(player)
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
        listeners.forEach { it.onPlayerChangePasswordFailed(event) }
    }

    /**
     * 触发玩家登出事件
     * @param player 登出的玩家
     */
    fun callPlayerLogout(player: Player) {
        if (!isPluginEnabled) return
        val event = PlayerLogoutEvent(player)
        listeners.forEach { it.onPlayerLogout(event) }
    }

    /**
     * 执行玩家登出
     * 包括：触发事件、关闭自动登录、踢出玩家
     *
     * @param player 登出的玩家
     * @param kickMessage 可选的踢出消息，如果为 null 则使用默认消息
     */
    fun logout(player: Player, kickMessage: net.kyori.adventure.text.Component? = null) {
        if (!isPluginEnabled) return

        val plugin = Bukkit.getPluginManager().getPlugin("KaLogin") as? org.katacr.kalogin.KaLogin ?: return

        // 1. 触发登出事件
        val event = PlayerLogoutEvent(player)
        listeners.forEach { it.onPlayerLogout(event) }

        // 2. 关闭同IP自动登录
        plugin.dbManager.updateAutoLoginByIp(player.uniqueId, false)

        // 3. 如果使用 AuthMe，触发 AuthMe 登出
        if (plugin.authMeManager.useAuthMe) {
            plugin.authMeManager.forceLogout(player)
        }

        // 4. 踢出玩家
        val message = kickMessage ?: plugin.messageManager.getComponent("logout.kick-message")
        player.kick(message)
    }

    /**
     * 触发玩家注销账户事件
     * @param player 注销账户的玩家
     */
    fun callPlayerUnregister(player: Player) {
        if (!isPluginEnabled) return
        val event = PlayerUnregisterEvent(player)
        listeners.forEach { it.onPlayerUnregister(event) }
    }

    /**
     * 触发管理员注销账户事件
     * @param playerName 被注销账户的玩家名称
     */
    fun callPlayerAdminUnregister(playerName: String) {
        if (!isPluginEnabled) return
        val event = PlayerAdminUnregisterEvent(playerName)
        listeners.forEach { it.onPlayerAdminUnregister(event) }
    }

}
