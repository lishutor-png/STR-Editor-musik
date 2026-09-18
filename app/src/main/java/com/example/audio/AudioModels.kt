package com.example.audio

import android.net.Uri
import java.io.File

/**
 * Raw 16-bit PCM audio data.
 */
data class PcmData(
    val sampleRate: Int = 44100,
    val channelCount: Int = 2,
    val samples: ShortArray = ShortArray(0)
) {
    val durationMs: Long
        get() {
            if (sampleRate <= 0 || channelCount <= 0) return 0L
            val totalFrames = samples.size / channelCount
            return (totalFrames * 1000L) / sampleRate
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PcmData
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

/**
 * Metadata for a selected audio track.
 */
data class AudioTrackInfo(
    val id: String,
    val title: String,
    val durationMs: Long,
    val format: String,
    val sizeBytes: Long,
    val sampleRate: Int = 44100,
    val channels: Int = 2,
    val uri: Uri? = null,
    val file: File? = null,
    val isSample: Boolean = false
)

/**
 * Supported export audio formats.
 */
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

/**
 * Equalizer quick presets and settings.
 */
enum class EqualizerPreset(
    val displayName: String,
    val bassDb: Float,
    val midDb: Float,
    val trebleDb: Float,
    val description: String
) {
    FLAT("Normal", 0f, 0f, 0f, "Suara seimbang alami"),
    BASS_BOOST("Bass Boost", 6f, 0f, -1f, "Dentuman frekuensi rendah lebih tebal"),
    VOCAL_BOOST("Vokal Jernih", -2f, 6f, 2f, "Menonjolkan kejernihan vokal penyanyi"),
    TREBLE_BOOST("Treble Boost", -2f, 1f, 7f, "Detail frekuensi tinggi renyah & jernih"),
    ROCK("Rock / EDM", 5f, -2f, 5f, "Sensasi dinamis untuk beat kencang"),
    ACOUSTIC("Akustik", 3f, 3f, 2f, "Hangat untuk instrumen petik & akustik"),
    CUSTOM("Kustom", 0f, 0f, 0f, "Pengaturan bebas sesuai selera")
}

data class EqualizerSettings(
    val bassDb: Float = 0f,     // -12 dB to +12 dB
    val midDb: Float = 0f,      // -12 dB to +12 dB
    val trebleDb: Float = 0f,   // -12 dB to +12 dB
    val activePreset: EqualizerPreset = EqualizerPreset.FLAT
)

/**
 * Configuration options for audio export.
 */
data class ExportOptions(
    val format: AudioFormat = AudioFormat.MP3,
    val bitrateKbps: Int = 192,
    val sampleRate: Int = 44100,
    val volumeGain: Float = 1.0f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val equalizerSettings: EqualizerSettings = EqualizerSettings(),
    val fileName: String = "audio_edit"
)

/**
 * Details of an exported audio file in the library.
 */
data class ExportedAudio(
    val file: File,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val format: AudioFormat,
    val dateModified: Long
)
