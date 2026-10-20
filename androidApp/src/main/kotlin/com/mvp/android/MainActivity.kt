package com.mvp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mvp.android.ui.auth.LoginScreen
import com.mvp.android.ui.auth.SessionUiState
import com.mvp.android.ui.auth.SessionViewModel
import com.mvp.android.ui.settings.SettingsScreen
import com.mvp.android.ui.theme.MvpTheme
import com.mvp.android.ui.todo.TodoScreen
import org.koin.androidx.compose.koinViewModel

private enum class Tab(val label: String) { Todo("Tâches"), Settings("Réglages") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MvpTheme {
                val session by koinViewModel<SessionViewModel>().state.collectAsStateWithLifecycle()
                // Sans session, seul l'écran de connexion est accessible.
                when (session) {
                    SessionUiState.Loading -> Box(Modifier.fillMaxSize())
                    SessionUiState.LoggedOut -> LoginScreen()
                    is SessionUiState.LoggedIn -> AppTabs()
                }
            }
        }
    }
}

@Composable
private fun AppTabs() {
    // Revient sur « Tâches » à chaque nouvelle connexion.
    var tab by rememberSaveable { mutableStateOf(Tab.Todo) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {},
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        // Les écrans ont leur propre Scaffold : on leur évite de réappliquer les insets déjà gérés ici.
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when (tab) {
                Tab.Todo -> TodoScreen()
                Tab.Settings -> SettingsScreen()
            }
        }
    }
}
