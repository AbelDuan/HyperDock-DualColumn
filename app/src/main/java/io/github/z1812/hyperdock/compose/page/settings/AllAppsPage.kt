package io.github.z1812.hyperdock.compose.page.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.z1812.hyperdock.PrefKeys
import io.github.z1812.hyperdock.R
import io.github.z1812.hyperdock.compose.component.DetailPage
import io.github.z1812.hyperdock.compose.component.PreferenceDropdown
import io.github.z1812.hyperdock.compose.component.PreferenceSwitch
import io.github.z1812.hyperdock.compose.component.SectionTitle
import io.github.z1812.hyperdock.compose.component.SettingsActionWithArrow
import io.github.z1812.hyperdock.compose.data.PrefsRepository
import io.github.z1812.hyperdock.compose.data.rememberBooleanPreference
import io.github.z1812.hyperdock.compose.data.rememberStringPreference
import io.github.z1812.hyperdock.compose.data.rememberStringSetPreference
import io.github.z1812.hyperdock.utils.IconNormalizer
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val MODE_WHITELIST = "whitelist"
private const val MODE_BLACKLIST = "blacklist"
private const val RECENT_OPEN = "recent_open"
private const val RECENT_TASK = "recent_task"
private val RECENT_SOURCES = listOf(RECENT_OPEN, RECENT_TASK)
private const val DEFAULT_RECENT_ORDER = "recent_open,recent_task"

