package org.katacr.kalogin

import fr.xephi.authme.api.v3.AuthMeApi
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * AuthMe 集成管理器
 * 用于与 AuthMe 插件进行交互
 */
class AuthMeManager(private val plugin: KaLogin) {

    private var authMeApi: AuthMeApi? = null
    var useAuthMe = false
        private set

    /**
     * 初始化 AuthMe 集成
     */
    fun init() {
        useAuthMe = plugin.config.getBoolean("use-AuthMe", false)

        if (useAuthMe) {
            val authMePlugin = plugin.server.pluginManager.getPlugin("AuthMe")
            if (authMePlugin != null && authMePlugin.isEnabled) {
                authMeApi = AuthMeApi.getInstance()
                plugin.logger.info(plugin.messageManager.getMessage("authme.enabled"))
            } else {
                plugin.logger.warning(plugin.messageManager.getMessage("authme.not-found"))
                plugin.logger.warning(plugin.messageManager.getMessage("authme.fallback-to-builtin"))
                useAuthMe = false
            }
        }
    }

    /**
     * 检查玩家是否已注册
     */
    fun isRegistered(playerName: String): Boolean {
        return if (useAuthMe) {
            authMeApi?.isRegistered(playerName) ?: false
        } else {
            false
        }
    }

    /**
     * 检查玩家是否已登录
     */
    fun isAuthenticated(player: Player): Boolean {
        return if (useAuthMe) {
            authMeApi?.isAuthenticated(player) ?: false
        } else {
            false
        }
    }

    /**
     * 验证密码
     */
    fun checkPassword(playerName: String, password: String): Boolean {
        return if (useAuthMe) {
            authMeApi?.checkPassword(playerName, password) ?: false
        } else {
            false
        }
    }

    /**
     * 强制玩家登录
     */
    fun forceLogin(player: Player) {
        if (useAuthMe) {
            authMeApi?.forceLogin(player)
            // 更新最后登录 IP
            val currentIp = player.address?.address?.hostAddress ?: "127.0.0.1"
            plugin.dbManager.updateLastLoginIp(player.uniqueId, currentIp)
        }
    }

    /**
     * 初始化玩家在 KaLogin 数据库中的记录
     * AuthMe 模式下只存储 IP 和自动登录设置
     */
    fun initPlayerInDatabase(player: Player) {
        if (useAuthMe) {
            val currentIp = player.address?.address?.hostAddress ?: "127.0.0.1"
            plugin.dbManager.isPlayerRegistered(player.uniqueId).thenAccept { registered ->
                if (!registered) {
                    // 玩家首次在 AuthMe 模式下登录，初始化记录
                    plugin.dbManager.initPlayerForAuthMe(
                        player.uniqueId,
                        player.name,
                        currentIp
                    )
                }
            }
        }
    }

    /**
     * 强制玩家注册
     */
    fun forceRegister(player: Player, password: String, autoLogin: Boolean = true) {
        if (useAuthMe) {
            authMeApi?.forceRegister(player, password, autoLogin)
        }
    }

    /**
     * 修改密码
     */
    fun changePassword(playerName: String, newPassword: String) {
        if (useAuthMe) {
            authMeApi?.changePassword(playerName, newPassword)
        }
    }

    /**
     * 强制玩家登出
     */
    fun forceLogout(player: Player) {
        if (useAuthMe) {
            authMeApi?.forceLogout(player)
        }
    }

    /**
     * 获取玩家最后登录IP
     */
    fun getLastIp(playerName: String): String? {
        return if (useAuthMe) {
            authMeApi?.getLastIp(playerName)
        } else {
            null
        }
    }

    /**
     * 根据IP获取玩家名称列表
     */
    fun getNamesByIp(ip: String): List<String>? {
        return if (useAuthMe) {
            authMeApi?.getNamesByIp(ip)
        } else {
            null
        }
    }

    /**
     * 获取所有已注册的玩家名称
     */
    fun getRegisteredNames(): List<String>? {
        return if (useAuthMe) {
            authMeApi?.getRegisteredNames()
        } else {
            null
        }
    }

