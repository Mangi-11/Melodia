package com.lin0721.linmusic.core.source

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LxArchiveReaderTest {

    private fun zip(entries: List<Pair<String, String>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                if (!name.endsWith("/")) zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun sevenZ(entries: List<Pair<String, String>>): ByteArray {
        val channel = SeekableInMemoryByteChannel()
        SevenZOutputFile(channel).use { sz ->
            entries.forEach { (name, content) ->
                val entry = SevenZArchiveEntry().apply {
                    this.name = name
                    isDirectory = false
                    size = content.toByteArray().size.toLong()
                }
                sz.putArchiveEntry(entry)
                sz.write(content.toByteArray())
                sz.closeArchiveEntry()
            }
        }
        return channel.array().copyOf(channel.size().toInt())
    }

    @Test
    fun `zip 内只取 js 条目`() {
        val bytes = zip(listOf("a.js" to "// a", "readme.md" to "x", "sub/b.JS" to "// b", "dir/" to ""))
        val scripts = LxArchiveReader.readScripts("pack.zip", bytes).getOrThrow()
        assertEquals(listOf("a.js", "sub/b.JS"), scripts.map { it.path })
        assertEquals("// a", scripts[0].content)
    }

    @Test
    fun `7z 内只取 js 条目`() {
        val bytes = sevenZ(listOf("a.js" to "// a", "notes.txt" to "x", "lib/c.js" to "// c"))
        val scripts = LxArchiveReader.readScripts("pack.7z", bytes).getOrThrow()
        assertEquals(listOf("a.js", "lib/c.js"), scripts.map { it.path })
        assertEquals("// c", scripts[1].content)
    }

    @Test
    fun `按文件头而不是扩展名识别压缩包`() {
        val bytes = zip(listOf("a.js" to "// a"))
        assertEquals(1, LxArchiveReader.readScripts("renamed.bin", bytes).getOrThrow().size)
    }

    @Test
    fun `普通脚本文件原样返回并去掉 BOM`() {
        val bytes = "﻿// plain".toByteArray()
        val scripts = LxArchiveReader.readScripts("plugin.js", bytes).getOrThrow()
        assertEquals("// plain", scripts.single().content)
        assertEquals("plugin.js", scripts.single().path)
    }

    @Test
    fun `没有 js 时报错`() {
        val result = LxArchiveReader.readScripts("pack.zip", zip(listOf("readme.md" to "x")))
        assertEquals("压缩包内没有找到 .js 脚本", result.exceptionOrNull()?.message)
    }

    @Test
    fun `跳过 macOS 资源文件与含 dotdot 的条目`() {
        val bytes = zip(
            listOf(
                "__MACOSX/._a.js" to "x",
                "dir/._b.js" to "x",
                "../evil.js" to "x",
                "a/../../evil2.js" to "x",
                "ok.js" to "// ok"
            )
        )
        val scripts = LxArchiveReader.readScripts("pack.zip", bytes).getOrThrow()
        assertEquals(listOf("ok.js"), scripts.map { it.path })
    }

    @Test
    fun `单个脚本超过 5MB 被拒绝`() {
        val big = "a".repeat(LxArchiveReader.MAX_SCRIPT_BYTES + 1)
        val result = LxArchiveReader.readScripts("pack.zip", zip(listOf("big.js" to big)))
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("5 MB"))
    }

    @Test
    fun `条目数超过上限被拒绝`() {
        val entries = (0..LxArchiveReader.MAX_ENTRIES).map { "f$it.txt" to "x" }
        val result = LxArchiveReader.readScripts("pack.zip", zip(entries))
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("文件过多"))
    }

    @Test
    fun `加密 zip 提示不支持`() {
        val bytes = zip(listOf("a.js" to "// a"))
        // 把中央目录里的通用标志位第 0 位置 1，等同于声明条目已加密
        for (i in 0 until bytes.size - 4) {
            if (bytes[i] == 0x50.toByte() && bytes[i + 1] == 0x4B.toByte() && bytes[i + 2] == 0x01.toByte() && bytes[i + 3] == 0x02.toByte()) {
                bytes[i + 8] = (bytes[i + 8].toInt() or 1).toByte()
            }
        }
        val result = LxArchiveReader.readScripts("pack.zip", bytes)
        assertEquals("暂不支持加密压缩包", result.exceptionOrNull()?.message)
    }

    @Test
    fun `损坏的压缩包与空文件报错`() {
        assertTrue(LxArchiveReader.readScripts("a.zip", "not an archive".toByteArray()).isFailure)
        assertTrue(LxArchiveReader.readScripts("a.js", ByteArray(0)).isFailure)
        val truncated = zip(listOf("a.js" to "// a")).copyOf(20)
        assertTrue(LxArchiveReader.readScripts("a.zip", truncated).isFailure)
    }

    @Test
    fun `压缩包本身超过 50MB 被拒绝`() {
        val bytes = ByteArray((LxArchiveReader.MAX_ARCHIVE_BYTES + 1).toInt())
        assertTrue(LxArchiveReader.readScripts("a.zip", bytes).isFailure)
    }
}
