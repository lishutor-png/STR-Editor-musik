package com.example

import com.example.audio.AudioTagManager
import com.example.audio.SongMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
class AudioTagManagerTest {

    @Test
    fun testBuildTextFrameAscii() {
        val frame = AudioTagManager.buildTextFrame("TIT2", "Cantik")
        assertEquals('T'.code.toByte(), frame[0])
        assertEquals('I'.code.toByte(), frame[1])
        assertEquals('T'.code.toByte(), frame[2])
        assertEquals('2'.code.toByte(), frame[3])

        val payloadSize = frame[7].toInt() and 0xFF
        assertEquals(8, payloadSize) // 1 (encoding 0) + 6 ("Cantik") + 1 (null)
        assertEquals(0x00.toByte(), frame[10]) // encoding ISO-8859-1
    }

    @Test
    fun testBuildPictureFrameApicMatchesReference() {
        val fakeJpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00
        )
        val apicFrame = AudioTagManager.buildPictureFrame(fakeJpeg, "image/jpeg")

        // APIC tag header
        assertEquals('A'.code.toByte(), apicFrame[0])
        assertEquals('P'.code.toByte(), apicFrame[1])
        assertEquals('I'.code.toByte(), apicFrame[2])
        assertEquals('C'.code.toByte(), apicFrame[3])

        // Flags 00 00
        assertEquals(0x00.toByte(), apicFrame[8])
        assertEquals(0x00.toByte(), apicFrame[9])

        // Payload byte 0: encoding 0x00
        assertEquals(0x00.toByte(), apicFrame[10])

        // Next bytes: "image/jpeg\x00" (11 bytes: 11..21)
        val mimeStr = String(apicFrame, 11, 10, Charsets.US_ASCII)
        assertEquals("image/jpeg", mimeStr)
        assertEquals(0x00.toByte(), apicFrame[21])

        // Picture type: 0x03 (Cover front)
        assertEquals(0x03.toByte(), apicFrame[22])

        // Description: "Cover\x00" (6 bytes: 23..28)
        val descStr = String(apicFrame, 23, 5, Charsets.ISO_8859_1)
        assertEquals("Cover", descStr)
        assertEquals(0x00.toByte(), apicFrame[28])

        // Then starts raw image bytes FF D8
        assertEquals(0xFF.toByte(), apicFrame[29])
        assertEquals(0xD8.toByte(), apicFrame[30])
    }

    @Test
    fun testDetectMimeType() {
        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        assertEquals("image/jpeg", AudioTagManager.detectMimeType(jpegBytes))

        val pngBytes = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
        assertEquals("image/png", AudioTagManager.detectMimeType(pngBytes))
    }

    @Test
    fun testWriteAndReadMp3Tags() {
        runBlocking {
            val tempDir = File(System.getProperty("java.io.tmpdir"), "lish_test_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            val dummyMp3 = File(tempDir, "source.mp3")
            FileOutputStream(dummyMp3).use { fos ->
                val mpegFrame = ByteArray(1024)
                mpegFrame[0] = 0xFF.toByte()
                mpegFrame[1] = 0xFB.toByte()
                mpegFrame[2] = 0x90.toByte()
                mpegFrame[3] = 0x64.toByte()
                fos.write(mpegFrame)
            }

            val fakeJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x00)
            val meta = SongMetadata(
                title = "Cantik",
                artist = "Lish",
                album = "Single 2026",
                coverArtBytes = fakeJpeg,
                coverMimeType = "image/jpeg"
            )

            val outputMp3 = File(tempDir, "output.mp3")
            val success = AudioTagManager.writeMp3Tags(dummyMp3, outputMp3, meta)
            assertTrue(success)
            assertTrue(outputMp3.exists())

            // Read tags back
            val context = RuntimeEnvironment.getApplication()
            val readBack = AudioTagManager.readMetadata(context, file = outputMp3)
            assertEquals("Cantik", readBack.title)
            assertEquals("Lish", readBack.artist)
            assertEquals("Single 2026", readBack.album)
            assertNotNull(readBack.coverArtBytes)
            assertEquals(fakeJpeg.size, readBack.coverArtBytes!!.size)

            dummyMp3.delete()
            outputMp3.delete()
            tempDir.delete()
        }
    }
}
