package com.lin0721.linmusic.desktop.platform.windows

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.desktop.platform.GlobalHotkeyService
import com.lin0721.linmusic.desktop.platform.HotkeyAction
import com.lin0721.linmusic.desktop.platform.HotkeyCombo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CountDownLatch
import javax.swing.SwingUtilities

private const val TAG = "GlobalHotkeys"

private const val VK_MEDIA_NEXT_TRACK = 0xB0
private const val VK_MEDIA_PREV_TRACK = 0xB1
private const val VK_MEDIA_PLAY_PAUSE = 0xB3

private const val MEDIA_ID_BASE = 1
private const val CUSTOM_ID_BASE = 100

// 投递给热键线程的自定义消息
private const val MSG_RELOAD = User32.WM_APP + 1
private const val MSG_PAUSE = User32.WM_APP + 2
private const val MSG_RESUME = User32.WM_APP + 3

private data class Binding(val id: Int, val modifiers: Int, val vk: Int, val action: HotkeyAction, val custom: Boolean)

private val mediaBindings = listOf(
    Binding(MEDIA_ID_BASE, 0, VK_MEDIA_PLAY_PAUSE, HotkeyAction.PlayPause, custom = false),
    Binding(MEDIA_ID_BASE + 1, 0, VK_MEDIA_NEXT_TRACK, HotkeyAction.Next, custom = false),
    Binding(MEDIA_ID_BASE + 2, 0, VK_MEDIA_PREV_TRACK, HotkeyAction.Previous, custom = false)
)

// 热键注册与 WM_HOTKEY 必须在同一线程，因此独占一条线程跑消息循环；配置变更通过线程消息通知重新注册
internal class Win32GlobalHotkeys : GlobalHotkeyService {

    override val isSupported: Boolean = true

    @Volatile override var onAction: ((HotkeyAction) -> Unit)? = null

    @Volatile private var customConfig: Map<HotkeyAction, HotkeyCombo?> = HotkeyCombo.defaults
    @Volatile private var mediaKeysEnabled = true

    private val _failed = MutableStateFlow<Set<HotkeyAction>>(emptySet())
    override val failed: StateFlow<Set<HotkeyAction>> = _failed.asStateFlow()

    @Volatile private var threadId = 0
    private var thread: Thread? = null

    override fun start() {
        if (thread != null) return
        val ready = CountDownLatch(1)
        thread = Thread({ runLoop(ready) }, "global-hotkeys").apply {
            isDaemon = true
            start()
        }
        ready.await()
    }

    override fun stop() {
        post(User32.WM_QUIT)
        thread?.join(1000)
        thread = null
    }

    override fun apply(custom: Map<HotkeyAction, HotkeyCombo?>, mediaKeys: Boolean) {
        customConfig = custom
        mediaKeysEnabled = mediaKeys
        post(MSG_RELOAD)
    }

    override fun pause() = post(MSG_PAUSE)

    override fun resume() = post(MSG_RESUME)

    private fun post(message: Int) {
        val id = threadId
        if (id != 0) User32.INSTANCE.PostThreadMessageW(id, message, 0, 0)
    }

    private fun runLoop(ready: CountDownLatch) {
        val user32 = try {
            User32.INSTANCE.also { threadId = Kernel32.INSTANCE.GetCurrentThreadId() }
        } catch (e: UnsatisfiedLinkError) {
            AppLogger.e(TAG, "user32 加载失败，全局快捷键不可用", e)
            ready.countDown()
            return
        }
        var paused = false
        var registered = register(user32)
        ready.countDown()
        val msg = Msg()
        // GetMessage 收到 WM_QUIT 返回 0，出错返回 -1
        while (user32.GetMessageW(msg, null, 0, 0) > 0) {
            when (msg.message) {
                User32.WM_HOTKEY -> {
                    val action = registered.firstOrNull { it.id == msg.wParam.toInt() }?.action ?: continue
                    SwingUtilities.invokeLater { onAction?.invoke(action) }
                }
                MSG_RELOAD -> {
                    unregister(user32, registered)
                    registered = if (paused) emptyList() else register(user32)
                }
                MSG_PAUSE -> {
                    paused = true
                    unregister(user32, registered)
                    registered = emptyList()
                }
                MSG_RESUME -> {
                    if (paused) {
                        paused = false
                        registered = register(user32)
                    }
                }
            }
        }
        unregister(user32, registered)
        threadId = 0
    }

    private fun register(user32: User32): List<Binding> {
        val desired = buildList {
            if (mediaKeysEnabled) addAll(mediaBindings)
            customConfig.forEach { (action, combo) ->
                if (combo != null) add(Binding(CUSTOM_ID_BASE + action.ordinal, toWin32Modifiers(combo.modifiers), combo.vk, action, custom = true))
            }
        }
        val failedActions = mutableSetOf<HotkeyAction>()
        val ok = desired.filter { binding ->
            val modifiers = if (binding.custom) binding.modifiers or User32.MOD_NOREPEAT else binding.modifiers
            user32.RegisterHotKey(null, binding.id, modifiers, binding.vk).also { success ->
                // 被其他程序占用时只跳过该键，不影响其余热键
                if (!success) {
                    AppLogger.w(TAG, "热键已被占用：${binding.action.label}")
                    if (binding.custom) failedActions += binding.action
                }
            }
        }
        _failed.value = failedActions
        return ok
    }

    private fun unregister(user32: User32, bindings: List<Binding>) {
        bindings.forEach { user32.UnregisterHotKey(null, it.id) }
    }
}

// 组合键的键码本就按 Win32 虚拟键码取值，可直接注册；修饰键逐位转换
internal fun toWin32Modifiers(modifiers: Int): Int {
    var result = 0
    if (modifiers and HotkeyCombo.MOD_CTRL != 0) result = result or User32.MOD_CONTROL
    if (modifiers and HotkeyCombo.MOD_ALT != 0) result = result or User32.MOD_ALT
    if (modifiers and HotkeyCombo.MOD_SHIFT != 0) result = result or User32.MOD_SHIFT
    return result
}
