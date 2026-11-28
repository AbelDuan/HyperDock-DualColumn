package io.github.z1812.hyperdock.xposed.hook.Sidebar

/**
 * 原生侧边栏编辑态标记。
 *
 * 编辑态下宿主会按自己持有的列表实例做增删；我们此时若重建列表，
 * 宿主会把“列表被外部替换”当成编辑取消，表现为点添加闪一下就退出。
 * 检测到编辑态后，所有注入/重排都暂停，原样放行。
 */
internal object SidebarEditGuard {
    @Volatile
    var active: Boolean = false

    fun reset() {
        active = false
    }
}
