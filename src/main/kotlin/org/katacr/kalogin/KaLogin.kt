package org.katacr.kalogin

import org.bukkit.plugin.java.JavaPlugin

class KaLogin : JavaPlugin() {

    lateinit var dbManager: DatabaseManager
    lateinit var messageManager: MessageManager
    lateinit var antiCheatManager: AntiCheatManager
    lateinit var loginListener: LoginListener

    override fun onEnable() {
        // 保存默认配置文件
        saveDefaultConfig()

        // 初始化消息管理器
        messageManager = MessageManager(this)
        messageManager.init()

        // 初始化防作弊管理器
        antiCheatManager = AntiCheatManager(this)

        // 初始化数据库
        dbManager = DatabaseManager(this)
        dbManager.init()

        // 创建并注册监听器
        loginListener = LoginListener(this)
        server.pluginManager.registerEvents(loginListener, this)
        server.pluginManager.registerEvents(antiCheatManager, this)

        // 注册指令
        registerCommands()

        logger.info(messageManager.getMessage("plugin.enabled"))
    }

    private fun registerCommands() {
        val commandExecutor = KaLoginCommand(this)

        // 注册主指令 kalogin
        getCommand("kalogin")?.setExecutor(commandExecutor)
        getCommand("kalogin")?.setTabCompleter(commandExecutor)

        // 注册别名 kl
        getCommand("kl")?.setExecutor(commandExecutor)
        getCommand("kl")?.setTabCompleter(commandExecutor)
    }

    override fun onDisable() {
        dbManager.close()
        antiCheatManager.clearAll()
        logger.info(messageManager.getMessage("plugin.disabled"))
    }
}