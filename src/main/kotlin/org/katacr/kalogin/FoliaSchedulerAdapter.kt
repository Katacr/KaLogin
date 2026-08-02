@file:Suppress("UnstableApiUsage")

package org.katacr.kalogin

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.function.Consumer

/**
 * Folia 专用调度实现，仅在检测到 Folia API 后由 KaLoginScheduler 反射加载。
 */
class FoliaSchedulerPlatformAdapter : FoliaSchedulerAdapter {
    override fun isPlayerThread(player: Player): Boolean = Bukkit.isOwnedByCurrentRegion(player)

    override fun runPlayer(plugin: Plugin, player: Player, task: Runnable): KaLoginTaskHandle =
        player.scheduler.run(plugin, Consumer { task.run() }, null).toHandle()

    override fun runPlayerLater(plugin: Plugin, player: Player, delayTicks: Long, task: Runnable): KaLoginTaskHandle =
        player.scheduler.runDelayed(plugin, Consumer { task.run() }, null, delayTicks).toHandle()

    override fun runGlobal(plugin: Plugin, task: Runnable): KaLoginTaskHandle =
        Bukkit.getGlobalRegionScheduler().run(plugin, Consumer { task.run() }).toHandle()

    override fun runGlobalLater(plugin: Plugin, delayTicks: Long, task: Runnable): KaLoginTaskHandle =
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, Consumer { task.run() }, delayTicks).toHandle()

    override fun runAsync(plugin: Plugin, task: Runnable): KaLoginTaskHandle =
        Bukkit.getAsyncScheduler().runNow(plugin, Consumer { task.run() }).toHandle()

    override fun teleport(player: Player, location: Location) {
        player.teleportAsync(location)
    }

    override fun cancelPluginTasks(plugin: Plugin) {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin)
        Bukkit.getAsyncScheduler().cancelTasks(plugin)
    }

    private fun ScheduledTask?.toHandle(): KaLoginTaskHandle =
        if (this == null) KaLoginTaskHandle.NOOP else KaLoginTaskHandle { cancel() }
}
