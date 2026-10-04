package com.example.gymtracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY date DESC")
    fun getAllWithSets(): Flow<List<WorkoutWithSets>>

    @Insert
    suspend fun insertWorkout(workout: Workout): Long

    @Insert
    suspend fun insertSets(sets: List<SetEntry>)

    @Transaction
    suspend fun insertWorkoutWithSets(exercise: String, sets: List<SetEntry>): Long {
        val id = insertWorkout(Workout(exercise = exercise))
        insertSets(sets.mapIndexed { i, s -> s.copy(id = 0, workoutId = id, setIndex = i) })
        return id
    }

    @Delete
    suspend fun deleteWorkout(workout: Workout)
}
