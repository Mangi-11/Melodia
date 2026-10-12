package com.lin0721.linmusic.desktop.data

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.OnlineStateProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.NetworkInterface

private const val TAG = "DesktopOnlineState"
private const val POLL_INTERVAL_MS = 5_000L
private val VIRTUAL_ADAPTER_HINTS = listOf("virtual", "vmware", "hyper-v", "vethernet", "vbox", "loopback", "tap-", "tunnel", "wsl")

// 以本机网卡是否存在可用连接判断，不发探测请求；定时轮询以感知网络切换
class DesktopOnlineStateProvider(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : OnlineStateProvider {

    private val _online = MutableStateFlow(probe())
    override val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                _online.value = probe()
            }
        }
    }

    // 枚举失败时按在线处理，避免误判离线而拦截正常请求
    private fun probe(): Boolean = try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return false
        interfaces.asSequence().any { nic ->
            nic.isUp && !nic.isLoopback && !nic.isVirtual && !isVirtualAdapter(nic) &&
                nic.inetAddresses.asSequence().any(::isUsableAddress)
        }
    } catch (e: Exception) {
        AppLogger.w(TAG, "网卡枚举异常，按在线处理", e)
        true
    }

    private fun isVirtualAdapter(nic: NetworkInterface): Boolean {
        val name = nic.displayName.orEmpty().lowercase()
        return VIRTUAL_ADAPTER_HINTS.any { name.contains(it) }
    }

    private fun isUsableAddress(address: InetAddress): Boolean =
        !address.isLoopbackAddress && !address.isLinkLocalAddress && !address.isAnyLocalAddress
}
