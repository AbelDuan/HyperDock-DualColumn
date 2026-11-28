package io.github.z1812.hyperdock.xposed.hook.Sidebar

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import io.github.z1812.hyperdock.xposed.ConfigManager
import io.github.z1812.hyperdock.xposed.LogUtil

/**
 * 全部应用面板的“最近”排序数据源。
 *
 * - 最近打开：UsageStatsManager 的 lastTimeUsed（Hook 运行在安全中心进程，
 *   该进程本身持有 PACKAGE_USAGE_STATS，不需要给模块 App 额外授权）。
 * - 最近任务：ActivityManager.getRecentTasks 反射调用，返回顺序即最近程度。
 *
 * 结果按包名去重，短时缓存，避免 StateFlow 每次发射都重新查询。
 */
internal object SidebarRecentApps {
    const val SORT_DEFAULT = "default"
    const val SORT_RECENT_OPEN = "recent_open"
    const val SORT_RECENT_TASK = "recent_task"

    private const val LIMIT = 64
    private const val CACHE_MS = 20_000L

    @Volatile private var cachedMode: String = ""
    @Volatile private var cachedAt: Long = 0L
    @Volatile private var cachedPackages: List<String> = emptyList()

    fun rankedPackages(context: Context?, mode: String): List<String> {
        if (context == null || mode == SORT_DEFAULT) return emptyList()
        val now = System.currentTimeMillis()
        if (mode == cachedMode && now - cachedAt < CACHE_MS) return cachedPackages
        val result = when (mode) {
            SORT_RECENT_OPEN -> recentOpen(context)
            SORT_RECENT_TASK -> recentTasks(context)
            else -> emptyList()
        }.distinct().take(LIMIT)
        if (result.isEmpty()) log("no packages for $mode (check usage-stats/recent-task access)")
        cachedMode = mode
        cachedAt = now
        cachedPackages = result
        return result
    }

    fun invalidate() {
        cachedMode = ""
        cachedAt = 0L
        cachedPackages = emptyList()
    }

    private fun recentOpen(context: Context): List<String> = runCatching {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val now = System.currentTimeMillis()
        val stats = manager.queryUsageStats(
            UsageStatsManager.INTERVAL_WEEKLY,
            now - 7L * 24 * 60 * 60 * 1000,
            now,
        ) ?: return emptyList()
        stats.filter { it.lastTimeUsed > 0 && it.packageName.isNotBlank() }
            .sortedByDescending { it.lastTimeUsed }
            .map { it.packageName }
    }.getOrElse {
        log("recent open query failed: ${it.javaClass.simpleName}")
        emptyList()
    }

    private fun recentTasks(context: Context): List<String> = runCatching {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return emptyList()
        val method = ActivityManager::class.java.getDeclaredMethod(
            "getRecentTasks",
            Integer.TYPE,
            Integer.TYPE,
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val tasks = method.invoke(manager, LIMIT, 0) as? List<Any> ?: return emptyList()
        tasks.mapNotNull { task ->
            val field = runCatching {
                task.javaClass.getDeclaredField("baseIntent").apply { isAccessible = true }
            }.getOrNull() ?: return@mapNotNull null
            val intent = runCatching { field.get(task) as? Intent }.getOrNull()
            intent?.component?.packageName?.takeIf { it.isNotBlank() }
        }
    }.getOrElse {
        log("recent tasks query failed: ${it.javaClass.simpleName}")
        emptyList()
    }

    private fun log(message: String) {
        runCatching {
            ConfigManager.module()?.let {
                LogUtil.log(it, LogUtil.WARN, "SidebarRecentApps", message)
            }
        }
    }
}
