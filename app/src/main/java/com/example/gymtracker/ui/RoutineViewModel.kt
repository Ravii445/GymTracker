package com.example.gymtracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtracker.data.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoutineViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val routineDao = db.routineDao()
    private val scheduleDao = db.scheduleDao()
    private val workoutDao = db.workoutDao()

    val routines: StateFlow<List<RoutineWithExercises>> =
        routineDao.getRoutinesWithExercises()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val schedule: StateFlow<List<ScheduleEntry>> =
        scheduleDao.getScheduleWithNames()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveRoutine(
        existingId: Long?,
        name: String,
        description: String,
        exercises: List<RoutineExercise>
    ) {
        viewModelScope.launch {
            if (existingId == null) {
                val newId = routineDao.insertRoutine(Routine(name = name, description = description))
                routineDao.insertExercises(exercises.mapIndexed { i, e ->
                    e.copy(routineId = newId, position = i)
                })
            } else {
                routineDao.updateRoutine(
                    Routine(id = existingId, name = name, description = description)
                )
                routineDao.clearExercises(existingId)
                routineDao.insertExercises(exercises.mapIndexed { i, e ->
                    e.copy(id = 0, routineId = existingId, position = i)
                })
            }
        }
    }

    fun deleteRoutine(routine: Routine) {
        viewModelScope.launch { routineDao.deleteRoutine(routine) }
    }

    fun getRoutine(id: Long, onLoaded: (RoutineWithExercises) -> Unit) {
        viewModelScope.launch { routineDao.getRoutineWithExercises(id)?.let(onLoaded) }
    }

    fun setSchedule(day: Int, routineId: Long) {
        viewModelScope.launch {
            scheduleDao.clearDay(day)
            scheduleDao.insertSchedule(ScheduledRoutine(dayOfWeek = day, routineId = routineId))
        }
    }

    fun clearDay(day: Int) {
        viewModelScope.launch { scheduleDao.clearDay(day) }
    }

    fun logCompletedRoutine(routine: RoutineWithExercises, actual: Map<Long, Triple<Int, Int, Float>>) {
        viewModelScope.launch {
            routine.exercises.forEach { ex ->
                val (s, r, w) = actual[ex.id] ?: Triple(ex.sets, ex.reps, ex.weight)
                workoutDao.insert(
                    Workout(exercise = ex.exerciseName, sets = s, reps = r, weight = w)
                )
            }
        }
    }
}
