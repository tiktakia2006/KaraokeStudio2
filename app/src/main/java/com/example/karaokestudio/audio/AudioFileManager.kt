package com.example.karaokestudio.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.example.karaokestudio.data.local.entity.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

data class ImportedAudio(val file: File, val displayName: String, val durationMs: Long)

class AudioFileManager(private val context: Context) {

    private val recordingsDir: File get() = File(context.filesDir, "recordings").apply { mkdirs() }
    private val demoDir: File get() = File(context.filesDir, "demo").apply { mkdirs() }
    private val songsDir: File get() = File(context.filesDir, "songs").apply { mkdirs() }

    fun newRecordingFile(): File = File(recordingsDir, "rec_${System.currentTimeMillis()}.m4a")

    suspend fun resolveAudio(song: Song): Uri? = withContext(Dispatchers.IO) {
        val source = song.audioSource
        when {
            source.startsWith("demo:") -> {
                val preset = DemoAudioGenerator.presetFor(source.removePrefix("demo:"))
                    ?: return@withContext null
                val file = File(demoDir, "${preset.slug}.wav")
                if (!file.exists() || file.length() < 1_000) DemoAudioGenerator.render(preset, file)
                Uri.fromFile(file)
            }
            source.isBlank() -> null
            else -> {
                val local = Song.localFileOf(source)
                if (local != null && !local.exists()) null else Uri.parse(source)
            }
        }
    }

    suspend fun importAudio(uri: Uri): ImportedAudio = withContext(Dispatchers.IO) {
        val name = queryDisplayName(uri) ?: "audio"
        val fromName = name.substringAfterLast('.', "").lowercase()
        val extension = if (fromName in AUDIO_EXTENSIONS) fromName else guessExtension(context.contentResolver.getType(uri))
        val target = File(songsDir, "import_${System.currentTimeMillis()}.$extension")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Не вдалося відкрити файл" }
                target.outputStream().use { output -> copyLimited(input, output, MAX_AUDIO_BYTES, "Файл завеликий: максимум 60 МБ") }
            }
            val duration = readDuration(target)
            require(duration > 0) { "Файл не схожий на аудіо: не вдалося визначити тривалість" }
            ImportedAudio(target, name, duration)
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Не вдалося відкрити файл" }
            val buffer = ByteArrayOutputStream()
            copyLimited(input, buffer, MAX_TEXT_BYTES, "Текстовий файл завеликий: максимум 200 КБ")
            buffer.toString("UTF-8").removePrefix("\uFEFF")
        }
    }

    fun cleanupOrphans(recordingPaths: Collection<String>, userSongSources: Collection<String>) {
        val cutoff = System.currentTimeMillis() - ORPHAN_AGE_MS
        val keepRecordings = recordingPaths.map { File(it).absolutePath }.toSet()
        recordingsDir.listFiles()?.forEach {
            if (it.absolutePath !in keepRecordings && it.lastModified() < cutoff) it.delete()
        }
        val keepSongs = userSongSources.mapNotNull { Song.localFileOf(it)?.absolutePath }.toSet()
        songsDir.listFiles()?.forEach {
            if (it.absolutePath !in keepSongs && it.lastModified() < cutoff) it.delete()
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun guessExtension(mime: String?): String =
        (mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "mp3").lowercase()

    private fun readDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long, tooLargeMessage: String) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { tooLargeMessage }
            output.write(buffer, 0, read)
        }
    }

    private companion object {
        val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "wav", "ogg", "flac", "aac")
        const val MAX_AUDIO_BYTES = 60L * 1_048_576
        const val MAX_TEXT_BYTES = 200L * 1024
        const val ORPHAN_AGE_MS = 60L * 60 * 1000
    }
}