@Composable
internal fun AllAppsPage(
    prefs: PrefsRepository,
    onBack: () -> Unit,
    onOpenApps: () -> Unit,
) {
    val enabled = rememberBooleanPreference(prefs, PrefKeys.ALL_APPS_CUSTOM_ENABLED, false)
    val mode = rememberStringPreference(prefs, PrefKeys.ALL_APPS_CUSTOM_MODE, MODE_BLACKLIST)
    val showSystem = rememberBooleanPreference(prefs, PrefKeys.ALL_APPS_SHOW_SYSTEM, true)
    val innerColumns = remember { mutableStateOf(prefs.getInt(PrefKeys.ALL_APPS_PANEL_COLUMNS_INNER, 0)) }
    val outerColumns = remember { mutableStateOf(prefs.getInt(PrefKeys.ALL_APPS_PANEL_COLUMNS_OUTER, 0)) }
    val columnLabels = listOf(
        stringResource(R.string.all_apps_columns_auto),
        "3", "4", "5", "6",
    )
    val recentOpen = rememberBooleanPreference(prefs, PrefKeys.ALL_APPS_RECENT_OPEN_ENABLED, false)
    val recentTask = rememberBooleanPreference(prefs, PrefKeys.ALL_APPS_RECENT_TASK_ENABLED, false)
    val recentOrder = rememberStringPreference(
        prefs,
        PrefKeys.ALL_APPS_RECENT_ORDER,
        DEFAULT_RECENT_ORDER,
    )
    val orderedSources = remember(recentOrder.value) {
        val saved = recentOrder.value.split(',').map(String::trim)
        (saved + RECENT_SOURCES).distinct().filter { it in RECENT_SOURCES }
    }
    fun sourceEnabled(id: String): Boolean = if (id == RECENT_OPEN) recentOpen.value else recentTask.value
    fun setSourceEnabled(id: String, checked: Boolean) {
        if (id == RECENT_OPEN) {
            recentOpen.value = checked
            prefs.putBoolean(PrefKeys.ALL_APPS_RECENT_OPEN_ENABLED, checked)
        } else {
            recentTask.value = checked
            prefs.putBoolean(PrefKeys.ALL_APPS_RECENT_TASK_ENABLED, checked)
        }
    }
    fun moveSource(id: String, delta: Int) {
        val next = orderedSources.toMutableList()
        val index = next.indexOf(id)
        val target = index + delta
        if (index < 0 || target !in next.indices) return
        next[index] = next[target]
        next[target] = id
        recentOrder.value = next.joinToString(",")
        prefs.putString(PrefKeys.ALL_APPS_RECENT_ORDER, recentOrder.value)
    }

    DetailPage(title = stringResource(R.string.all_apps), onBack = onBack) {
        item {
            SectionTitle(stringResource(R.string.all_apps))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.all_apps_custom_enabled),
                    summary = stringResource(R.string.all_apps_custom_enabled_summary),
                    icon = null,
                    checked = enabled.value,
                ) {
                    enabled.value = it
                    prefs.putBoolean(PrefKeys.ALL_APPS_CUSTOM_ENABLED, it)
                }
                // 系统应用过滤只在黑名单模式有意义：白名单本身就是“仅显示所选”。
                AnimatedVisibility(visible = mode.value != MODE_WHITELIST) {
                    PreferenceSwitch(
                        title = stringResource(R.string.all_apps_show_system),
                        summary = stringResource(R.string.all_apps_show_system_summary),
                        icon = null,
                        checked = showSystem.value,
                        enabled = enabled.value,
                    ) {
                        showSystem.value = it
                        prefs.putBoolean(PrefKeys.ALL_APPS_SHOW_SYSTEM, it)
                    }
                }
                PreferenceDropdown(
                    title = stringResource(R.string.all_apps_custom_mode),
                    summary = null,
                    icon = null,
                    items = listOf(
                        stringResource(R.string.all_apps_blacklist),
                        stringResource(R.string.all_apps_whitelist),
                    ),
                    selectedIndex = if (mode.value == MODE_WHITELIST) 1 else 0,
                    enabled = enabled.value,
                ) { index ->
                    val next = if (index == 1) MODE_WHITELIST else MODE_BLACKLIST
                    mode.value = next
                    prefs.putString(PrefKeys.ALL_APPS_CUSTOM_MODE, next)
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.all_apps_recent_title))
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.all_apps_recent_summary),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
                orderedSources.forEach { id ->
                    val index = orderedSources.indexOf(id)
                    val label = stringResource(
                        if (id == RECENT_OPEN) R.string.all_apps_sort_recent_open
                        else R.string.all_apps_sort_recent_task,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, modifier = Modifier.weight(1f), color = MiuixTheme.colorScheme.onSurface)
                        Checkbox(
                            state = if (sourceEnabled(id)) ToggleableState.On else ToggleableState.Off,
                            onClick = { setSourceEnabled(id, !sourceEnabled(id)) },
                        )
                        CircleMoveButton(90f, index > 0, stringResource(R.string.sidebar_section_move_up)) {
                            moveSource(id, -1)
                        }
                        CircleMoveButton(
                            270f,
                            index < orderedSources.lastIndex,
                            stringResource(R.string.sidebar_section_move_down),
                        ) { moveSource(id, 1) }
                    }
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.all_apps_columns_title))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceDropdown(
                    title = stringResource(R.string.all_apps_columns_inner),
                    summary = stringResource(R.string.all_apps_columns_summary),
                    icon = null,
                    items = columnLabels,
                    selectedIndex = innerColumns.value.coerceIn(3, 6) - 3,
                    enabled = true,
                ) { index ->
                    val columns = index + 3
                    innerColumns.value = columns
                    prefs.putInt(PrefKeys.ALL_APPS_PANEL_COLUMNS_INNER, columns)
                }
                PreferenceDropdown(
                    title = stringResource(R.string.all_apps_columns_outer),
                    summary = stringResource(R.string.all_apps_columns_summary),
                    icon = null,
                    items = columnLabels,
                    selectedIndex = outerColumns.value.coerceIn(3, 6) - 3,
                    enabled = true,
                ) { index ->
                    val columns = index + 3
                    outerColumns.value = columns
                    prefs.putInt(PrefKeys.ALL_APPS_PANEL_COLUMNS_OUTER, columns)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsActionWithArrow(
                    title = stringResource(R.string.all_apps_custom_apps),
                    summary = stringResource(R.string.all_apps_custom_apps_summary),
                    icon = null,
                    onClick = onOpenApps,
                )
            }
        }
    }
}

