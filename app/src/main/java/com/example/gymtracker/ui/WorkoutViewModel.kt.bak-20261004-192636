package com.example.gymtracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtracker.data.AppDatabase
import com.example.gymtracker.data.Workout
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).workoutDao()

    val workouts: StateFlow<List<Workout>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addWorkout(exercise: String, sets: Int, reps: Int, weight: Float) {
        viewModelScope.launch {
            dao.insert(Workout(exercise = exercise, sets = sets, reps = reps, weight = weight))
        }
    }

    fun deleteWorkout(workout: Workout) {
        viewModelScope.launch {
            dao.delete(workout)
        }
    }
}