    /**
     * 强制注销玩家
     */
    fun forceUnregister(playerName: String) {
        if (useAuthMe) {
            authMeApi?.forceUnregister(playerName)
        }
    }

    /**
     * 注册玩家（管理员命令使用）
     */
    fun registerPlayer(playerName: String, password: String): Boolean {
        return if (useAuthMe) {
            authMeApi?.registerPlayer(playerName, password) ?: false
        } else {
            false
        }
    }

    /**
     * 强制注销玩家（通过Player对象）
     */
    fun forceUnregister(player: Player) {
        if (useAuthMe) {
            authMeApi?.forceUnregister(player)
        }
    }
}

/**
 * AuthMe 模式下的命令执行器
 * 当使用 AuthMe 模式时，某些命令需要由 AuthMe 处理
 */
class AuthMeCommandExecutor(private val plugin: KaLogin) : CommandExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>
    ): Boolean {
        // 如果不是使用 AuthMe 模式，返回 false 让其他处理器处理
        if (!plugin.authMeManager.useAuthMe) {
            return false
        }

        // AuthMe 模式下的特殊处理
        // 大部分登录/注册命令由 AuthMe 自己处理
        // KaLogin 只负责 UI 展示和某些自定义命令

        return when (command.name.lowercase()) {
            "kalogin", "kl" -> handleKaLoginCommand(sender, args)
            else -> false
        }
    }

    private fun handleKaLoginCommand(sender: CommandSender, args: Array<String>): Boolean {
        if (args.isEmpty()) {
            plugin.messageManager.sendMessage(sender, "authme.command-help-delete")
            plugin.messageManager.sendMessage(sender, "authme.command-help-register")
            plugin.messageManager.sendMessage(sender, "authme.command-help-reload")
            return true
        }

        val subCommand = args[0].lowercase()

        return when (subCommand) {
            "delete" -> handleDeleteCommand(sender, args)
            "register" -> handleRegisterCommand(sender, args)
            "reload" -> handleReloadCommand(sender)
            else -> {
                plugin.messageManager.sendMessage(sender, "authme.unknown-command", "command" to subCommand)
                true
            }
        }
    }

    private fun handleDeleteCommand(sender: CommandSender, args: Array<String>): Boolean {
        if (args.size < 2) {
            plugin.messageManager.sendMessage(sender, "authme.delete-usage")
            return true
        }

        if (!sender.hasPermission("kalogin.admin")) {
            plugin.messageManager.sendMessage(sender, "command.no-permission")
            return true
        }

        val playerName = args[1]

        // 使用 AuthMe 注销玩家
        plugin.authMeManager.forceUnregister(playerName)
        plugin.messageManager.sendMessage(sender, "authme.delete-success", "player" to playerName)

        return true
    }

    private fun handleRegisterCommand(sender: CommandSender, args: Array<String>): Boolean {
        if (args.size < 3) {
            plugin.messageManager.sendMessage(sender, "authme.register-usage")
            return true
        }

        if (!sender.hasPermission("kalogin.admin")) {
            plugin.messageManager.sendMessage(sender, "command.no-permission")
            return true
        }

        val playerName = args[1]
        val password = args[2]

        // 使用 AuthMe 注册玩家
        val success = plugin.authMeManager.registerPlayer(playerName, password)
        if (success) {
            plugin.messageManager.sendMessage(sender, "authme.register-set-success", "player" to playerName)
        } else {
            plugin.messageManager.sendMessage(sender, "authme.register-failed")
        }

        return true
    }

    private fun handleReloadCommand(sender: CommandSender): Boolean {
        if (!sender.hasPermission("kalogin.admin")) {
            plugin.messageManager.sendMessage(sender, "command.no-permission")
            return true
        }

        plugin.reloadConfig()
        plugin.messageManager.reload()
        plugin.authMeManager.init()

        plugin.messageManager.sendMessage(sender, "command.reload.success")
        return true
    }
}
