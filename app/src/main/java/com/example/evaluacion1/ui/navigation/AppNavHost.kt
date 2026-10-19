package com.example.evaluacion1.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.evaluacion1.model.User
import com.example.evaluacion1.ui.login.LoginScreen
import com.example.evaluacion1.ui.tickets.CreateTicketScreen
import com.example.evaluacion1.ui.tickets.TicketDetailScreen
import com.example.evaluacion1.ui.tickets.TicketListScreen
import com.example.evaluacion1.viewmodel.SessionState
import com.example.evaluacion1.viewmodel.SessionViewModel

sealed class Screen(val route: String) {
    data object Tickets : Screen("tickets")
    data object Create : Screen("create_ticket")
    data object Detail : Screen("ticket/{ticketId}") {
        const val ARG_TICKET_ID = "ticketId"
        fun createRoute(ticketId: String) = "ticket/$ticketId"
    }
}

/** Chooses between login and the main graph depending on the persisted session. */
@Composable
fun AppNavHost(
    sessionViewModel: SessionViewModel = viewModel(factory = SessionViewModel.Factory)
) {
    val session by sessionViewModel.state.collectAsStateWithLifecycle()

    when (val current = session) {
        SessionState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        SessionState.LoggedOut -> LoginScreen(onLoggedIn = sessionViewModel::onLoggedIn)

        is SessionState.LoggedIn -> MainNavigation(
            user = current.user,
            onLogout = sessionViewModel::logout
        )
    }
}

@Composable
private fun MainNavigation(user: User, onLogout: () -> Unit) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Tickets.route,
        enterTransition = { slideInHorizontally(initialOffsetX = { it / 5 }) + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it / 5 }) + fadeOut() }
    ) {
        composable(Screen.Tickets.route) {
            TicketListScreen(
                user = user,
                onOpenTicket = { navController.navigate(Screen.Detail.createRoute(it)) },
                onCreateTicket = { navController.navigate(Screen.Create.route) },
                onLogout = onLogout
            )
        }
        composable(Screen.Create.route) {
            CreateTicketScreen(
                user = user,
                onBack = { navController.popBackStack() },
                onCreated = { ticketId ->
                    navController.navigate(Screen.Detail.createRoute(ticketId)) {
                        popUpTo(Screen.Create.route) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument(Screen.Detail.ARG_TICKET_ID) { type = NavType.StringType })
        ) { entry ->
            val ticketId = entry.arguments?.getString(Screen.Detail.ARG_TICKET_ID).orEmpty()
            TicketDetailScreen(
                user = user,
                ticketId = ticketId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
