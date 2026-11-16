package com.example.karaokestudio.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recordings",
    foreignKeys = [
        ForeignKey(
            entity = Performance::class,
            parentColumns = ["id"],
            childColumns = ["performanceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["performanceId"], unique = true)]
)
data class Recording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val performanceId: Long,
    val filePath: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val createdAt: Long
)
