package org.katacr.kalogin.listener

import org.bukkit.entity.Player

/**
 * 玩家登录成功事件
 * 当玩家通过密码验证成功登录时触发
 */
data class PlayerLoginSuccessEvent(
    val player: Player,
    val ip: String,
    val isAutoLogin: Boolean = false
)

/**
 * 玩家登录失败事件
 * 当玩家密码错误导致登录失败时触发
 */
data class PlayerLoginFailedEvent(
    val player: Player,
    val remainingAttempts: Int
)

/**
 * 玩家自动登录成功事件
 * 当玩家通过IP检测自动登录成功时触发
 */
data class PlayerAutoLoginEvent(
    val player: Player,
    val ip: String
)

/**
 * 玩家注册成功事件
 * 当玩家成功注册新账户时触发
 */
data class PlayerRegisterSuccessEvent(
    val player: Player,
    val ip: String
)

/**
 * 玩家注册失败事件
 * 当玩家注册失败时触发
 */
data class PlayerRegisterFailedEvent(
    val player: Player,
    val reason: String
)

/**
 * 修改密码成功事件
 * 当玩家成功修改密码时触发
 */
data class PlayerChangePasswordSuccessEvent(
    val player: Player
)

/**
 * 修改密码失败事件
 * 当玩家修改密码失败时触发
 */
data class PlayerChangePasswordFailedEvent(
    val player: Player,
    val reason: String
)

/**
 * 玩家登出事件
 * 当玩家登出游戏时触发
 */
data class PlayerLogoutEvent(
    val player: Player
)

/**
 * 玩家注销账户事件
 * 当玩家注销自己的账户时触发
 */
data class PlayerUnregisterEvent(
    val player: Player
)

/**
 * 玩家被管理员注销账户事件
 * 当管理员注销玩家账户时触发
 */
data class PlayerAdminUnregisterEvent(
    val playerName: String
)
