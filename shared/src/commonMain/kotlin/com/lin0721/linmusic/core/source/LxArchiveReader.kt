package com.lin0721.linmusic.core.source

import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import java.io.ByteArrayOutputStream
import java.io.InputStream

// 压缩包内读到的单个脚本，path 仅用于界面展示
data class ArchiveScript(val path: String, val content: String)

// 从 zip、7z 或单个 .js 文件的字节中取出 LX 插件脚本；全程在内存里读取，不落盘
object LxArchiveReader {

    const val MAX_ARCHIVE_BYTES = 50L * 1024 * 1024
    const val MAX_ENTRIES = 1000
    const val MAX_SCRIPT_BYTES = 5 * 1024 * 1024

    // 条目声明的解压后总大小上限，防解压炸弹
    private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 200L * 1024 * 1024

    private val ZIP_MAGICS = listOf(
        byteArrayOf(0x50, 0x4B, 0x03, 0x04),
        byteArrayOf(0x50, 0x4B, 0x05, 0x06)
    )
    private val SEVEN_Z_MAGIC = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)

    private class ArchiveException(message: String) : Exception(message)

    // 从流里读取，超过压缩包上限时不会把整个文件读进内存
    fun readScripts(fileName: String, input: InputStream): Result<List<ArchiveScript>> {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        return try {
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (out.size() + n > MAX_ARCHIVE_BYTES) return Result.failure(Exception("文件超过 50 MB，已拒绝读取"))
                out.write(buffer, 0, n)
            }
            readScripts(fileName, out.toByteArray())
        } catch (e: Exception) {
            Result.failure(Exception("读取文件失败：${e.message.orEmpty()}"))
        }
    }

    fun readScripts(fileName: String, bytes: ByteArray): Result<List<ArchiveScript>> {
        if (bytes.isEmpty()) return Result.failure(Exception("文件为空"))
        if (bytes.size > MAX_ARCHIVE_BYTES) return Result.failure(Exception("文件超过 50 MB，已拒绝读取"))
        return try {
            val lowerName = fileName.lowercase()
            val scripts = when {
                startsWith(bytes, SEVEN_Z_MAGIC) -> readSevenZ(bytes)
                ZIP_MAGICS.any { startsWith(bytes, it) } -> readZip(bytes)
                lowerName.endsWith(".zip") || lowerName.endsWith(".7z") -> throw ArchiveException("不是有效的压缩包")
                else -> listOf(ArchiveScript(fileName, decode(bytes.also { checkScriptSize(it.size.toLong()) })))
            }
            if (scripts.isEmpty()) Result.failure(Exception("压缩包内没有找到 .js 脚本")) else Result.success(scripts)
        } catch (e: ArchiveException) {
            Result.failure(Exception(e.message))
        } catch (e: PasswordRequiredException) {
            Result.failure(Exception(ENCRYPTED_MESSAGE))
        } catch (e: Exception) {
            val kind = if (startsWith(bytes, SEVEN_Z_MAGIC)) "7z" else "压缩包"
            Result.failure(Exception("无法解析该 $kind 文件：${e.message.orEmpty()}"))
        }
    }

    private fun readZip(bytes: ByteArray): List<ArchiveScript> {
        val scripts = mutableListOf<ArchiveScript>()
        ZipFile.builder().setSeekableByteChannel(SeekableInMemoryByteChannel(bytes)).get().use { zip ->
            var count = 0
            var declaredTotal = 0L
            for (entry in zip.entries) {
                if (++count > MAX_ENTRIES) throw ArchiveException("压缩包内文件过多（超过 $MAX_ENTRIES 个）")
                if (entry.isDirectory) continue
                declaredTotal += entry.size.coerceAtLeast(0)
                checkTotal(declaredTotal)
                if (!isScriptEntry(entry.name)) continue
                if (entry.generalPurposeBit.usesEncryption()) throw ArchiveException(ENCRYPTED_MESSAGE)
                checkScriptSize(entry.size)
                if (!zip.canReadEntryData(entry)) throw ArchiveException("不支持该压缩包的压缩方式")
                scripts += toScript(entry, zip.getInputStream(entry))
            }
        }
        return scripts
    }

    private fun toScript(entry: ZipArchiveEntry, input: InputStream): ArchiveScript =
        input.use { ArchiveScript(displayPath(entry.name), decode(readCapped(it))) }

    private fun readSevenZ(bytes: ByteArray): List<ArchiveScript> {
        val scripts = mutableListOf<ArchiveScript>()
        SevenZFile.builder().setSeekableByteChannel(SeekableInMemoryByteChannel(bytes)).get().use { sevenZ ->
            var count = 0
            var declaredTotal = 0L
            while (true) {
                val entry = sevenZ.nextEntry ?: break
                if (++count > MAX_ENTRIES) throw ArchiveException("压缩包内文件过多（超过 $MAX_ENTRIES 个）")
                if (entry.isDirectory) continue
                declaredTotal += entry.size.coerceAtLeast(0)
                checkTotal(declaredTotal)
                if (!isScriptEntry(entry.name)) continue
                checkScriptSize(entry.size)
                scripts += sevenZ.getInputStream(entry).use { ArchiveScript(displayPath(entry.name), decode(readCapped(it))) }
            }
        }
        return scripts
    }

    // 只收 .js；跳过 macOS 附带的资源文件与含 .. 的路径
    private fun isScriptEntry(name: String): Boolean {
        val segments = name.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty() || segments.any { it == ".." || it == "__MACOSX" }) return false
        val base = segments.last()
        return !base.startsWith("._") && base.lowercase().endsWith(".js")
    }

    private fun displayPath(name: String): String = name.replace('\\', '/').trimStart('/')

    private fun checkScriptSize(size: Long) {
        if (size > MAX_SCRIPT_BYTES) throw ArchiveException("脚本文件超过 5 MB，已拒绝读取")
    }

    private fun checkTotal(total: Long) {
        if (total > MAX_TOTAL_UNCOMPRESSED_BYTES) throw ArchiveException("压缩包解压后体积过大，已拒绝读取")
    }

    // 声明的大小可能不可信，读取时再按上限截断
    private fun readCapped(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_SCRIPT_BYTES) throw ArchiveException("脚本文件超过 5 MB，已拒绝读取")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun decode(bytes: ByteArray): String {
        val text = bytes.toString(Charsets.UTF_8)
        return text.removePrefix("﻿")
    }

    private fun startsWith(bytes: ByteArray, prefix: ByteArray): Boolean =
        bytes.size >= prefix.size && prefix.indices.all { bytes[it] == prefix[it] }

    private const val ENCRYPTED_MESSAGE = "暂不支持加密压缩包"
}
