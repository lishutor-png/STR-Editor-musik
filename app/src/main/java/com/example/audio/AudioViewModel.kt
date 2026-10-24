package com.example.audio

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
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
import java.io.FileInputStream
import java.io.FileOutputStream

class AudioViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "AudioViewModel"

    val player = RealtimeAudioPlayer(viewModelScope)

    // Active track & editing state
    private val _activeTrack = MutableStateFlow<AudioTrackInfo?>(null)
    val activeTrack: StateFlow<AudioTrackInfo?> = _activeTrack.asStateFlow()

    private val _activePcm = MutableStateFlow<PcmData?>(null)
    val activePcm: StateFlow<PcmData?> = _activePcm.asStateFlow()

    private val _waveformBars = MutableStateFlow<FloatArray>(FloatArray(100) { 0.1f })
    val waveformBars: StateFlow<FloatArray> = _waveformBars.asStateFlow()

    private val _startMs = MutableStateFlow(0L)
    val startMs: StateFlow<Long> = _startMs.asStateFlow()

    private val _endMs = MutableStateFlow(0L)
    val endMs: StateFlow<Long> = _endMs.asStateFlow()

    private val _volumeGain = MutableStateFlow(1.0f)
    val volumeGain: StateFlow<Float> = _volumeGain.asStateFlow()

    private val _fadeInMs = MutableStateFlow(0L)
    val fadeInMs: StateFlow<Long> = _fadeInMs.asStateFlow()

    private val _fadeOutMs = MutableStateFlow(0L)
    val fadeOutMs: StateFlow<Long> = _fadeOutMs.asStateFlow()

    private val _equalizerSettings = MutableStateFlow(EqualizerSettings())
    val equalizerSettings: StateFlow<EqualizerSettings> = _equalizerSettings.asStateFlow()

    // Theme mode (true = dark, false = light)
    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    // Save as state
    private val _pendingSaveAsFile = MutableStateFlow<File?>(null)
    val pendingSaveAsFile: StateFlow<File?> = _pendingSaveAsFile.asStateFlow()

    // Merge tracks state
    private val _mergeTracks = MutableStateFlow<List<AudioTrackInfo>>(emptyList())
    val mergeTracks: StateFlow<List<AudioTrackInfo>> = _mergeTracks.asStateFlow()

    private val _mergeGapMs = MutableStateFlow(500L)
    val mergeGapMs: StateFlow<Long> = _mergeGapMs.asStateFlow()

    // Converter state
    private val _converterTrack = MutableStateFlow<AudioTrackInfo?>(null)
    val converterTrack: StateFlow<AudioTrackInfo?> = _converterTrack.asStateFlow()

    private val _converterFormat = MutableStateFlow(AudioFormat.MP3)
    val converterFormat: StateFlow<AudioFormat> = _converterFormat.asStateFlow()

    private val _converterBitrate = MutableStateFlow(192)
    val converterBitrate: StateFlow<Int> = _converterBitrate.asStateFlow()

    // Processing & status
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _processingProgress = MutableStateFlow(0f)
    val processingProgress: StateFlow<Float> = _processingProgress.asStateFlow()

    private val _processingStatus = MutableStateFlow("")
    val processingStatus: StateFlow<String> = _processingStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Library & sample tracks
    private val _exportedFiles = MutableStateFlow<List<ExportedAudio>>(emptyList())
    val exportedFiles: StateFlow<List<ExportedAudio>> = _exportedFiles.asStateFlow()

    private val _sampleTracks = MutableStateFlow<List<AudioTrackInfo>>(emptyList())
    val sampleTracks: StateFlow<List<AudioTrackInfo>> = _sampleTracks.asStateFlow()

    private var libraryMediaPlayer: MediaPlayer? = null
    private val _libraryPlayingPath = MutableStateFlow<String?>(null)
    val libraryPlayingPath: StateFlow<String?> = _libraryPlayingPath.asStateFlow()

    // Tag Editor state
    private val _tagMetadata = MutableStateFlow<SongMetadata?>(null)
    val tagMetadata: StateFlow<SongMetadata?> = _tagMetadata.asStateFlow()

    private val _selectedTagFile = MutableStateFlow<File?>(null)
    val selectedTagFile: StateFlow<File?> = _selectedTagFile.asStateFlow()

    private val _selectedTagUri = MutableStateFlow<Uri?>(null)
    val selectedTagUri: StateFlow<Uri?> = _selectedTagUri.asStateFlow()

    init {
        viewModelScope.launch {
            loadSampleTracks()
            refreshExportedLibrary()
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private suspend fun loadSampleTracks() {
        try {
            val samples = SampleAudioGenerator.getOrCreateSampleTracks(getApplication())
            _sampleTracks.value = samples
            if (_activeTrack.value == null && samples.isNotEmpty()) {
                loadTrack(samples.first())
            }
            if (_tagMetadata.value == null && samples.isNotEmpty()) {
                loadTagFromFile(samples.first().file ?: return)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading sample tracks: ${e.message}")
        }
    }

    fun refreshExportedLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = mutableListOf<ExportedAudio>()
                val app = getApplication<Application>()

                // Check internal export dir
                val exportDir = getExportDirectory()
                if (exportDir.exists()) {
                    exportDir.listFiles()?.filter { file ->
                        file.isFile && (file.name.endsWith(".mp3", true) ||
                                file.name.endsWith(".m4a", true) ||
                                file.name.endsWith(".wav", true) ||
                                file.name.endsWith(".flac", true))
                    }?.forEach { file ->
                        try {
                            val meta = AudioDecoder.extractMetadata(app, file = file)
                            list.add(
                                ExportedAudio(
                                    file = file,
                                    title = meta.title,
                                    durationMs = meta.durationMs,
                                    sizeBytes = file.length(),
                                    format = meta.format,
                                    dateModified = file.lastModified(),
                                    coverArtBytes = meta.coverArtBytes
                                )
                            )
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }

                // Check public Music/LishStudioRecord
                val publicDir = File(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC),
                    "LishStudioRecord"
                )
                if (publicDir.exists()) {
                    publicDir.listFiles()?.filter { file ->
                        file.isFile && (file.name.endsWith(".mp3", true) ||
                                file.name.endsWith(".m4a", true) ||
                                file.name.endsWith(".wav", true) ||
                                file.name.endsWith(".flac", true))
                    }?.forEach { file ->
                        if (list.none { it.file.name == file.name }) {
                            try {
                                val meta = AudioDecoder.extractMetadata(app, file = file)
                                list.add(
                                    ExportedAudio(
                                        file = file,
                                        title = meta.title,
                                        durationMs = meta.durationMs,
                                        sizeBytes = file.length(),
                                        format = meta.format,
                                        dateModified = file.lastModified(),
                                        coverArtBytes = meta.coverArtBytes
                                    )
                                )
                            } catch (e: Exception) {
                                // ignore
                            }
                        }
                    }
                }

                _exportedFiles.value = list.sortedByDescending { it.dateModified }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing library: ${e.message}")
            }
        }
    }

    fun loadTrackFromUri(uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatus.value = "Membuka file audio..."
            _processingProgress.value = 0.2f
            try {
                val app = getApplication<Application>()
                val trackInfo = AudioDecoder.extractMetadata(app, uri = uri)
                _activeTrack.value = trackInfo

                _processingStatus.value = "Memproses gelombang suara..."
                _processingProgress.value = 0.5f
                val pcm = AudioDecoder.decode(app, uri)
                _activePcm.value = pcm

                _startMs.value = 0L
                _endMs.value = pcm.durationMs
                _waveformBars.value = AudioProcessor.extractWaveform(pcm, 100)

                player.setSource(pcm, 0L, pcm.durationMs)
                _statusMessage.value = "Berhasil memuat: ${trackInfo.title}"
            } catch (e: Exception) {
                Log.e(TAG, "Failed loading track from uri: ${e.message}", e)
                _statusMessage.value = "Gagal memuat audio: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun loadTrack(track: AudioTrackInfo) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatus.value = "Membuka ${track.title}..."
            _processingProgress.value = 0.2f
            try {
                val app = getApplication<Application>()
                _activeTrack.value = track
                val fileOrUri = track.file ?: track.uri ?: return@launch
                val pcm = AudioDecoder.decode(app, fileOrUri)
                _activePcm.value = pcm

                _startMs.value = 0L
                _endMs.value = pcm.durationMs
                _waveformBars.value = AudioProcessor.extractWaveform(pcm, 100)

                player.setSource(pcm, 0L, pcm.durationMs)
            } catch (e: Exception) {
                Log.e(TAG, "Failed loading track: ${e.message}", e)
                _statusMessage.value = "Gagal memuat lagu: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun updateTrimRange(start: Long, end: Long) {
        val duration = _activePcm.value?.durationMs ?: return
        val s = start.coerceIn(0L, duration)
        val e = end.coerceIn(s, duration)
        _startMs.value = s
        _endMs.value = e
        player.updateSelection(s, e)
    }

    fun resetTrimRange() {
        val duration = _activePcm.value?.durationMs ?: return
        _startMs.value = 0L
        _endMs.value = duration
        player.updateSelection(0L, duration)
    }

    fun adjustStartMs(deltaMs: Long) {
        val duration = _activePcm.value?.durationMs ?: return
        val newStart = (_startMs.value + deltaMs).coerceIn(0L, _endMs.value)
        _startMs.value = newStart
        player.updateSelection(newStart, _endMs.value)
    }

    fun adjustEndMs(deltaMs: Long) {
        val duration = _activePcm.value?.durationMs ?: return
        val newEnd = (_endMs.value + deltaMs).coerceIn(_startMs.value, duration)
        _endMs.value = newEnd
        player.updateSelection(_startMs.value, newEnd)
    }

    fun setVolumeGain(gain: Float) {
        _volumeGain.value = gain
        player.updateEffects(gain, _fadeInMs.value, _fadeOutMs.value, _equalizerSettings.value)
    }

    fun setFadeInMs(ms: Long) {
        _fadeInMs.value = ms
        player.updateEffects(_volumeGain.value, ms, _fadeOutMs.value, _equalizerSettings.value)
    }

    fun setFadeOutMs(ms: Long) {
        _fadeOutMs.value = ms
        player.updateEffects(_volumeGain.value, _fadeInMs.value, ms, _equalizerSettings.value)
    }

    fun setEqualizerPreset(preset: EqualizerPreset) {
        _equalizerSettings.value = EqualizerSettings(
            bassGain = preset.bassGain,
            midGain = preset.midGain,
            trebleGain = preset.trebleGain,
            presetName = preset.name
        )
        player.updateEffects(_volumeGain.value, _fadeInMs.value, _fadeOutMs.value, _equalizerSettings.value)
    }

    fun setBassGain(db: Float) {
        _equalizerSettings.value = _equalizerSettings.value.copy(bassGain = db, presetName = null)
        player.updateEffects(_volumeGain.value, _fadeInMs.value, _fadeOutMs.value, _equalizerSettings.value)
    }

    fun setMidGain(db: Float) {
        _equalizerSettings.value = _equalizerSettings.value.copy(midGain = db, presetName = null)
        player.updateEffects(_volumeGain.value, _fadeInMs.value, _fadeOutMs.value, _equalizerSettings.value)
    }

    fun setTrebleGain(db: Float) {
        _equalizerSettings.value = _equalizerSettings.value.copy(trebleGain = db, presetName = null)
        player.updateEffects(_volumeGain.value, _fadeInMs.value, _fadeOutMs.value, _equalizerSettings.value)
    }

    fun resetEqualizer() {
        _equalizerSettings.value = EqualizerSettings()
        player.updateEffects(_volumeGain.value, _fadeInMs.value, _fadeOutMs.value, _equalizerSettings.value)
    }

    fun setThemeMode(isDark: Boolean) {
        _isDarkTheme.value = isDark
    }

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun prepareSaveAs(file: File) {
        _pendingSaveAsFile.value = file
    }

    fun clearPendingSaveAs() {
        _pendingSaveAsFile.value = null
    }

    fun executeSaveAs(targetUri: Uri, onComplete: ((Boolean) -> Unit)? = null) {
        val file = _pendingSaveAsFile.value ?: return
        viewModelScope.launch {
            val ok = StorageExporter.saveAsToUri(getApplication(), file, targetUri)
            _pendingSaveAsFile.value = null
            if (ok) {
                _statusMessage.value = "File berhasil disimpan ke lokasi pilihan Anda!"
            } else {
                _statusMessage.value = "Gagal menyimpan file ke lokasi tujuan."
            }
            onComplete?.invoke(ok)
        }
    }

    fun saveToDevicePublicMusic(file: File, mimeType: String = "audio/mpeg", onComplete: ((Uri?) -> Unit)? = null) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatus.value = "Menyimpan ke Memori HP..."
            try {
                val uri = StorageExporter.saveToPhoneMemory(getApplication(), file, file.name, mimeType)
                if (uri != null) {
                    _statusMessage.value = "Tersimpan di Penyimpanan Internal -> Music/LishStudioRecord!"
                } else {
                    _statusMessage.value = "Gagal menyimpan ke memori HP"
                }
                refreshExportedLibrary()
                onComplete?.invoke(uri)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun exportCurrentTrack(options: ExportOptions, onComplete: ((File) -> Unit)? = null) {
        val pcm = _activePcm.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatus.value = "Menerapkan efek & memproses audio..."
            _processingProgress.value = 0.1f

            try {
                val trimmed = AudioProcessor.trim(pcm, _startMs.value, _endMs.value)
                val processed = AudioProcessor.applyEqualizer(
                    AudioProcessor.applyFades(
                        AudioProcessor.applyGain(trimmed, _volumeGain.value),
                        _fadeInMs.value,
                        _fadeOutMs.value
                    ),
                    _equalizerSettings.value
                )

                _processingStatus.value = "Mengenkode ke format ${options.format.displayName}..."
                val exportDir = getExportDirectory()
                val safeName = options.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val finalFileName = if (safeName.endsWith(".${options.format.extension}", true)) safeName else "$safeName.${options.format.extension}"
                val targetFile = File(exportDir, finalFileName)

                AudioEncoder.encode(processed, targetFile, options) { progress ->
                    _processingProgress.value = 0.2f + (progress * 0.7f)
                }

                // If track has cover art and is MP3, embed it
                val trackCover = _activeTrack.value?.coverArtBytes
                if (options.format == AudioFormat.MP3 && trackCover != null && trackCover.isNotEmpty()) {
                    _processingStatus.value = "Menanamkan cover album..."
                    val meta = SongMetadata(
                        title = options.fileName.substringBeforeLast("."),
                        artist = _activeTrack.value?.artist ?: "Artis Tidak Diketahui",
                        coverArtBytes = trackCover,
                        coverMimeType = AudioTagManager.detectMimeType(trackCover)
                    )
                    AudioTagManager.writeMp3Tags(targetFile, targetFile, meta)
                }

                // Also save to device public music directory
                StorageExporter.saveToPhoneMemory(getApplication(), targetFile, finalFileName, options.format.mimeType)

                refreshExportedLibrary()
                _statusMessage.value = "Ekspor sukses: $finalFileName (Tersimpan di Music/LishStudioRecord)"
                onComplete?.invoke(targetFile)
            } catch (e: Exception) {
                Log.e(TAG, "Export failed: ${e.message}", e)
                _statusMessage.value = "Ekspor gagal: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // --- Merge Actions ---
    fun addTrackToMerge(track: AudioTrackInfo) {
        val list = _mergeTracks.value.toMutableList()
        list.add(track)
        _mergeTracks.value = list
    }

    fun clearMergeTracks() {
        _mergeTracks.value = emptyList()
    }

    fun removeTrackFromMerge(index: Int) {
        val list = _mergeTracks.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _mergeTracks.value = list
        }
    }

    fun moveTrackMergeUp(index: Int) {
        if (index <= 0) return
        val list = _mergeTracks.value.toMutableList()
        val item = list.removeAt(index)
        list.add(index - 1, item)
        _mergeTracks.value = list
    }

    fun moveTrackMergeDown(index: Int) {
        val list = _mergeTracks.value.toMutableList()
        if (index >= list.size - 1) return
        val item = list.removeAt(index)
        list.add(index + 1, item)
        _mergeTracks.value = list
    }

    fun setMergeGapMs(gap: Long) {
        _mergeGapMs.value = gap
    }

    fun executeMerge(fileName: String, format: AudioFormat = AudioFormat.MP3, onComplete: ((File) -> Unit)? = null) {
        val tracks = _mergeTracks.value
        if (tracks.size < 2) {
            _statusMessage.value = "Pilih minimal 2 trek audio untuk digabungkan"
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatus.value = "Mendekode trek audio..."
            _processingProgress.value = 0.1f

            try {
                val app = getApplication<Application>()
                val pcmList = mutableListOf<PcmData>()

                for ((idx, t) in tracks.withIndex()) {
                    _processingStatus.value = "Mendekode ${idx + 1}/${tracks.size}: ${t.title}..."
                    val source = t.file ?: t.uri ?: continue
                    val pcm = AudioDecoder.decode(app, source)
                    pcmList.add(pcm)
                    _processingProgress.value = 0.1f + ((idx.toFloat() / tracks.size) * 0.4f)
                }

                _processingStatus.value = "Menggabungkan trek PCM..."
                _processingProgress.value = 0.55f
                val mergedPcm = AudioProcessor.mergeTracks(pcmList, _mergeGapMs.value)

                _processingStatus.value = "Mengenkode ke format ${format.displayName}..."
                val exportDir = getExportDirectory()
                val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val finalFileName = if (safeName.endsWith(".${format.extension}", true)) safeName else "$safeName.${format.extension}"
                val targetFile = File(exportDir, finalFileName)

                val options = ExportOptions(fileName = safeName, format = format)
                AudioEncoder.encode(mergedPcm, targetFile, options) { progress ->
                    _processingProgress.value = 0.6f + (progress * 0.35f)
                }

                StorageExporter.saveToPhoneMemory(app, targetFile, finalFileName, format.mimeType)
                refreshExportedLibrary()
                _statusMessage.value = "Penggabungan selesai: $finalFileName (Tersimpan di Music/LishStudioRecord)"
                onComplete?.invoke(targetFile)
            } catch (e: Exception) {
                Log.e(TAG, "Merge failed: ${e.message}", e)
                _statusMessage.value = "Gagal menggabungkan trek: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // --- Converter Actions ---
    fun selectTrackForConverter(track: AudioTrackInfo) {
        _converterTrack.value = track
    }

    fun setConverterFormat(format: AudioFormat) {
        _converterFormat.value = format
    }

    fun setConverterBitrate(bitrate: Int) {
        _converterBitrate.value = bitrate
    }

    fun executeConvert(fileName: String, onComplete: ((File) -> Unit)? = null) {
        val track = _converterTrack.value
        if (track == null) {
            _statusMessage.value = "Pilih trek yang akan dikonversi"
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatus.value = "Mendekode audio asli..."
            _processingProgress.value = 0.2f

            try {
                val app = getApplication<Application>()
                val source = track.file ?: track.uri ?: return@launch
                val pcm = AudioDecoder.decode(app, source)

                _processingStatus.value = "Mengenkode ke ${_converterFormat.value.displayName}..."
                _processingProgress.value = 0.5f

                val exportDir = getExportDirectory()
                val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val finalFileName = if (safeName.endsWith(".${_converterFormat.value.extension}", true)) safeName else "$safeName.${_converterFormat.value.extension}"
                val targetFile = File(exportDir, finalFileName)

                val options = ExportOptions(
                    fileName = safeName,
                    format = _converterFormat.value,
                    bitrateKbps = _converterBitrate.value
                )

                AudioEncoder.encode(pcm, targetFile, options) { progress ->
                    _processingProgress.value = 0.5f + (progress * 0.45f)
                }

                // Carry over cover art if converting to MP3
                val cover = track.coverArtBytes
                if (_converterFormat.value == AudioFormat.MP3 && cover != null && cover.isNotEmpty()) {
                    val meta = SongMetadata(
                        title = safeName.substringBeforeLast("."),
                        artist = track.artist,
                        coverArtBytes = cover,
                        coverMimeType = AudioTagManager.detectMimeType(cover)
                    )
                    AudioTagManager.writeMp3Tags(targetFile, targetFile, meta)
                }

                StorageExporter.saveToPhoneMemory(app, targetFile, finalFileName, _converterFormat.value.mimeType)
                refreshExportedLibrary()
                _statusMessage.value = "Konversi berhasil: $finalFileName (Tersimpan di Music/LishStudioRecord)"
                onComplete?.invoke(targetFile)
            } catch (e: Exception) {
                Log.e(TAG, "Conversion failed: ${e.message}", e)
                _statusMessage.value = "Konversi gagal: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // --- Library Actions ---
    fun toggleLibraryPlay(audio: ExportedAudio) {
        if (_libraryPlayingPath.value == audio.file.absolutePath) {
            libraryMediaPlayer?.stop()
            libraryMediaPlayer?.release()
            libraryMediaPlayer = null
            _libraryPlayingPath.value = null
        } else {
            libraryMediaPlayer?.stop()
            libraryMediaPlayer?.release()
            libraryMediaPlayer = MediaPlayer().apply {
                setDataSource(audio.file.absolutePath)
                setOnCompletionListener {
                    _libraryPlayingPath.value = null
                }
                prepare()
                start()
            }
            _libraryPlayingPath.value = audio.file.absolutePath
        }
    }

    fun shareExportedFile(context: Context, audio: ExportedAudio) {
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                audio.file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = audio.format.mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Bagikan Audio"))
        } catch (e: Exception) {
            Log.e(TAG, "Share failed: ${e.message}")
            _statusMessage.value = "Gagal membagikan file: ${e.localizedMessage}"
        }
    }

    fun deleteExportedFile(audio: ExportedAudio) {
        try {
            if (_libraryPlayingPath.value == audio.file.absolutePath) {
                libraryMediaPlayer?.stop()
                libraryMediaPlayer?.release()
                libraryMediaPlayer = null
                _libraryPlayingPath.value = null
            }
            if (audio.file.exists()) {
                audio.file.delete()
            }
            refreshExportedLibrary()
            _statusMessage.value = "File dihapus: ${audio.title}"
        } catch (e: Exception) {
            _statusMessage.value = "Gagal menghapus file: ${e.localizedMessage}"
        }
    }

    fun getExportDirectory(): File {
        val app = getApplication<Application>()
        val dir = File(app.getExternalFilesDir(null) ?: app.filesDir, "LishStudioRecord")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    // --- Tag Editor Actions ---
    fun loadTagFromUri(uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatus.value = "Membaca tag & info lagu..."
            try {
                val app = getApplication<Application>()
                _selectedTagUri.value = uri

                // Copy to cache file for reliable tagging operations
                val cacheDir = File(app.cacheDir, "tag_editor")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val tempFile = File(cacheDir, "input_tag_${System.currentTimeMillis()}.mp3")
                app.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { out ->
                        input.copyTo(out)
                    }
                }
                _selectedTagFile.value = tempFile

                val metadata = AudioTagManager.readMetadata(app, uri = uri, file = tempFile)
                _tagMetadata.value = metadata
                _statusMessage.value = "Tag & Cover lagu berhasil dimuat!"
            } catch (e: Exception) {
                Log.e(TAG, "Failed reading tags from uri: ${e.message}", e)
                _statusMessage.value = "Gagal membaca tag lagu: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun loadTagFromFile(file: File) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatus.value = "Membaca informasi lagu..."
            try {
                val app = getApplication<Application>()
                _selectedTagFile.value = file
                _selectedTagUri.value = null

                val metadata = AudioTagManager.readMetadata(app, file = file)
                _tagMetadata.value = metadata
                _statusMessage.value = "Informasi lagu berhasil dimuat!"
            } catch (e: Exception) {
                Log.e(TAG, "Failed reading tags: ${e.message}", e)
                _statusMessage.value = "Gagal membaca tag: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun updateSongMetadata(updated: SongMetadata) {
        _tagMetadata.value = updated
    }

    fun updateCoverArt(bytes: ByteArray, mimeType: String = "image/jpeg") {
        val current = _tagMetadata.value ?: return
        _tagMetadata.value = current.copy(
            coverArtBytes = bytes,
            coverMimeType = mimeType
        )
        _statusMessage.value = "Cover album berhasil diperbarui!"
    }

    fun applyPresetCover(presetIndex: Int) {
        val current = _tagMetadata.value ?: return
        val (bytes, mime) = AudioTagManager.generatePresetCover(presetIndex, 600)
        _tagMetadata.value = current.copy(
            coverArtBytes = bytes,
            coverMimeType = mime
        )
        _statusMessage.value = "Preset cover album berhasil diterapkan!"
    }

    fun removeCoverArt() {
        val current = _tagMetadata.value ?: return
        _tagMetadata.value = current.copy(coverArtBytes = null)
        _statusMessage.value = "Cover album dihapus"
    }

    /**
     * Saves metadata and embedded APIC cover art to the MP3 file,
     * writes directly to phone memory Music/LishStudioRecord/,
     * updates SAF Uri if present, and scans file into Android MediaStore.
     */
    fun saveSongMetadata(customFileName: String? = null, onComplete: ((File) -> Unit)? = null) {
        val currentMeta = _tagMetadata.value ?: return
        val currentFile = _selectedTagFile.value ?: return

        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatus.value = "Menanamkan Cover Album & Tag ID3v2..."
            _processingProgress.value = 0.2f

            val app = getApplication<Application>()
            try {
                val safeName = (customFileName ?: currentMeta.title.ifBlank { currentFile.nameWithoutExtension })
                    .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    .let { if (it.endsWith(".mp3", true)) it else "$it.mp3" }

                val exportDir = getExportDirectory()
                val targetFile = File(exportDir, safeName)

                val isMp3 = AudioTagManager.isMp3File(currentFile)
                val success: Boolean

                if (isMp3) {
                    // Lossless ID3v2.3 tag writing directly from current file
                    success = AudioTagManager.writeMp3Tags(currentFile, targetFile, currentMeta)
                } else {
                    // Convert non-MP3 to MP3 first
                    _processingStatus.value = "Mendekode audio untuk format MP3..."
                    val pcm = AudioDecoder.decode(app, currentFile)
                    val rawMp3 = File(app.cacheDir, "raw_${System.currentTimeMillis()}.mp3")
                    AudioEncoder.encode(pcm, rawMp3, ExportOptions(safeName, AudioFormat.MP3))
                    success = AudioTagManager.writeMp3Tags(rawMp3, targetFile, currentMeta)
                    rawMp3.delete()
                }

                _processingProgress.value = 0.7f

                if (success && targetFile.exists()) {
                    _processingStatus.value = "Menyimpan ke Memori HP & Memindai Galeri..."

                    // 1. Save directly to public phone storage (Music/LishStudioRecord) & scan MediaStore
                    val publicUri = StorageExporter.saveToPhoneMemory(
                        app,
                        targetFile,
                        safeName,
                        "audio/mpeg"
                    )

                    // 2. If opened from an existing Content URI via SAF, write back to original Uri as well
                    val sourceUri = _selectedTagUri.value
                    if (sourceUri != null && sourceUri.scheme == "content") {
                        StorageExporter.saveAsToUri(app, targetFile, sourceUri)
                    }

                    // Update selected file to the newly saved file
                    _selectedTagFile.value = targetFile

                    refreshExportedLibrary()
                    _statusMessage.value = "Cover & Tag lagu berhasil disimpan ke Memori HP (Music/LishStudioRecord)!"
                    onComplete?.invoke(targetFile)
                } else {
                    _statusMessage.value = "Gagal menulis tag ke file MP3"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Save song metadata failed: ${e.message}", e)
                _statusMessage.value = "Gagal menyimpan info lagu: ${e.localizedMessage}"
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
