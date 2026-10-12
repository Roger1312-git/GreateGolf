package com.greategolf.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayCircle
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
import com.greategolf.ui.course.HoleGridScreen
import com.greategolf.ui.course.HoleMapScreen
import com.greategolf.ui.map.MapScreen
import com.greategolf.ui.home.HomeScreen
import com.greategolf.ui.more.MoreScreen
import com.greategolf.ui.play.PlayTab
import com.greategolf.ui.stats.StatsScreen
import com.greategolf.ui.score.RoundScreen
import com.greategolf.ui.score.RoundsScreen
import com.greategolf.ui.swing.SwingScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Trang chủ", Icons.Filled.Home),
    Courses("map", "Sân", Icons.Filled.Map),
    Play("play", "Chơi", Icons.Filled.PlayCircle),
    Stats("stats", "Thống kê", Icons.Filled.BarChart),
    More("more", "Thêm", Icons.Filled.MoreHoriz),
}

private val MORE_ROUTES = listOf("clubs", "swing", "rounds", "round/")
private val COURSE_ROUTES = listOf("course/", "hole3d/", "holes/", "holemap/")

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
                        (tab == Tab.More && route != null && MORE_ROUTES.any { route.startsWith(it) || route == it }) ||
                        (tab == Tab.Courses && route != null && COURSE_ROUTES.any { route.startsWith(it) })
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
        NavHost(nav, startDestination = Tab.Home.route, modifier = Modifier.padding(padding).statusBarsPadding()) {
            composable(Tab.Home.route) {
                HomeScreen(
                    onPlay = { go(Tab.Play) },
                    onOpenRound = { id -> nav.navigate("round/$id") },
                    onStats = { go(Tab.Stats) },
                    onCourses = { go(Tab.Courses) },
                    onClubs = { nav.navigate("clubs") },
                    onSwing = { nav.navigate("swing") },
                    onHistory = { nav.navigate("rounds") },
                )
            }
            composable(Tab.Courses.route) {
                MapScreen(
                    onPlayHere = { go(Tab.Play) },
                    onOpenRound = { id -> nav.navigate("round/$id") },
                    onOpenCourse = { id -> nav.navigate("course/$id") },
                )
            }
            composable("course/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments!!.getString("id")!!
                CourseMapScreen(
                    courseId = id, onBack = { nav.popBackStack() },
                    onOpen3D = { idx -> nav.navigate("hole3d/$id/$idx") },
                    onOpenHoles = { nav.navigate("holes/$id") },
                    onOpenHoleMap = { idx -> nav.navigate("holemap/$id/$idx") },
                )
            }
            composable("holes/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments!!.getString("id")!!
                HoleGridScreen(id, onBack = { nav.popBackStack() }, onOpenHole = { idx -> nav.navigate("holemap/$id/$idx") })
            }
            composable(
                "holemap/{id}/{idx}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("idx") { type = NavType.IntType }),
            ) { entry ->
                val id = entry.arguments!!.getString("id")!!
                HoleMapScreen(id, entry.arguments!!.getInt("idx"), onBack = { nav.popBackStack() }, on3D = { idx -> nav.navigate("hole3d/$id/$idx") })
            }
            composable(
                "hole3d/{id}/{idx}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("idx") { type = NavType.IntType }),
            ) { entry ->
                Hole3DScreen(entry.arguments!!.getString("id")!!, entry.arguments!!.getInt("idx"), onBack = { nav.popBackStack() })
            }
            composable(Tab.Play.route) {
                PlayTab(
                    onOpenScorecard = { id -> nav.navigate("round/$id") },
                    onOpen3D = { courseId, idx -> nav.navigate("hole3d/$courseId/$idx") },
                    onFinished = { id -> nav.navigate("round/$id") },
                )
            }
            composable(Tab.Stats.route) { StatsScreen() }
            composable(Tab.More.route) {
                MoreScreen(onClubs = { nav.navigate("clubs") }, onSwing = { nav.navigate("swing") }, onHistory = { nav.navigate("rounds") })
            }
            composable("rounds") { RoundsScreen(onOpen = { id -> nav.navigate("round/$id") }) }
            composable(
                "round/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                RoundScreen(roundId = entry.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
            }
            composable("clubs") { ClubsScreen() }
            composable("swing") { SwingScreen() }
        }
    }
}
