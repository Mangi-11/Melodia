package com.lin0721.linmusic

import com.lin0721.linmusic.core.preferences.BottomBarItem
import com.lin0721.linmusic.core.preferences.BottomBarItemSetting

// 底栏候选项目与导航根页面的双向映射（仅涉及 7 个根页面，创建入口无对应页面）

fun BottomBarItem.toRootScreen(): Screen? = when (this) {
    BottomBarItem.HOME -> Screen.Home
    BottomBarItem.SEARCH -> Screen.Search
    BottomBarItem.LIBRARY -> Screen.Library
    BottomBarItem.LOCAL_MUSIC -> Screen.LocalMusic
    BottomBarItem.DOWNLOADS -> Screen.Downloads
    BottomBarItem.CLOUD -> Screen.Cloud
    BottomBarItem.PROFILE -> Screen.MyProfile
    BottomBarItem.CREATE -> null
}

fun Screen.toRootTabKey(): String? = when (this) {
    Screen.Home -> BottomBarItem.HOME.key
    Screen.Search -> BottomBarItem.SEARCH.key
    Screen.Library -> BottomBarItem.LIBRARY.key
    Screen.LocalMusic -> BottomBarItem.LOCAL_MUSIC.key
    Screen.Downloads -> BottomBarItem.DOWNLOADS.key
    Screen.Cloud -> BottomBarItem.CLOUD.key
    Screen.MyProfile -> BottomBarItem.PROFILE.key
    else -> null
}

fun rootScreenFromKey(key: String?): Screen? = key?.let { BottomBarItem.fromKey(it)?.toRootScreen() }

// 底栏第一个可见的根页面，缺省回主页
fun firstVisibleRootScreen(layout: List<BottomBarItemSetting>): Screen =
    layout.firstOrNull { it.visible && it.item.isRoute }?.item?.toRootScreen() ?: Screen.Home
