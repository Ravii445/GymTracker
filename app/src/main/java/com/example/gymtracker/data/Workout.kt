package com.example.gymtracker.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exercise: String,
    val date: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "set_entries",
    foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workoutId")]
)
data class SetEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val setIndex: Int,
    val reps: Int,
    val weight: Float
)

data class WorkoutWithSets(
    @Embedded val workout: Workout,
    @Relation(parentColumn = "id", entityColumn = "workoutId")
    val sets: List<SetEntry>
) {
    val totalSets: Int get() = sets.size
    val totalReps: Int get() = sets.sumOf { it.reps }
    val totalVolume: Float get() = sets.sumOf { (it.reps * it.weight).toDouble() }.toFloat()
    val topWeight: Float get() = sets.maxOfOrNull { it.weight } ?: 0f
    val estimated1RM: Float get() =
        sets.maxOfOrNull { it.weight * (1 + it.reps / 30f) } ?: 0f
}
