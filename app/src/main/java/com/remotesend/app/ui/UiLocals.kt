package com.remotesend.app.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.remotesend.app.di.AppContainer
import com.remotesend.app.ui.screens.console.ConsoleViewModel
import com.remotesend.app.ui.screens.home.HomeViewModel
import com.remotesend.app.ui.screens.installed.InstalledViewModel
import com.remotesend.app.ui.screens.logs.LogsViewModel
import com.remotesend.app.ui.screens.packages.PackagesViewModel
import com.remotesend.app.ui.screens.queue.QueueViewModel
import com.remotesend.app.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.flow.Flow

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer no proporcionado")
}

val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("SnackbarHostState no proporcionado")
}

class AppViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel: ViewModel = when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(container)
            modelClass.isAssignableFrom(PackagesViewModel::class.java) -> PackagesViewModel(container)
            modelClass.isAssignableFrom(QueueViewModel::class.java) -> QueueViewModel(container)
            modelClass.isAssignableFrom(ConsoleViewModel::class.java) -> ConsoleViewModel(container)
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(container)
            modelClass.isAssignableFrom(LogsViewModel::class.java) -> LogsViewModel(container)
            modelClass.isAssignableFrom(InstalledViewModel::class.java) ->
                InstalledViewModel(container)
            else -> error("ViewModel no soportado: ${modelClass.name}")
        }
        @Suppress("UNCHECKED_CAST")
        return viewModel as T
    }
}

@Composable
inline fun <reified T : ViewModel> appViewModel(): T {
    val container = LocalAppContainer.current
    val factory = remember(container) { AppViewModelFactory(container) }
    return viewModel<T>(factory = factory)
}

@Composable
fun CollectUiMessages(events: Flow<String>) {
    val snackbar = LocalSnackbarHostState.current
    LaunchedEffect(events) {
        events.collect { message ->
            snackbar.showSnackbar(message)
        }
    }
}
