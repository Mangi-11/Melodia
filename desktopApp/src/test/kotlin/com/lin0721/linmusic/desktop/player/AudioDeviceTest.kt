package com.lin0721.linmusic.desktop.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDeviceTest {

    @Test
    fun keepsOnlyWasapiDevicesWithSystemDefaultFirst() {
        val json = """
            [
              {"name":"openal","description":"OpenAL"},
              {"name":"wasapi/{a}","description":"扬声器 (Realtek High Definition Audio)"},
              {"name":"auto","description":"Autoselect device"},
              {"name":"wasapi/{b}","description":"耳机 (AirPods Pro)"}
            ]
        """.trimIndent()

        val devices = parseAudioDevices(json)

        assertEquals(listOf(AUTO_AUDIO_DEVICE, "wasapi/{a}", "wasapi/{b}"), devices.map { it.name })
        assertTrue(devices.first().isSystemDefault)
    }

    @Test
    fun fallsBackToAllDevicesWhenNoWasapi() {
        val devices = parseAudioDevices("""[{"name":"auto"},{"name":"openal","description":"OpenAL"}]""")

        assertEquals(listOf(AUTO_AUDIO_DEVICE, "openal"), devices.map { it.name })
    }

    @Test
    fun skipsMalformedEntriesAndDuplicates() {
        val json = """[{"description":"无名称"},{"name":""},"字符串",{"name":"wasapi/{a}"},{"name":"wasapi/{a}"}]"""

        val devices = parseAudioDevices(json)

        assertEquals(listOf("wasapi/{a}"), devices.map { it.name })
        assertEquals("wasapi/{a}", devices.single().description)
    }

    @Test
    fun invalidJsonYieldsEmptyList() {
        assertTrue(parseAudioDevices("不是 JSON").isEmpty())
        assertTrue(parseAudioDevices("""{"name":"auto"}""").isEmpty())
    }

    @Test
    fun classifiesByDescriptionKeywords() {
        assertEquals(AudioDeviceKind.SYSTEM_DEFAULT, classifyAudioDevice(AUTO_AUDIO_DEVICE, "任意"))
        assertEquals(AudioDeviceKind.HEADPHONES, classifyAudioDevice("wasapi/1", "耳机 (AirPods Pro)"))
        assertEquals(AudioDeviceKind.HEADPHONES, classifyAudioDevice("wasapi/2", "Bluetooth Headset"))
        assertEquals(AudioDeviceKind.DISPLAY, classifyAudioDevice("wasapi/3", "DELL U2720Q (NVIDIA High Definition Audio)"))
        assertEquals(AudioDeviceKind.SPEAKER, classifyAudioDevice("wasapi/4", "扬声器 (Realtek High Definition Audio)"))
    }

    @Test
    fun splitsDescriptionIntoTitleAndSubtitle() {
        val device = AudioDevice("wasapi/1", "扬声器 (Realtek High Definition Audio)", AudioDeviceKind.SPEAKER)
        assertEquals("扬声器", device.title)
        assertEquals("Realtek High Definition Audio", device.subtitle)

        val plain = AudioDevice("wasapi/2", "USB Audio", AudioDeviceKind.SPEAKER)
        assertEquals("USB Audio", plain.title)
        assertNull(plain.subtitle)

        val auto = AudioDevice(AUTO_AUDIO_DEVICE, "Autoselect device", AudioDeviceKind.SYSTEM_DEFAULT)
        assertEquals("系统默认", auto.title)
        assertNull(auto.subtitle)
    }
}
