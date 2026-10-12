package com.hearthelp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier

private enum class Tab(val label: String) { Home("Home"), Contacts("Contacts"), Settings("Settings"), Log("Log") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var tab by rememberSaveable { mutableStateOf(Tab.Home) }
                Scaffold(bottomBar = {
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(selected = tab == t, onClick = { tab = t },
                                icon = {}, label = { Text(t.label) })
                        }
                    }
                }) { padding ->
                    val m = Modifier.padding(padding)
                    when (tab) {
                        Tab.Home -> HomeScreen(m)
                        Tab.Contacts -> ContactsScreen(m)
                        Tab.Settings -> SettingsScreen(m)
                        Tab.Log -> LogScreen(m)
                    }
                }
            }
        }
    }
}
