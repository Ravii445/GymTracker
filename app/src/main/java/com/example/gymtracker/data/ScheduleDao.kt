package com.example.gymtracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Query("""
        SELECT s.dayOfWeek AS dayOfWeek,
               s.routineId AS routineId,
               r.name AS routineName
        FROM scheduled_routines s
        LEFT JOIN routines r ON r.id = s.routineId
        ORDER BY s.dayOfWeek
    """)
    fun getScheduleWithNames(): Flow<List<ScheduleEntry>>

    @Query("DELETE FROM scheduled_routines WHERE dayOfWeek = :day")
    suspend fun clearDay(day: Int)

    @Insert
    suspend fun insertSchedule(entry: ScheduledRoutine)
}
