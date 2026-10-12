package com.hearthelp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearthelp.app
import com.hearthelp.ui.theme.Hh
import com.hearthelp.ui.theme.HhType
import java.text.DateFormat
import java.util.Date

@Composable
fun LogScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    val entries by ctx.app.log.recent.collectAsStateWithLifecycle(emptyList())
    val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp)) {
        item {
            Text("Activity", style = HhType.Title, color = Hh.Moon)
            Text("Everything HeartHelp has done, newest first.", style = HhType.Small, color = Hh.Mist)
            Spacer(Modifier.height(16.dp))
        }
        if (entries.isEmpty()) item {
            Text("Nothing yet. Alerts, texts, calls and signal changes will appear here.",
                style = HhType.Body, color = Hh.Moon)
        }
        items(entries, key = { it.id }) { e ->
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(e.type, style = HhType.Label, color = Hh.Moon)
                if (e.detail.isNotBlank()) Text(e.detail, style = HhType.Body, color = Hh.Moon.copy(alpha = 0.85f))
                Text(fmt.format(Date(e.time)), style = HhType.Small, color = Hh.Mist)
            }
        }
    }
}
