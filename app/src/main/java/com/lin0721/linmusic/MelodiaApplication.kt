package com.lin0721.linmusic

import android.app.Application
import android.app.NotificationManager
import androidx.work.Configuration
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.lin0721.linmusic.core.download.DownloadWorkerFactory
import com.lin0721.linmusic.core.AppEnvironment
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.log.CrashHandler
import com.lin0721.linmusic.core.log.init
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.update.UpdateChecker
import com.lin0721.linmusic.core.vehicle.LeapmotorMediaClaim
import com.lin0721.linmusic.core.vehicle.LeapmotorSteeringControl
import com.lin0721.linmusic.di.downloadModule
import com.lin0721.linmusic.di.localModule
import com.lin0721.linmusic.di.localMusicModule
import com.lin0721.linmusic.di.lxPluginModule
import com.lin0721.linmusic.di.networkModule
import com.lin0721.linmusic.di.playerModule
import com.lin0721.linmusic.di.recognitionModule
import com.lin0721.linmusic.di.repositoryModule
import com.lin0721.linmusic.di.sourceModule
import com.lin0721.linmusic.core.source.SourcePreferences
import com.lin0721.linmusic.feature.source.plugin.LxPluginEngine
import kotlinx.coroutines.flow.first
import com.lin0721.linmusic.di.updateModule
import com.lin0721.linmusic.di.viewModelModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.android.ext.android.inject
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class MelodiaApplication : Application() {

    private val updateChecker: UpdateChecker by inject()
    private val downloadWorkerFactory: DownloadWorkerFactory by inject()
    private val playbackController = inject<PlaybackController>()
    private val settingsPreferences: SettingsPreferences by inject()

    override fun onCreate() {
        super.onCreate()
        // 尽早初始化，覆盖 Koin/Coil 启动阶段的崩溃与日志
        AppEnvironment.isDebug = BuildConfig.DEBUG
        AppLogger.init(this)
        CrashHandler.init(this)

        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(this, 0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizePercent(0.02)
                    .build()
            }
            .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(4))
            .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(8))
            .crossfade(true)
            .build()
        SingletonImageLoader.setSafe { imageLoader }
        // 在后台线程强制触发 DiskLruCache.initialize()，避免首次图片加载时锁竞争
        Thread { imageLoader.diskCache }.start()

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.ERROR)
            androidContext(this@MelodiaApplication)
            modules(
                networkModule, localModule, repositoryModule, sourceModule, lxPluginModule, viewModelModule, playerModule,
                updateModule(BuildConfig.VERSION_CODE), downloadModule, localMusicModule, recognitionModule
            )
        }

        // 异步预加载已保存启用的 LX 音源插件
        val sourcePreferences: SourcePreferences by inject()
        val lxPluginEngine: LxPluginEngine by inject()
        CoroutineScope(Dispatchers.IO).launch {
            val script = sourcePreferences.lxScriptContent.first()
            if (script.isNotBlank() && sourcePreferences.lxPluginEnabled.first()) {
                lxPluginEngine.loadScript(script)
            }
        }

        // 初始化 WorkManager 并注入自定义 WorkerFactory
        WorkManager.initialize(
            this,
            Configuration.Builder()
                .setWorkerFactory(downloadWorkerFactory)
                .build()
        )

        // 零跑车机方向盘：必须进程级注册，任何入口启动都要生效
        val mediaClaim = LeapmotorMediaClaim(this, playbackController, settingsPreferences)
        mediaClaim.start()
        LeapmotorSteeringControl(this, playbackController, onKeyReceived = mediaClaim::publishNow).register()

        // 延迟几秒后台检查更新，避开启动关键路径；进程生命周期内只检查这一次
        CoroutineScope(Dispatchers.IO).launch {
            launch { updateChecker.messages.collect(ToastManager::showToast) }
            delay(3000)
            updateChecker.checkForUpdate(manual = false)
        }
        // 旧版内置下载遗留的更新通知渠道
        getSystemService(NotificationManager::class.java)?.deleteNotificationChannel(LEGACY_UPDATE_CHANNEL_ID)
    }

    private companion object {
        const val LEGACY_UPDATE_CHANNEL_ID = "app_update"
    }
}
