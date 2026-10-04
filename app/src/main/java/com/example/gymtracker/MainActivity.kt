package com.example.gymtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.gymtracker.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GymTrackerTheme { GymTrackerApp() }
        }
    }
}

@Composable
fun GymTrackerApp() {
    val navController = rememberNavController()
    val workoutVm: WorkoutViewModel = viewModel()
    val routineVm: RoutineViewModel = viewModel()

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in listOf("dashboard", "routines", "planner", "progress", "history")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == "dashboard",
                        onClick = { navController.navigate("dashboard") { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "routines",
                        onClick = { navController.navigate("routines") { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.FitnessCenter, null) },
                        label = { Text("Routines") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "planner",
                        onClick = { navController.navigate("planner") { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.CalendarMonth, null) },
                        label = { Text("Planner") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "progress",
                        onClick = { navController.navigate("progress") { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.TrendingUp, null) },
                        label = { Text("Progress") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "history",
                        onClick = { navController.navigate("history") { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.List, null) },
                        label = { Text("History") }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(padding)
        ) {
            composable("dashboard") {
                DashboardScreen(
                    viewModel = workoutVm,
                    onStartWorkout = { navController.navigate("add") },
                    onOpenRoutines = { navController.navigate("routines") }
                )
            }
            composable("add") {
                AddWorkoutScreen(viewModel = workoutVm, onDone = { navController.popBackStack() })
            }
            composable("history") { HistoryScreen(workoutVm) }
            composable("progress") { ProgressScreen(workoutVm) }
            composable("planner") { PlannerScreen(routineVm) }

            composable("routines") {
                RoutineListScreen(
                    viewModel = routineVm,
                    onCreate = { navController.navigate("routineEdit/-1") },
                    onEdit = { id -> navController.navigate("routineEdit/$id") },
                    onRun = { id -> navController.navigate("routineRun/$id") }
                )
            }
            composable(
                "routineEdit/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: -1L
                RoutineEditorScreen(
                    viewModel = routineVm,
                    routineId = if (id <= 0) null else id,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                "routineRun/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                RunRoutineScreen(
                    viewModel = routineVm,
                    routineId = id,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
