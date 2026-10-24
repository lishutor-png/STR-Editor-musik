package com.example.audio

import android.net.Uri
import java.io.File
import java.util.Arrays

enum class AudioFormat(
    val extension: String,
    val displayName: String,
    val mimeType: String,
    val description: String
) {
    MP3("mp3", "MP3", "audio/mpeg", "Format paling populer & kompatibel"),
    M4A("m4a", "M4A / AAC", "audio/mp4", "Kualitas tinggi, ukuran ringkas"),
    WAV("wav", "WAV", "audio/x-wav", "Kualitas studio tanpa kompresi (Lossless)"),
    FLAC("flac", "FLAC", "audio/flac", "Lossless terkompresi dengan fidelitas penuh")
}

data class AudioTrackInfo(
    val id: String,
    val title: String,
    val artist: String = "Artis Tidak Diketahui",
    val durationMs: Long = 0L,
    val uri: Uri? = null,
    val file: File? = null,
    val format: AudioFormat = AudioFormat.MP3,
    val sizeBytes: Long = 0L,
    val sampleRate: Int = 44100,
    val channels: Int = 2,
    val bitrateKbps: Int = 192,
    val coverArtBytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioTrackInfo) return false
        if (id != other.id) return false
        if (title != other.title) return false
        if (artist != other.artist) return false
        if (durationMs != other.durationMs) return false
        if (uri != other.uri) return false
        if (file != other.file) return false
        if (format != other.format) return false
        if (sizeBytes != other.sizeBytes) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
        if (bitrateKbps != other.bitrateKbps) return false
        if (coverArtBytes != null) {
            if (other.coverArtBytes == null) return false
            if (!coverArtBytes.contentEquals(other.coverArtBytes)) return false
        } else if (other.coverArtBytes != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + (coverArtBytes?.contentHashCode() ?: 0)
        return result
    }
}

data class PcmData(
    val sampleRate: Int,
    val channelCount: Int,
    val samples: ShortArray
) {
    val durationMs: Long
        get() {
            if (sampleRate <= 0 || channelCount <= 0) return 0L
            return (samples.size.toLong() * 1000L) / (sampleRate.toLong() * channelCount.toLong())
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PcmData) return false
        if (sampleRate != other.sampleRate) return false
        if (channelCount != other.channelCount) return false
        return samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        var result = sampleRate
        result = 31 * result + channelCount
        result = 31 * result + samples.contentHashCode()
        return result
    }
}

data class EqualizerPreset(
    val name: String,
    val bassGain: Float,
    val midGain: Float,
    val trebleGain: Float
) {
    companion object {
        val PRESETS = listOf(
            EqualizerPreset("Normal", 0f, 0f, 0f),
            EqualizerPreset("Bass Boost", 5f, 1f, -1f),
            EqualizerPreset("Vocal Clear", -2f, 4f, 2f),
            EqualizerPreset("Pop / Bright", 2f, 1f, 4f),
            EqualizerPreset("Rock Energetic", 4f, -1f, 3f),
            EqualizerPreset("Akustik Lembut", 1f, 3f, 1f)
        )
    }
}

data class EqualizerSettings(
    val bassGain: Float = 0f,
    val midGain: Float = 0f,
    val trebleGain: Float = 0f,
    val presetName: String? = "Normal"
)

data class ExportOptions(
    val fileName: String,
    val format: AudioFormat = AudioFormat.MP3,
    val bitrateKbps: Int = 192,
    val sampleRate: Int = 44100,
    val channels: Int = 2
)

data class ExportedAudio(
    val file: File,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val format: AudioFormat,
    val dateModified: Long,
    val coverArtBytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExportedAudio) return false
        if (file != other.file) return false
        if (title != other.title) return false
        if (durationMs != other.durationMs) return false
        if (sizeBytes != other.sizeBytes) return false
        if (format != other.format) return false
        if (dateModified != other.dateModified) return false
        if (coverArtBytes != null) {
            if (other.coverArtBytes == null) return false
            if (!coverArtBytes.contentEquals(other.coverArtBytes)) return false
        } else if (other.coverArtBytes != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = file.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + (coverArtBytes?.contentHashCode() ?: 0)
        return result
    }
}

data class SongMetadata(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val year: String = "",
    val trackNumber: String = "",
    val composer: String = "",
    val comment: String = "",
    val coverArtBytes: ByteArray? = null,
    val coverMimeType: String = "image/jpeg",
    val durationMs: Long = 0L,
    val bitrateKbps: Int = 0,
    val sampleRate: Int = 0,
    val format: String = "MP3",
    val originalFileName: String = "",
    val originalFilePath: String? = null,
    val originalUri: Uri? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SongMetadata) return false
        if (title != other.title) return false
        if (artist != other.artist) return false
        if (album != other.album) return false
        if (albumArtist != other.albumArtist) return false
        if (genre != other.genre) return false
        if (year != other.year) return false
        if (trackNumber != other.trackNumber) return false
        if (composer != other.composer) return false
        if (comment != other.comment) return false
        if (coverMimeType != other.coverMimeType) return false
        if (coverArtBytes != null) {
            if (other.coverArtBytes == null) return false
            if (!coverArtBytes.contentEquals(other.coverArtBytes)) return false
        } else if (other.coverArtBytes != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + (coverArtBytes?.contentHashCode() ?: 0)
        return result
    }
}
