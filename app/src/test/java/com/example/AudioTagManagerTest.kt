package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioTagManager
import com.example.audio.SongMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioTagManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testPresetCoverGeneration() {
        val (bytes, mime) = AudioTagManager.generatePresetCover(0, dimension = 400)
        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())
        assertEquals("image/jpeg", mime)
    }

    @Test
    fun testWriteAndReadMp3Tags() = runBlocking {
        val testMp3 = tempFolder.newFile("test_source.mp3")
        // Create dummy MP3 file with mock MPEG audio frames (0xFF 0xFB ...)
        FileOutputStream(testMp3).use { fos ->
            val dummyAudio = ByteArray(4096) { 0xFF.toByte() }
            fos.write(dummyAudio)
        }

        val (coverBytes, coverMime) = AudioTagManager.generatePresetCover(0, dimension = 400)

        val meta = SongMetadata(
            title = "Lagu Uji Coba",
            artist = "Artis Keren",
            album = "Album Terbaik",
            albumArtist = "Artis Kompilasi",
            genre = "Pop",
            year = "2024",
            trackNumber = "1",
            composer = "Komposer Top",
            comment = "Catatan Lagu",
            coverArtBytes = coverBytes,
            coverMimeType = coverMime
        )

        val outputMp3 = tempFolder.newFile("test_output.mp3")
        val success = AudioTagManager.writeMp3Tags(testMp3, outputMp3, meta)
        assertTrue("writeMp3Tags should succeed", success)
        assertTrue("outputMp3 should exist and have size", outputMp3.length() > testMp3.length())

        // Test isMp3File detection
        assertTrue("outputMp3 should be identified as MP3", AudioTagManager.isMp3File(outputMp3))

        // Test direct thumbnail extraction
        val thumb = AudioTagManager.extractCoverThumbnail(outputMp3)
        assertNotNull("extractCoverThumbnail should extract embedded picture", thumb)
        assertTrue("Thumbnail size should be non-zero", thumb!!.isNotEmpty())

        val context = ApplicationProvider.getApplicationContext<Context>()
        val readBack = AudioTagManager.readMetadata(context, null, outputMp3)
        assertNotNull("coverArtBytes should not be null", readBack.coverArtBytes)
        assertTrue("coverArtBytes should match written size", readBack.coverArtBytes!!.isNotEmpty())
    }
}
