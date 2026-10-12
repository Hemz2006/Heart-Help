package com.hearthelp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearthelp.app
import java.text.DateFormat
import java.util.Date

@Composable
fun LogScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    val entries by ctx.app.log.recent.collectAsStateWithLifecycle(emptyList())
    val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Event log", style = MaterialTheme.typography.titleLarge) }
        if (entries.isEmpty()) item { Text("Nothing yet.") }
        items(entries, key = { it.id }) { e ->
            Column {
                Text("${fmt.format(Date(e.time))} · ${e.type}", style = MaterialTheme.typography.labelMedium)
                Text(e.detail)
            }
        }
    }
}
