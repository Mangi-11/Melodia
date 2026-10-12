package com.lin0721.linmusic.desktop.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

class CoverImageTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-cover-test").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun writePng(name: String, width: Int, height: Int, type: Int = BufferedImage.TYPE_INT_ARGB): File {
        val image = BufferedImage(width, height, type)
        val file = File(dir, name)
        ImageIO.write(image, "png", file)
        return file
    }

    private fun decode(bytes: ByteArray): BufferedImage = ImageIO.read(ByteArrayInputStream(bytes))

    @Test
    fun landscapeImageIsCenterCroppedToSquare() {
        val bytes = CoverImage.prepareJpeg(writePng("wide.png", 300, 200)).getOrThrow()
        val result = decode(bytes)
        assertEquals(200, result.width)
        assertEquals(200, result.height)
    }

    @Test
    fun largeImageIsScaledDownToMaxEdge() {
        val bytes = CoverImage.prepareJpeg(writePng("big.png", 2000, 2400, BufferedImage.TYPE_INT_RGB)).getOrThrow()
        val result = decode(bytes)
        assertEquals(CoverImage.MAX_EDGE, result.width)
        assertEquals(CoverImage.MAX_EDGE, result.height)
    }

    @Test
    fun outputIsJpegAndTransparentPixelsBecomeWhite() {
        val bytes = CoverImage.prepareJpeg(writePng("alpha.png", 64, 64)).getOrThrow()
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
        val pixel = decode(bytes).getRGB(32, 32) and 0xFFFFFF
        assertTrue("应接近白色: ${pixel.toString(16)}", pixel >= 0xF8F8F8)
    }

    @Test
    fun nonImageFileFails() {
        val file = File(dir, "note.png").apply { writeText("not an image") }
        assertTrue(CoverImage.prepareJpeg(file).isFailure)
    }

    @Test
    fun missingOrEmptyFileFails() {
        assertTrue(CoverImage.prepareJpeg(File(dir, "missing.png")).isFailure)
        assertTrue(CoverImage.prepareJpeg(File(dir, "empty.png").apply { createNewFile() }).isFailure)
    }
}