@Composable
internal fun CustomAppsPage(prefs: PrefsRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val selected = rememberStringSetPreference(prefs, PrefKeys.ALL_APPS_CUSTOM_PACKAGES)
    val appsState = produceState<List<LaunchableApp>?>(initialValue = null, key1 = context) {
        value = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }
    val apps = appsState.value
    // 进入页面时把已勾选应用置顶；勾选过程中只改变 Checkbox，不立即重排列表。
    val orderedApps = remember(apps) {
        apps?.let { source ->
            val selectedAtLoad = selected.value
            source.sortedByDescending { it.packageName in selectedAtLoad }
        }
    }
    var query by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    // 名单勾选页始终列出全部应用：系统应用是否显示在侧边栏由黑名单模式的开关决定，
    // 与名单内容本身无关，系统应用同样可以被加入黑名单。
    val filteredApps = remember(orderedApps, query) {
        val source = orderedApps ?: emptyList()
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) source else source.filter {
            it.label.lowercase().contains(needle) || it.packageName.lowercase().contains(needle)
        }
    }

    fun toggle(packageName: String) {
        val next = if (packageName in selected.value) {
            selected.value - packageName
        } else {
            selected.value + packageName
        }
        selected.value = next
        // 只更新配置状态，不重新加载/重排当前页面。
        prefs.putStringSet(PrefKeys.ALL_APPS_CUSTOM_PACKAGES, next)
    }

    DetailPage(title = stringResource(R.string.all_apps_custom_apps), onBack = onBack) {
        item {
            SearchBar(
                inputField = {
                    InputField(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = {},
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                        label = stringResource(R.string.search_apps),
                    )
                },
                onExpandedChange = { searchExpanded = it },
                expanded = searchExpanded,
                outsideEndAction = {
                    Text(
                        text = stringResource(R.string.cancel),
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .clickable(
                                interactionSource = null,
                                indication = null,
                            ) { searchExpanded = false },
                        color = MiuixTheme.colorScheme.primary,
                    )
                },
            ) {}
        }
        if (apps == null) {
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(size = 28.dp)
                    }
                }
            }
        } else if (filteredApps.isEmpty()) {
            item { Text(stringResource(R.string.all_apps_no_apps)) }
        } else {
            items(filteredApps.size, key = { filteredApps[it].packageName }) { index ->
                val app = filteredApps[index]
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { toggle(app.packageName) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app.icon, Modifier.size(44.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = app.packageName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Checkbox(
                            state = ToggleableState(app.packageName in selected.value),
                            onClick = { toggle(app.packageName) },
                        )
                    }
                }
            }
        }
    }
}

private data class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: Bitmap,
)

private fun loadLaunchableApps(context: Context): List<LaunchableApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val pm = context.packageManager
    return pm.queryIntentActivities(intent, 0)
        .mapNotNull { resolve ->
            val info = resolve.activityInfo ?: return@mapNotNull null
            val label = runCatching { info.loadLabel(pm).toString() }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: info.packageName
            val icon = runCatching { info.loadIcon(pm).toBitmap(context) }.getOrNull()
                ?: return@mapNotNull null
            LaunchableApp(info.packageName, label, icon)
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}

private fun Drawable.toBitmap(context: Context): Bitmap {
    val size = (48 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    // 按固有比例 contain，不能 setBounds 到整幅：Drawable 会拉伸填满 bounds，
    // 长方形应用图标会被压成正方形。
    val bounds = IconNormalizer.containBounds(this, size)
    setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
    draw(Canvas(bitmap))
    return bitmap
}

@Composable
private fun AppIcon(icon: Bitmap, modifier: Modifier) {
    Image(
        bitmap = icon.asImageBitmap(),
        contentDescription = null,
        modifier = modifier,
    )
}

@Composable
private fun CircleMoveButton(
    iconRotation: Float,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(start = 4.dp)
            .size(36.dp)
            .background(
                color = if (enabled) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.primary.copy(alpha = 0.35f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = MiuixIcons.Back,
                contentDescription = description,
                modifier = Modifier.size(18.dp).graphicsLayer(rotationZ = iconRotation),
                tint = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}
