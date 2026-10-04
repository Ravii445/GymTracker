package com.example.gymtracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {

    @Transaction
    @Query("SELECT * FROM routines ORDER BY createdAt DESC")
    fun getRoutinesWithExercises(): Flow<List<RoutineWithExercises>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getRoutineWithExercises(id: Long): RoutineWithExercises?

    @Insert
    suspend fun insertRoutine(routine: Routine): Long

    @Insert
    suspend fun insertExercises(exercises: List<RoutineExercise>)

    @Update
    suspend fun updateRoutine(routine: Routine)

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    suspend fun clearExercises(routineId: Long)

    @Delete
    suspend fun deleteRoutine(routine: Routine)
}
