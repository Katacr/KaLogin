package org.katacr.kalogin

import org.bukkit.entity.Player
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * 更新检查器
 * 从 GitHub 获取最新版本号，对比当前版本，向 OP 玩家发送更新提示
 */
object UpdateChecker {

    private const val PLUGIN_YML_URL =
        "https://raw.githubusercontent.com/Katacr/KaLogin/refs/heads/main/src/main/resources/plugin.yml"
    private const val MINEBBS_URL = "https://www.minebbs.com/resources/"
    private const val SPIGOTMC_URL = "https://www.spigotmc.org/resources/"

    private var latestVersion: String? = null
    private var currentVersion: String? = null
    private var checkComplete = false
    private var messageManager: MessageManager? = null
    private var logger: java.util.logging.Logger? = null

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    /**
     * 启动异步版本检查
     */
    fun check(plugin: KaLogin) {
        currentVersion = plugin.description.version
        messageManager = plugin.messageManager
        logger = plugin.logger

        // 使用 Bukkit 调度器异步执行
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(PLUGIN_YML_URL))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build()

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() == 200) {
                    val body = response.body()
                    val versionRegex = Regex("version:\\s*'([^']+)'")
                    latestVersion = versionRegex.find(body)?.groupValues?.get(1)
                }
            } catch (_: Exception) {
                // 网络不可达，静默忽略
            }
            checkComplete = true
        })
    }

    /**
     * 向 OP 玩家发送更新提示（如果有新版本）
     * 应在玩家加入事件中调用
     */
    fun notifyIfUpdateAvailable(player: Player) {
        if (!checkComplete || !player.isOp) return
        val latest = latestVersion ?: return
        val current = currentVersion ?: return
        if (!isNewer(latest, current)) return

        val msg = messageManager?.getMessage(
            "plugin.update-available",
            "latest" to latest,
            "current" to current
        ) ?: "&e[KaLogin] &fNew version available: &a$latest&f, current: &7$current"

        // 替换为 hovertext 格式，复用 LoginUI 的解析器
        val minebbsHover = messageManager?.getMessage("plugin.update-minebbs-hover") ?: "&7MineBBS"
        val spigotmcHover = messageManager?.getMessage("plugin.update-spigotmc-hover") ?: "&7SpigotMC"
        val processed = msg
            .replace("&a[MineBBS]", "<text=&a[MineBBS];hover=$minebbsHover;url=$MINEBBS_URL>")
            .replace("&b[SpigotMC]", "<text=&b[SpigotMC];hover=$spigotmcHover;url=$SPIGOTMC_URL>")

        player.sendMessage(LoginUI.parseClickableText(processed, player))
    }

    /**
     * 比较版本号，latest > current 则返回 true
     * 支持标准 semver 格式 x.y.z
     */
    private fun isNewer(latest: String, current: String): Boolean {
        val lv = parseVersion(latest) ?: return false
        val cv = parseVersion(current) ?: return false
        for (i in 0 until maxOf(lv.size, cv.size)) {
            val l = lv.getOrElse(i) { 0 }
            val c = cv.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun parseVersion(version: String): List<Int>? {
        return try {
            version.split(".").map { it.toInt() }
        } catch (_: Exception) {
            null
        }
    }
}
