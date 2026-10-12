package com.lin0721.linmusic.desktop.platform.windows

import com.lin0721.linmusic.desktop.platform.AutoStartService

private const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
private const val VALUE_NAME = "Melodia"

// 开机自启以注册表 Run 项为准
internal class WindowsAutoStart : AutoStartService {

    // jpackage 启动器注入的 exe 路径，开发环境运行时为空
    private val exePath: String? = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }

    override val unavailableReason: String? = if (exePath == null) "仅安装版可用" else null

    override fun isEnabled(): Boolean {
        val path = exePath ?: return false
        val result = RegistryCli.run("query", RUN_KEY, "/v", VALUE_NAME) ?: return false
        return result.exitCode == 0 && result.output.contains(path, ignoreCase = true)
    }

    override fun setEnabled(enabled: Boolean): Boolean {
        val path = exePath ?: return false
        val result = if (enabled) {
            RegistryCli.run("add", RUN_KEY, "/v", VALUE_NAME, "/t", "REG_SZ", "/d", "\"$path\"", "/f")
        } else {
            RegistryCli.run("delete", RUN_KEY, "/v", VALUE_NAME, "/f")
        }
        return result?.exitCode == 0
    }
}
