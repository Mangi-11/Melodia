package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.source.AudioSourceProvider
import com.lin0721.linmusic.core.source.LxAudioSourceProvider
import com.lin0721.linmusic.core.source.LxPluginRuntime
import com.lin0721.linmusic.feature.source.plugin.LxPluginEngine
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

// LX Music 插件沙盒与音源注入模块
val lxPluginModule = module {
    single { LxPluginEngine(androidContext()) } bind LxPluginRuntime::class
    single<AudioSourceProvider>(named("lx")) {
        LxAudioSourceProvider(get<LxPluginRuntime>(), get())
    }
}
