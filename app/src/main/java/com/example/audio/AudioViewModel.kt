package com.example.audio

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AudioViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "AudioViewModel"

    val player = RealtimeAudioPlayer(viewModelScope)

    // Tag Editor & Song Information State
    private val _tagMetadata = MutableStateFlow<SongMetadata?>(null)
    val tagMetadata: StateFlow<SongMetadata?> = _tagMetadata.asStateFlow()

    private val _selectedTagFile = MutableStateFlow<File?>(null)
    val selectedTagFile: StateFlow<File?> = _selectedTagFile.asStateFlow()

    private val _selectedTagUri = MutableStateFlow<Uri?>(null)
    val selectedTagUri: StateFlow<Uri?> = _selectedTagUri.asStateFlow()

    // Current active track for Editing / Trimming
    private val _activeTrack = MutableStateFlow<AudioTrackInfo?>(null)
    val activeTrack: StateFlow<AudioTrackInfo?> = _activeTrack.asStateFlow()

    private val _activePcm = MutableStateFlow<PcmData?>(null)
    val activePcm: StateFlow<PcmData?> = _activePcm.asStateFlow()

    private val _waveformBars = MutableStateFlow(FloatArray(120) { 0.1f })
    val waveformBars: StateFlow<FloatArray> = _waveformBars.asStateFlow()

    // Trim & Effects State
    private val _startMs = MutableStateFlow(0L)
    val startMs: StateFlow<Long> = _startMs.asStateFlow()

    private val _endMs = MutableStateFlow(0L)
    val endMs: StateFlow<Long> = _endMs.asStateFlow()

    private val _volumeGain = MutableStateFlow(1.0f) // 100%
    val volumeGain: StateFlow<Float> = _volumeGain.asStateFlow()

    private val _fadeInMs = MutableStateFlow(0L)
    val fadeInMs: StateFlow<Long> = _fadeInMs.asStateFlow()

    private val _fadeOutMs = MutableStateFlow(0L)
    val fadeOutMs: StateFlow<Long> = _fadeOutMs.asStateFlow()

    // Equalizer State
    private val _equalizerSettings = MutableStateFlow(EqualizerSettings())
    val equalizerSettings: StateFlow<EqualizerSettings> = _equalizerSettings.asStateFlow()

    // Theme Mode (null = Follow System, true = Dark, false = Light)
    private val _isDarkTheme = MutableStateFlow<Boolean?>(null)
    val isDarkTheme: StateFlow<Boolean?> = _isDarkTheme.asStateFlow()

    // Selected file for "Save As" operation
    private val _pendingSaveAsFile = MutableStateFlow<File?>(null)
    val pendingSaveAsFile: StateFlow<File?> = _pendingSaveAsFile.asStateFlow()

    // Merge Queue
    private val _mergeTracks = MutableStateFlow<List<AudioTrackInfo>>(emptyList())
    val mergeTracks: StateFlow<List<AudioTrackInfo>> = _mergeTracks.asStateFlow()

    private val _mergeGapMs = MutableStateFlow(0L)
    val mergeGapMs: StateFlow<Long> = _mergeGapMs.asStateFlow()

    // Format Converter State
    private val _converterTrack = MutableStateFlow<AudioTrackInfo?>(null)
    val converterTrack: StateFlow<AudioTrackInfo?> = _converterTrack.asStateFlow()

    private val _converterFormat = MutableStateFlow(AudioFormat.MP3)
    val converterFormat: StateFlow<AudioFormat> = _converterFormat.asStateFlow()

    private val _converterBitrate = MutableStateFlow(192)
    val converterBitrate: StateFlow<Int> = _converterBitrate.asStateFlow()

    // Export & Processing State
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _processingProgress = MutableStateFlow(0.0f)
    val processingProgress: StateFlow<Float> = _processingProgress.asStateFlow()

    private val _processingStatus = MutableStateFlow("")
    val processingStatus: StateFlow<String> = _processingStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Exported files library
    private val _exportedFiles = MutableStateFlow<List<ExportedAudio>>(emptyList())
    val exportedFiles: StateFlow<List<ExportedAudio>> = _exportedFiles.asStateFlow()

    // Built-in sample tracks
    private val _sampleTracks = MutableStateFlow<List<AudioTrackInfo>>(emptyList())
    val sampleTracks: StateFlow<List<AudioTrackInfo>> = _sampleTracks.asStateFlow()

    // Mini player for library
    private var libraryMediaPlayer: MediaPlayer? = null
    private val _libraryPlayingPath = MutableStateFlow<String?>(null)
    val libraryPlayingPath: StateFlow<String?> = _libraryPlayingPath.asStateFlow()

    init {
        // Clear any previous sample tracks so app starts completely clean and empty as requested
        SampleAudioGenerator.deleteSampleTracks(getApplication())
        _sampleTracks.value = emptyList()
        refreshExportedLibrary()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun refreshExportedLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            val exportDir = getExportDirectory()
            val files = exportDir.listFiles { file ->
                file.isFile && (file.name.endsWith(".mp3", true) ||
                        file.name.endsWith(".m4a", true) ||
                        file.name.endsWith(".wav", true) ||
                        file.name.endsWith(".flac", true))
            }?.sortedByDescending { it.lastModified() } ?: emptyList()

            val list = files.map { file ->
                val format = when {
                    file.name.endsWith(".mp3", true) -> AudioFormat.MP3
                    file.name.endsWith(".m4a", true) -> AudioFormat.M4A
                    file.name.endsWith(".wav", true) -> AudioFormat.WAV
                    file.name.endsWith(".flac", true) -> AudioFormat.FLAC
                    else -> AudioFormat.MP3
                }
                val duration = try {
                    val pcm = if (file.name.endsWith(".wav", true)) AudioDecoder.parseWavFile(file) else null
                    pcm?.durationMs ?: (file.length() / (192 * 128 / 8)) // estimate if needed
                } catch (e: Exception) {
                    0L
                }

                ExportedAudio(
                    file = file,
                    title = file.nameWithoutExtension,
                    durationMs = duration,
                    sizeBytes = file.length(),
                    format = format,
                    dateModified = file.lastModified()
                )
            }
            _exportedFiles.value = list
        }
    }

    fun loadTrackFromUri(uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatus.value = "Membuka file audio..."
            try {
                val info = AudioDecoder.extractMetadata(getApplication(), uri, null, "Audio Pilihan")
                loadTrack(info)
                _statusMessage.value = "Berhasil memuat: ${info.title}"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load audio from URI: ${e.message}")
                _statusMessage.value = "Gagal memuat audio: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun loadTrack(track: AudioTrackInfo) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatus.value = "Menganalisis audio..."
            player.stop()

            try {
                val pcm = withContext(Dispatchers.IO) {
                    if (track.file != null) {
                        AudioDecoder.decode(getApplication(), track.file)
                    } else if (track.uri != null) {
                        AudioDecoder.decode(getApplication(), track.uri)
                    } else {
                        throw IllegalStateException("No file or URI available for track")
                    }
                }

                _activeTrack.value = track.copy(durationMs = pcm.durationMs)
                _activePcm.value = pcm
                _startMs.value = 0L
                _endMs.value = pcm.durationMs

                // Waveform
                val bars = AudioProcessor.extractWaveform(pcm, 120)
                _waveformBars.value = bars

                // Setup player
                player.setSource(pcm, 0L, pcm.durationMs)
                player.updateEffects(_volumeGain.value, _fadeInMs.value, _fadeOutMs.value)
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding track: ${e.message}")
                _statusMessage.value = "Gagal memproses audio: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun updateTrimRange(start: Long, end: Long) {
        val duration = _activePcm.value?.durationMs ?: 0L
        val newStart = start.coerceIn(0L, duration)
        val newEnd = end.coerceIn(newStart, duration)
        _startMs.value = newStart
        _endMs.value = newEnd
        player.updateSelection(newStart, newEnd)
    }

    fun resetTrimRange() {
        val duration = _activePcm.value?.durationMs ?: 0L
        updateTrimRange(0L, duration)
        _statusMessage.value = "Seleksi di-reset ke durasi penuh"
    }

    fun adjustStartMs(deltaMs: Long) {
        val newStart = (_startMs.value + deltaMs).coerceIn(0L, _endMs.value)
        updateTrimRange(newStart, _endMs.value)
    }

    fun adjustEndMs(deltaMs: Long) {
        val duration = _activePcm.value?.durationMs ?: 0L
        val newEnd = (_endMs.value + deltaMs).coerceIn(_startMs.value, duration)
        updateTrimRange(_startMs.value, newEnd)
    }

    fun setVolumeGain(gain: Float) {
        val safeGain = gain.coerceIn(0.0f, 3.0f)
        _volumeGain.value = safeGain
        player.updateEffects(safeGain, _fadeInMs.value, _fadeOutMs.value, _equalizerSettings.value)
    }

    fun setFadeInMs(ms: Long) {
        val safeMs = ms.coerceAtLeast(0L)
        _fadeInMs.value = safeMs
        player.updateEffects(_volumeGain.value, safeMs, _fadeOutMs.value, _equalizerSettings.value)
    }

    fun setFadeOutMs(ms: Long) {
        val safeMs = ms.coerceAtLeast(0L)
        _fadeOutMs.value = safeMs
        player.updateEffects(_volumeGain.value, _fadeInMs.value, safeMs, _equalizerSettings.value)
    }

    // Equalizer Controls
    fun setEqualizerPreset(preset: EqualizerPreset) {
        val updated = _equalizerSettings.value.copy(
            bassDb = preset.bassDb,
            midDb = preset.midDb,
            trebleDb = preset.trebleDb,
            activePreset = preset
        )
        _equalizerSettings.value = updated
        player.updateEffects(_volumeGain.value, _fadeInMs.value, _fadeOutMs.value, updated)
    }

    fun setBassGain(db: Float) {
        val updated = _equalizerSettings.value.copy(
            bassDb = db.coerceIn(-12f, 12f),
            activePreset = EqualizerPreset.CUSTOM
        )
        _equalizerSettings.value = updated
        player.updateEffects(_volumeGain.value, _fadeInMs.value, _fadeOutMs.value, updated)
    }

    fun setMidGain(db: Float) {
        val updated = _equalizerSettings.value.copy(
            midDb = db.coerceIn(-12f, 12f),
            activePreset = EqualizerPreset.CUSTOM
        )
        _equalizerSettings.value = updated
        player.updateEffects(_volumeGain.value, _fadeInMs.value, _fadeOutMs.value, updated)
    }

    fun setTrebleGain(db: Float) {
        val updated = _equalizerSettings.value.copy(
            trebleDb = db.coerceIn(-12f, 12f),
            activePreset = EqualizerPreset.CUSTOM
        )
        _equalizerSettings.value = updated
        player.updateEffects(_volumeGain.value, _fadeInMs.value, _fadeOutMs.value, updated)
    }

    fun resetEqualizer() {
        setEqualizerPreset(EqualizerPreset.FLAT)
    }

    // Theme Mode Toggle
    fun setThemeMode(isDark: Boolean?) {
        _isDarkTheme.value = isDark
    }

    fun toggleTheme(currentDark: Boolean = _isDarkTheme.value ?: true) {
        _isDarkTheme.value = !currentDark
    }

    // Save As / Phone Storage Handlers
    fun prepareSaveAs(file: File) {
        _pendingSaveAsFile.value = file
    }

    fun clearPendingSaveAs() {
        _pendingSaveAsFile.value = null
    }

    fun executeSaveAs(targetUri: Uri) {
        val file = _pendingSaveAsFile.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatus.value = "Menyimpan ke lokasi pilihan (Save As)..."
            val success = StorageExporter.saveAsToUri(getApplication(), file, targetUri)
            _isProcessing.value = false
            _pendingSaveAsFile.value = null
            _statusMessage.value = if (success) {
                "Berhasil 'Save As' ke memori HP"
            } else {
                "Gagal menyimpan ke lokasi pilihan"
            }
        }
    }

    fun saveToDevicePublicMusic(file: File, mimeType: String = "audio/mpeg") {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatus.value = "Menyimpan ke Memori HP (Folder Musik)..."
            val savedUri = StorageExporter.saveToPhoneMemory(
                getApplication(),
                file,
                file.name,
                mimeType
            )
            _isProcessing.value = false
            _statusMessage.value = if (savedUri != null) {
                "Berhasil tersimpan di Memori HP (Folder Music/LishStudioRecord)"
            } else {
                "Gagal menyimpan ke memori HP"
            }
        }
    }

    // Export current trimmed/edited track
    fun exportCurrentTrack(options: ExportOptions) {
        val pcm = _activePcm.value ?: run {
            _statusMessage.value = "Tidak ada audio yang dipilih untuk diekspor"
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _processingProgress.value = 0.0f
            _processingStatus.value = "Menerapkan pemotongan, equalizer & efek..."
            player.pause()

            try {
                val processedPcm = withContext(Dispatchers.Default) {
                    // 1. Trim
                    var result = AudioProcessor.trim(pcm, _startMs.value, _endMs.value)
                    // 2. Equalizer
                    result = AudioProcessor.applyEqualizer(result, options.equalizerSettings)
                    // 3. Volume Gain
                    if (options.volumeGain != 1.0f) {
                        result = AudioProcessor.applyGain(result, options.volumeGain)
                    }
                    // 4. Fades
                    if (options.fadeInMs > 0L || options.fadeOutMs > 0L) {
                        result = AudioProcessor.applyFades(result, options.fadeInMs, options.fadeOutMs)
                    }
                    result
                }

                _processingStatus.value = "Mengekspor ke format ${options.format.displayName}..."

                val exportDir = getExportDirectory()
                val safeName = options.fileName.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val fileNameWithExt = "$safeName.${options.format.extension}"
                val outputFile = File(exportDir, fileNameWithExt)

                AudioEncoder.encode(processedPcm, outputFile, options) { progress ->
                    _processingProgress.value = progress
                }

                // Automatically save to phone's public Music folder
                val publicUri = StorageExporter.saveToPhoneMemory(
                    getApplication(),
                    outputFile,
                    outputFile.name,
                    options.format.mimeType
                )

                refreshExportedLibrary()
                val phoneStorageNote = if (publicUri != null) " & tersimpan di Memori HP (Folder Musik)" else ""
                _statusMessage.value = "Audio berhasil disimpan: ${outputFile.name}$phoneStorageNote"
            } catch (e: Exception) {
                Log.e(TAG, "Export failed: ${e.message}")
                _statusMessage.value = "Ekspor gagal: ${e.message}"
            } finally {
                _isProcessing.value = false
                _processingProgress.value = 0.0f
            }
        }
    }

    // Merge Queue Management
    fun addTrackToMerge(track: AudioTrackInfo) {
        val current = _mergeTracks.value.toMutableList()
        current.add(track)
        _mergeTracks.value = current
        _statusMessage.value = "${track.title} ditambahkan ke antrean gabung"
    }

    fun clearMergeTracks() {
        _mergeTracks.value = emptyList()
        _statusMessage.value = "Antrean gabung dikosongkan"
    }

    fun removeTrackFromMerge(index: Int) {
        val current = _mergeTracks.value.toMutableList()
        if (index in current.indices) {
            val removed = current.removeAt(index)
            _mergeTracks.value = current
            _statusMessage.value = "${removed.title} dihapus dari antrean"
        }
    }

    fun moveTrackMergeUp(index: Int) {
        if (index <= 0) return
        val current = _mergeTracks.value.toMutableList()
        val item = current.removeAt(index)
        current.add(index - 1, item)
        _mergeTracks.value = current
    }

    fun moveTrackMergeDown(index: Int) {
        val current = _mergeTracks.value.toMutableList()
        if (index >= current.size - 1) return
        val item = current.removeAt(index)
        current.add(index + 1, item)
        _mergeTracks.value = current
    }

    fun setMergeGapMs(ms: Long) {
        _mergeGapMs.value = ms.coerceAtLeast(0L)
    }

    fun executeMerge(options: ExportOptions) {
        val tracks = _mergeTracks.value
        if (tracks.size < 2) {
            _statusMessage.value = "Tambahkan minimal 2 lagu untuk digabungkan"
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _processingProgress.value = 0.0f
            _processingStatus.value = "Mendekode trek antrean..."
            player.pause()

            try {
                val pcmList = mutableListOf<PcmData>()
                tracks.forEachIndexed { idx, track ->
                    _processingStatus.value = "Mendekode (${idx + 1}/${tracks.size}): ${track.title}"
                    val pcm = withContext(Dispatchers.IO) {
                        if (track.file != null) {
                            AudioDecoder.decode(getApplication(), track.file)
                        } else if (track.uri != null) {
                            AudioDecoder.decode(getApplication(), track.uri)
                        } else {
                            throw IllegalStateException("No source for ${track.title}")
                        }
                    }
                    pcmList.add(pcm)
                    _processingProgress.value = ((idx + 1).toFloat() / tracks.size) * 0.4f
                }

                _processingStatus.value = "Menggabungkan trek audio..."
                val mergedPcm = withContext(Dispatchers.Default) {
                    AudioProcessor.mergeTracks(pcmList, _mergeGapMs.value)
                }

                _processingStatus.value = "Mengekspor hasil penggabungan..."
                val exportDir = getExportDirectory()
                val safeName = options.fileName.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val outputFile = File(exportDir, "$safeName.${options.format.extension}")

                AudioEncoder.encode(mergedPcm, outputFile, options) { progress ->
                    _processingProgress.value = 0.4f + progress * 0.6f
                }

                StorageExporter.saveToPhoneMemory(
                    getApplication(),
                    outputFile,
                    outputFile.name,
                    options.format.mimeType
                )

                refreshExportedLibrary()
                _statusMessage.value = "Berhasil menggabungkan ${tracks.size} lagu & tersimpan di Memori HP"
            } catch (e: Exception) {
                Log.e(TAG, "Merge failed: ${e.message}")
                _statusMessage.value = "Gagal menggabungkan: ${e.message}"
            } finally {
                _isProcessing.value = false
                _processingProgress.value = 0.0f
            }
        }
    }

    // Format Converter Management
    fun selectTrackForConverter(track: AudioTrackInfo) {
        _converterTrack.value = track
    }

    fun setConverterFormat(format: AudioFormat) {
        _converterFormat.value = format
    }

    fun setConverterBitrate(bitrate: Int) {
        _converterBitrate.value = bitrate
    }

    fun executeConvert(fileName: String) {
        val track = _converterTrack.value ?: run {
            _statusMessage.value = "Pilih lagu yang akan dikonversi terlebih dahulu"
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _processingProgress.value = 0.0f
            _processingStatus.value = "Membaca file sumber..."

            try {
                val pcm = withContext(Dispatchers.IO) {
                    if (track.file != null) {
                        AudioDecoder.decode(getApplication(), track.file)
                    } else if (track.uri != null) {
                        AudioDecoder.decode(getApplication(), track.uri)
                    } else {
                        throw IllegalStateException("No file or URI")
                    }
                }

                _processingStatus.value = "Mengonversi ke ${_converterFormat.value.displayName}..."
                val exportDir = getExportDirectory()
                val safeName = fileName.trim().ifEmpty { track.title + "_converted" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val outputFile = File(exportDir, "$safeName.${_converterFormat.value.extension}")

                val options = ExportOptions(
                    format = _converterFormat.value,
                    bitrateKbps = _converterBitrate.value,
                    fileName = safeName
                )

                AudioEncoder.encode(pcm, outputFile, options) { progress ->
                    _processingProgress.value = progress
                }

                StorageExporter.saveToPhoneMemory(
                    getApplication(),
                    outputFile,
                    outputFile.name,
                    _converterFormat.value.mimeType
                )

                refreshExportedLibrary()
                _statusMessage.value = "Konversi selesai: ${outputFile.name} & tersimpan di Memori HP"
            } catch (e: Exception) {
                Log.e(TAG, "Conversion failed: ${e.message}")
                _statusMessage.value = "Konversi gagal: ${e.message}"
            } finally {
                _isProcessing.value = false
                _processingProgress.value = 0.0f
            }
        }
    }

    // Library file playback & sharing
    fun toggleLibraryPlay(file: File) {
        if (_libraryPlayingPath.value == file.absolutePath) {
            libraryMediaPlayer?.stop()
            libraryMediaPlayer?.release()
            libraryMediaPlayer = null
            _libraryPlayingPath.value = null
        } else {
            libraryMediaPlayer?.stop()
            libraryMediaPlayer?.release()
            libraryMediaPlayer = null

            try {
                libraryMediaPlayer = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    start()
                    setOnCompletionListener {
                        _libraryPlayingPath.value = null
                    }
                }
                _libraryPlayingPath.value = file.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Failed playing library file: ${e.message}")
                _statusMessage.value = "Gagal memutar file: ${e.message}"
            }
        }
    }

    fun shareExportedFile(context: Context, file: File) {
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Bagikan Audio"))
        } catch (e: Exception) {
            Log.e(TAG, "Share failed: ${e.message}")
            _statusMessage.value = "Gagal membagikan: ${e.message}"
        }
    }

    fun deleteExportedFile(file: File) {
        if (_libraryPlayingPath.value == file.absolutePath) {
            libraryMediaPlayer?.stop()
            libraryMediaPlayer?.release()
            libraryMediaPlayer = null
            _libraryPlayingPath.value = null
        }
        if (file.exists()) {
            file.delete()
        }
        refreshExportedLibrary()
        _statusMessage.value = "File dihapus"
    }

    private fun getExportDirectory(): File {
        val app = getApplication<Application>()
        val dir = app.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: File(app.filesDir, "exported_audio")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // --- Tag Editor Operations ---
    fun loadTagFromUri(uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatus.value = "Membaca informasi lagu..."
            _processingProgress.value = 0.4f
            try {
                val app = getApplication<Application>()
                val tempFile = File(app.cacheDir, "tag_edit_temp_${System.currentTimeMillis()}.mp3")
                app.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val meta = AudioTagManager.readMetadata(app, uri, tempFile)
                _selectedTagFile.value = tempFile
                _selectedTagUri.value = uri
                _tagMetadata.value = meta
                _statusMessage.value = "File berhasil dimuat: ${meta.title.ifBlank { "Audio" }}"
            } catch (e: Exception) {
                Log.e(TAG, "Failed loading tag metadata: ${e.message}")
                _statusMessage.value = "Gagal membaca info lagu: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun loadTagFromFile(file: File) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatus.value = "Membaca informasi lagu..."
            _processingProgress.value = 0.4f
            try {
                val app = getApplication<Application>()
                val meta = AudioTagManager.readMetadata(app, null, file)
                _selectedTagFile.value = file
                _selectedTagUri.value = null
                _tagMetadata.value = meta
                _statusMessage.value = "Memuat tag: ${meta.title.ifBlank { file.nameWithoutExtension }}"
            } catch (e: Exception) {
                Log.e(TAG, "Failed loading tag metadata from file: ${e.message}")
                _statusMessage.value = "Gagal membaca info file: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun updateSongMetadata(updated: SongMetadata) {
        _tagMetadata.value = updated
    }

    fun updateCoverArt(bytes: ByteArray, mimeType: String = "image/jpeg") {
        _tagMetadata.value = _tagMetadata.value?.copy(
            coverArtBytes = bytes,
            coverMimeType = mimeType
        )
        _statusMessage.value = "Gambar cover diperbarui"
    }

    fun removeCoverArt() {
        _tagMetadata.value = _tagMetadata.value?.copy(
            coverArtBytes = null
        )
        _statusMessage.value = "Gambar cover dihapus"
    }

    fun saveSongMetadata(customFileName: String? = null, onComplete: ((File) -> Unit)? = null) {
        val currentMeta = _tagMetadata.value ?: return
        val currentFile = _selectedTagFile.value ?: return
        val app = getApplication<Application>()

        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatus.value = "Menyimpan informasi & cover lagu..."
            _processingProgress.value = 0.25f

            try {
                val safeName = if (!customFileName.isNullOrBlank()) {
                    customFileName.trim().replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "_")
                } else if (currentMeta.title.isNotBlank()) {
                    val combined = if (currentMeta.artist.isNotBlank()) {
                        "${currentMeta.artist} - ${currentMeta.title}"
                    } else {
                        currentMeta.title
                    }
                    combined.replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "_")
                } else {
                    "lagu_edited_${System.currentTimeMillis()}"
                }

                val exportDir = getExportDirectory()
                val targetFile = File(exportDir, "$safeName.mp3")

                _processingProgress.value = 0.6f
                val isMp3 = currentFile.name.endsWith(".mp3", true) || currentMeta.format.equals("MP3", true)
                val success = if (isMp3) {
                    AudioTagManager.writeMp3Tags(currentFile, targetFile, currentMeta)
                } else {
                    val pcm = AudioDecoder.decode(app, currentFile)
                    val rawMp3 = File(app.cacheDir, "temp_encode_${System.currentTimeMillis()}.mp3")
                    AudioEncoder.encode(pcm, rawMp3, ExportOptions(format = AudioFormat.MP3, bitrateKbps = 192))
                    val res = AudioTagManager.writeMp3Tags(rawMp3, targetFile, currentMeta)
                    rawMp3.delete()
                    res
                }

                if (success && targetFile.exists() && targetFile.length() > 0) {
                    refreshExportedLibrary()
                    _statusMessage.value = "Informasi & cover lagu berhasil disimpan ke Koleksi!"
                    _tagMetadata.value = currentMeta.copy(
                        originalFilePath = targetFile.absolutePath,
                        originalFileName = targetFile.name
                    )
                    _selectedTagFile.value = targetFile
                    onComplete?.invoke(targetFile)
                } else {
                    _statusMessage.value = "Gagal menyimpan info lagu."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving song metadata: ${e.message}", e)
                _statusMessage.value = "Gagal menyimpan: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
        libraryMediaPlayer?.release()
        libraryMediaPlayer = null
    }
}
