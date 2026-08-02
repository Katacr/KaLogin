package org.katacr.kalogin

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask

/**
 * 统一封装 Bukkit、Paper 与 Folia 的调度入口。
 *
 * 玩家相关操作在 Folia 上进入玩家 EntityScheduler，普通核心上回退到 BukkitScheduler。
 */
object KaLoginScheduler {
    private lateinit var plugin: Plugin

    private val foliaAdapter: FoliaSchedulerAdapter? by lazy {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            Class.forName("org.katacr.kalogin.FoliaSchedulerPlatformAdapter")
                .getDeclaredConstructor()
                .newInstance() as FoliaSchedulerAdapter
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: LinkageError) {
            null
        }
    }

    val folia: Boolean
        get() = foliaAdapter != null

    fun init(plugin: Plugin) {
        this.plugin = plugin
    }

    fun isPlayerThread(player: Player): Boolean =
        foliaAdapter?.isPlayerThread(player) ?: Bukkit.isPrimaryThread()

    fun runPlayer(player: Player, task: Runnable): KaLoginTaskHandle =
        foliaAdapter?.runPlayer(plugin, player, task) ?: Bukkit.getScheduler().runTask(plugin, task).toHandle()

    fun runPlayerLater(player: Player, delayTicks: Long, task: Runnable): KaLoginTaskHandle =
        foliaAdapter?.runPlayerLater(plugin, player, delayTicks.coerceAtLeast(1L), task)
            ?: Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks.coerceAtLeast(1L)).toHandle()

    fun runGlobal(task: Runnable): KaLoginTaskHandle =
        foliaAdapter?.runGlobal(plugin, task) ?: Bukkit.getScheduler().runTask(plugin, task).toHandle()

    fun runGlobalLater(delayTicks: Long, task: Runnable): KaLoginTaskHandle =
        foliaAdapter?.runGlobalLater(plugin, delayTicks.coerceAtLeast(1L), task)
            ?: Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks.coerceAtLeast(1L)).toHandle()

    fun runAsync(task: Runnable): KaLoginTaskHandle =
        foliaAdapter?.runAsync(plugin, task) ?: Bukkit.getScheduler().runTaskAsynchronously(plugin, task).toHandle()

    fun teleport(player: Player, location: Location) {
        val adapter = foliaAdapter
        if (adapter != null) {
            adapter.teleport(player, location)
        } else if (Bukkit.isPrimaryThread()) {
            player.teleport(location)
        } else {
            runPlayer(player, Runnable { player.teleport(location) })
        }
    }

    fun cancelPluginTasks() {
        val adapter = foliaAdapter
        if (adapter != null) {
            adapter.cancelPluginTasks(plugin)
        } else {
            Bukkit.getScheduler().cancelTasks(plugin)
        }
    }

    private fun BukkitTask.toHandle(): KaLoginTaskHandle = KaLoginTaskHandle { cancel() }
}

/**
 * Folia 调度实现契约。共享代码只依赖该接口，避免 Spigot 解析 Folia 类型。
 */
interface FoliaSchedulerAdapter {
    fun isPlayerThread(player: Player): Boolean
    fun runPlayer(plugin: Plugin, player: Player, task: Runnable): KaLoginTaskHandle
    fun runPlayerLater(plugin: Plugin, player: Player, delayTicks: Long, task: Runnable): KaLoginTaskHandle
    fun runGlobal(plugin: Plugin, task: Runnable): KaLoginTaskHandle
    fun runGlobalLater(plugin: Plugin, delayTicks: Long, task: Runnable): KaLoginTaskHandle
    fun runAsync(plugin: Plugin, task: Runnable): KaLoginTaskHandle
    fun teleport(player: Player, location: Location)
    fun cancelPluginTasks(plugin: Plugin)
}

/**
 * 统一任务句柄，隐藏 BukkitTask 与 Folia ScheduledTask 的差异。
 */
class KaLoginTaskHandle(private val cancelAction: () -> Unit) {
    fun cancel() {
        cancelAction()
    }

    companion object {
        val NOOP = KaLoginTaskHandle {}
    }
}
