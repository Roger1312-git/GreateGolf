package com.greategolf.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SportsGolf
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.greategolf.ui.clubs.ClubsScreen
import com.greategolf.ui.course.CourseMapScreen
import com.greategolf.ui.course.Hole3DScreen
import com.greategolf.ui.map.MapScreen
import com.greategolf.ui.play.PlayScreen
import com.greategolf.ui.score.RoundScreen
import com.greategolf.ui.score.RoundsScreen
import com.greategolf.ui.swing.SwingScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Map("map", "Bản đồ", Icons.Filled.Map),
    Play("play", "GPS", Icons.Filled.GpsFixed),
    Score("rounds", "Scorecard", Icons.AutoMirrored.Filled.ListAlt),
    Clubs("clubs", "Gậy", Icons.Filled.SportsGolf),
    Swing("swing", "Swing", Icons.Filled.Videocam),
}

@Composable
fun GolfRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    fun go(tab: Tab) = nav.navigate(tab.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    val selected = route == tab.route ||
                        (tab == Tab.Score && route?.startsWith("round/") == true) ||
                        (tab == Tab.Map && (route?.startsWith("course/") == true || route?.startsWith("hole3d/") == true))
                    NavigationBarItem(
                        selected = selected,
                        onClick = { go(tab) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.Map.route, modifier = Modifier.padding(padding).statusBarsPadding()) {
            composable(Tab.Map.route) {
                MapScreen(
                    onPlayHere = { go(Tab.Play) },
                    onOpenRound = { id -> nav.navigate("round/$id") },
                    onOpenCourse = { id -> nav.navigate("course/$id") },
                )
            }
            composable("course/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments!!.getString("id")!!
                CourseMapScreen(courseId = id, onBack = { nav.popBackStack() }, onOpen3D = { idx -> nav.navigate("hole3d/$id/$idx") })
            }
            composable(
                "hole3d/{id}/{idx}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("idx") { type = NavType.IntType }),
            ) { entry ->
                Hole3DScreen(entry.arguments!!.getString("id")!!, entry.arguments!!.getInt("idx"), onBack = { nav.popBackStack() })
            }
            composable(Tab.Play.route) { PlayScreen(onOpenScorecard = { id -> nav.navigate("round/$id") }) }
            composable(Tab.Score.route) { RoundsScreen(onOpen = { id -> nav.navigate("round/$id") }) }
            composable(
                "round/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                RoundScreen(roundId = entry.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
            }
            composable(Tab.Clubs.route) { ClubsScreen() }
            composable(Tab.Swing.route) { SwingScreen() }
        }
    }
}
