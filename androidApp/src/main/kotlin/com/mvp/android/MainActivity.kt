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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mvp.android.ui.settings.SettingsScreen
import com.mvp.android.ui.theme.MvpTheme
import com.mvp.android.ui.todo.TodoScreen

private enum class Tab(val label: String) { Todo("Tâches"), Settings("Réglages") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MvpTheme {
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
        }
    }
}
