package org.katacr.kalogin

import net.byteflux.libby.BukkitLibraryManager
import net.byteflux.libby.Library
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kalogin.listener.KaLoginAPI
import java.io.File

class KaLogin : JavaPlugin() {

    lateinit var dbManager: DatabaseManager
    lateinit var messageManager: MessageManager
    lateinit var antiCheatManager: AntiCheatManager
    lateinit var loginListener: LoginListener
    lateinit var authMeManager: AuthMeManager

    /**
     * 在插件加载时优先处理依赖下载
     */
    override fun onLoad() {
        // 创建共享的库目录（服务器根目录下的libraries文件夹）
        val librariesDir = File(dataFolder.parentFile.parentFile, "libraries")
        if (!librariesDir.exists()) {
            librariesDir.mkdirs()
        }

        val libraryManager = BukkitLibraryManager(this, librariesDir.absolutePath)

        // 添加 Maven 中央仓库和阿里云镜像（加速国内下载）
        libraryManager.addMavenCentral()
        libraryManager.addRepository("https://maven.aliyun.com/repository/public")

        // 1. Kotlin 标准库
        val kotlinStd = Library.builder()
            .groupId("org{}jetbrains{}kotlin")
            .artifactId("kotlin-stdlib")
            .version("1.9.22")
            .build()

        // 2. BCrypt 密码加密库
        val jbcrypt = Library.builder()
            .groupId("org{}mindrot")
            .artifactId("jbcrypt")
            .version("0.4")
            .build()

        // 3. SQLite JDBC 驱动
        val sqlite = Library.builder()
            .groupId("org{}xerial")
            .artifactId("sqlite-jdbc")
            .version("3.46.1.0")
            .build()

        logger.info("Checking and downloading necessary dependent libraries, please wait...")

        libraryManager.loadLibrary(kotlinStd)
        libraryManager.loadLibrary(jbcrypt)
        libraryManager.loadLibrary(sqlite)
    }

    override fun onEnable() {
        // 保存默认配置文件
        saveDefaultConfig()

        // 加载UI配置文件
        loadUIConfigs()

        // 初始化消息管理器
        messageManager = MessageManager(this)
        messageManager.init()

        // 初始化UI构建器
        LoginUI.init(this)

        // 初始化防作弊管理器
        antiCheatManager = AntiCheatManager(this)

        // 初始化 AuthMe 集成
        authMeManager = AuthMeManager(this)
        authMeManager.init()

        // 初始化数据库（始终初始化，因为 AuthMe 模式下也需要存储 IP 信息）
        dbManager = DatabaseManager(this)
        dbManager.init()

        // 初始化 KaLogin API
        val api = KaLoginAPI.getInstance()
        api?.setEnabled(true)

        // 创建并注册监听器
        loginListener = LoginListener(this)
        if (!authMeManager.useAuthMe) {
            server.pluginManager.registerEvents(loginListener, this)
            server.pluginManager.registerEvents(antiCheatManager, this)
        } else {
            // AuthMe 模式下注册 AuthMe 登录监听器
            // AuthMe 会自己处理反作弊，不需要 KaLogin 的 antiCheatManager
            server.pluginManager.registerEvents(AuthMeLoginListener(this), this)
        }

        // 注册指令
        registerCommands()

        logger.info(messageManager.getMessage("plugin.enabled"))
    }

    /**
     * 加载UI配置文件
     */
    private fun loadUIConfigs() {
        val uiFolder = File(dataFolder, "ui")
        if (!uiFolder.exists()) {
            uiFolder.mkdirs()
        }

        // 释放默认UI配置文件
        listOf("login", "register", "change-password").forEach { name ->
            val uiFile = File(uiFolder, "$name.yml")
            if (!uiFile.exists()) {
                saveResource("ui/$name.yml", false)
            }
        }

        // 将UI配置加载到config中
        val config = config
        uiFolder.listFiles()?.filter { it.extension == "yml" }?.forEach { file ->
            val uiConfig = YamlConfiguration.loadConfiguration(file)
            val uiName = file.nameWithoutExtension
            uiConfig.getKeys(false).forEach { key ->
                config.set("ui.$uiName.$key", uiConfig.get(key))
            }
        }

        saveConfig()
    }

    private fun registerCommands() {
        // 根据是否使用 AuthMe 选择命令执行器
        val commandExecutor = if (authMeManager.useAuthMe) {
            AuthMeCommandExecutor(this)
        } else {
            KaLoginCommand(this)
        }

        // 注册主指令 kalogin
        getCommand("kalogin")?.setExecutor(commandExecutor)

        // 注册别名 kl
        getCommand("kl")?.setExecutor(commandExecutor)

        // 注册修改密码指令
        val changePasswordCommand = ChangePasswordCommand(this)
        getCommand("changepassword")?.setExecutor(changePasswordCommand)
    }

    override fun onDisable() {
        dbManager.close()
        antiCheatManager.clearAll()

        // 关闭 KaLogin API
        val api = KaLoginAPI.getInstance()
        api?.setEnabled(false)

        logger.info(messageManager.getMessage("plugin.disabled"))
    }
}