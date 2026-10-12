package com.lin0721.linmusic.desktop.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualAdapterTest {

    @Test
    fun recognisesCommonVirtualAdapters() {
        listOf(
            "VMware Virtual Ethernet Adapter for VMnet8",
            "Hyper-V Virtual Ethernet Adapter",
            "vEthernet (WSL)",
            "VirtualBox Host-Only Ethernet Adapter",
            "TAP-Windows Adapter V9",
            "Software Loopback Interface 1"
        ).forEach { assertTrue(it, isVirtualAdapterName(it)) }
    }

    @Test
    fun physicalAdaptersAreNotVirtual() {
        listOf(
            "Intel(R) Wi-Fi 6E AX211 160MHz",
            "Realtek PCIe 2.5GbE Family Controller"
        ).forEach { assertFalse(it, isVirtualAdapterName(it)) }
        assertFalse(isVirtualAdapterName(null))
    }
}
