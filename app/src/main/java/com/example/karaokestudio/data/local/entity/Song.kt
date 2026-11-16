package com.example.karaokestudio.data.local.entity

import android.net.Uri
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.File
import java.util.Locale

@Entity(tableName = "songs", indices = [Index(value = ["slug"], unique = true)])
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val lyrics: String,
    val audioSource: String,
    val coverSource: String? = null,
    val genre: String = "Інше",
    val isDemo: Boolean = false,
    val searchKey: String = buildSearchKey(title, artist),
    val slug: String? = null,
    val remoteUpdatedAt: Long = 0
) {
    companion object {
        fun buildSearchKey(title: String, artist: String): String =
            "$title $artist".trim().lowercase(Locale.ROOT)

        fun localFileOf(source: String): File? =
            if (source.startsWith("file:")) Uri.parse(source).path?.let { File(it) } else null
    }
}
