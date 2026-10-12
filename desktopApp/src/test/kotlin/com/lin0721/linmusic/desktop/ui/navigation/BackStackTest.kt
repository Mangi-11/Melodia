package com.lin0721.linmusic.desktop.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackStackTest {

    @Test
    fun startsAtRootWithoutHistory() {
        val stack = BackStack(DesktopRoute.Home)

        assertEquals(DesktopRoute.Home, stack.current)
        assertFalse(stack.canGoBack)
        assertFalse(stack.canGoForward)
    }

    @Test
    fun navigatingToSameRouteIsIgnored() {
        val stack = BackStack(DesktopRoute.Home)
        val entry = stack.currentEntry

        stack.navigate(DesktopRoute.Home)

        assertEquals(entry, stack.currentEntry)
        assertFalse(stack.canGoBack)
    }

    @Test
    fun backAndForwardMoveBetweenStacks() {
        val stack = BackStack(DesktopRoute.Home)
        stack.navigate(DesktopRoute.Search)
        stack.navigate(DesktopRoute.Settings)

        stack.back()
        assertEquals(DesktopRoute.Search, stack.current)
        assertTrue(stack.canGoForward)

        stack.forward()
        assertEquals(DesktopRoute.Settings, stack.current)
        assertFalse(stack.canGoForward)
    }

    @Test
    fun newNavigationClearsForwardStack() {
        val stack = BackStack(DesktopRoute.Home)
        stack.navigate(DesktopRoute.Search)
        stack.back()

        stack.navigate(DesktopRoute.Browse)

        assertFalse(stack.canGoForward)
        stack.forward()
        assertEquals(DesktopRoute.Browse, stack.current)
    }

    @Test
    fun backAtRootAndForwardWithoutHistoryAreNoOps() {
        val stack = BackStack(DesktopRoute.Home)

        stack.back()
        stack.forward()

        assertEquals(DesktopRoute.Home, stack.current)
    }

    // 同一路由在历史里出现两次时栈帧 id 不同，各自保留页面现场
    @Test
    fun repeatedRouteGetsDistinctEntries() {
        val stack = BackStack(DesktopRoute.Home)
        stack.navigate(DesktopRoute.Search)
        val first = stack.currentEntry
        stack.navigate(DesktopRoute.Settings)
        stack.navigate(DesktopRoute.Search)

        assertEquals(first.route, stack.currentEntry.route)
        assertNotEquals(first.id, stack.currentEntry.id)
    }

    @Test
    fun liveEntriesIncludeForwardStackAndDropDiscarded() {
        val stack = BackStack(DesktopRoute.Home)
        stack.navigate(DesktopRoute.Search)
        val search = stack.currentEntry.id
        stack.back()
        assertTrue(search in stack.liveEntryIds)

        stack.navigate(DesktopRoute.Settings)

        assertFalse(search in stack.liveEntryIds)
        assertEquals(2, stack.liveEntryIds.size)
    }
}
