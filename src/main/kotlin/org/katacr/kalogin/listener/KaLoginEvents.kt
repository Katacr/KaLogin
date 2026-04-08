package org.katacr.kalogin.listener

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * KaLogin 事件基类
 */
abstract class KaLoginEvent : Event() {

    companion object {
        private val handlers = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }

    override fun getHandlers(): HandlerList = handlers
}

/**
 * 玩家登录成功事件
 * 当玩家通过密码验证成功登录时触发
 *
 * @param player 登录的玩家
 * @param ip 玩家登录时的IP地址
 * @param isAutoLogin 是否为自动登录
 */
class PlayerLoginSuccessEvent(
    val player: Player,
    val ip: String,
    val isAutoLogin: Boolean = false
) : KaLoginEvent()

/**
 * 玩家登录失败事件
 * 当玩家密码错误导致登录失败时触发
 *
 * @param player 尝试登录的玩家
 * @param remainingAttempts 剩余尝试次数
 */
class PlayerLoginFailedEvent(
    val player: Player,
    val remainingAttempts: Int
) : KaLoginEvent()

/**
 * 玩家自动登录成功事件
 * 当玩家通过IP检测自动登录成功时触发
 *
 * @param player 自动登录的玩家
 * @param ip 玩家登录时的IP地址
 */
class PlayerAutoLoginEvent(
    val player: Player,
    val ip: String
) : KaLoginEvent()

/**
 * 玩家注册成功事件
 * 当玩家成功注册新账户时触发
 *
 * @param player 注册的玩家
 * @param ip 玩家注册时的IP地址
 */
class PlayerRegisterSuccessEvent(
    val player: Player,
    val ip: String
) : KaLoginEvent()

/**
 * 玩家注册失败事件
 * 当玩家注册失败时触发
 *
 * @param player 尝试注册的玩家
 * @param reason 失败原因
 */
class PlayerRegisterFailedEvent(
    val player: Player,
    val reason: String
) : KaLoginEvent()

/**
 * 修改密码成功事件
 * 当玩家成功修改密码时触发
 *
 * @param player 修改密码的玩家
 */
class PlayerChangePasswordSuccessEvent(
    val player: Player
) : KaLoginEvent()

/**
 * 修改密码失败事件
 * 当玩家修改密码失败时触发
 *
 * @param player 尝试修改密码的玩家
 * @param reason 失败原因
 */
class PlayerChangePasswordFailedEvent(
    val player: Player,
    val reason: String
) : KaLoginEvent()

/**
 * 玩家登出事件
 * 当玩家登出游戏时触发
 *
 * @param player 登出的玩家
 */
class PlayerLogoutEvent(
    val player: Player
) : KaLoginEvent()

/**
 * 玩家注销账户事件
 * 当玩家注销自己的账户时触发
 *
 * @param player 注销账户的玩家
 */
class PlayerUnregisterEvent(
    val player: Player
) : KaLoginEvent()

/**
 * 玩家被管理员注销账户事件
 * 当管理员注销玩家账户时触发
 *
 * @param playerName 被注销账户的玩家名称
 */
class PlayerAdminUnregisterEvent(
    val playerName: String
) : KaLoginEvent()
