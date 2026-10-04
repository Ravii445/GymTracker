package com.example.gymtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_routines")
data class ScheduledRoutine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayOfWeek: Int, // 1 = Monday ... 7 = Sunday
    val routineId: Long
)

data class ScheduleEntry(
    val dayOfWeek: Int,
    val routineId: Long,
    val routineName: String
)
