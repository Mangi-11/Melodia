package com.lin0721.linmusic.desktop.platform

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseCookiesTest {

    private fun cookies(json: String): JsonArray = Json.parseToJsonElement(json).jsonArray

    @Test
    fun keepsOnlyNeteaseDomains() {
        val result = neteaseCookiesOf(
            cookies(
                """
                [
                  {"name":"MUSIC_U","value":"abc","domain":".music.163.com"},
                  {"name":"__csrf","value":"xyz","domain":"music.163.com"},
                  {"name":"NMTID","value":"1","domain":"163.com"},
                  {"name":"MUID","value":"edge","domain":".microsoft.com"},
                  {"name":"FAKE","value":"x","domain":"evil163.com"}
                ]
                """.trimIndent()
            )
        )

        assertEquals(linkedMapOf("MUSIC_U" to "abc", "__csrf" to "xyz", "NMTID" to "1"), result)
    }

    @Test
    fun skipsMalformedEntries() {
        val result = neteaseCookiesOf(
            cookies(
                """
                [
                  "字符串",
                  {"value":"无名","domain":"music.163.com"},
                  {"name":"","value":"空名","domain":"music.163.com"},
                  {"name":"NO_VALUE","domain":"music.163.com"},
                  {"name":"NO_DOMAIN","value":"1"},
                  {"name":"NULL_VALUE","value":null,"domain":"music.163.com"}
                ]
                """.trimIndent()
            )
        )

        assertTrue(result.isEmpty())
    }

    // 同名 Cookie 以后出现的为准，顺序保持首次出现的位置
    @Test
    fun laterDuplicateWins() {
        val result = neteaseCookiesOf(
            cookies(
                """
                [
                  {"name":"MUSIC_U","value":"old","domain":"music.163.com"},
                  {"name":"__csrf","value":"c","domain":"music.163.com"},
                  {"name":"MUSIC_U","value":"new","domain":".163.com"}
                ]
                """.trimIndent()
            )
        )

        assertEquals(listOf("MUSIC_U" to "new", "__csrf" to "c"), result.toList())
    }
}
