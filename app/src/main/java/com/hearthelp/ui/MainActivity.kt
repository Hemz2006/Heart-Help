package com.hearthelp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hearthelp.ui.theme.HeartHelpTheme
import com.hearthelp.ui.theme.Hh
import com.hearthelp.ui.theme.HhType

internal enum class Tab(val label: String) {
    Home("Home"), History("History"), Contacts("Contacts"), Settings("Settings"), Log("Log")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HeartHelpTheme {
                val holder = rememberSaveableStateHolder()
                var tab by rememberSaveable { mutableStateOf(Tab.Home) }
                Scaffold(containerColor = Hh.Night, bottomBar = { TabBar(tab) { tab = it } }) { padding ->
                    val m = Modifier.padding(padding)
                    holder.SaveableStateProvider(tab.name) {
                        when (tab) {
                            Tab.Home -> HomeScreen(m) { tab = it }
                            Tab.History -> HistoryScreen(m)
                            Tab.Contacts -> ContactsScreen(m)
                            Tab.Settings -> SettingsScreen(m)
                            Tab.Log -> LogScreen(m)
                        }
                    }
                }
            }
        }
    }
}

/** Text-only tabs; a small Pulse dot marks where you are. */
@Composable
private fun TabBar(current: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Hh.Night).navigationBarsPadding().height(64.dp).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            val selected = t == current
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(t) }),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text(t.label, style = HhType.Small, color = if (selected) Hh.Moon else Hh.Mist)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(4.dp).clip(CircleShape).background(if (selected) Hh.Pulse else Color.Transparent))
            }
        }
    }
}
