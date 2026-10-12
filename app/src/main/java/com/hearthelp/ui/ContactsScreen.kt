package com.hearthelp.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearthelp.app
import com.hearthelp.domain.EmergencyContact
import com.hearthelp.domain.EmergencyContacts
import kotlinx.coroutines.launch

@Composable
fun ContactsScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    val prefs = ctx.app.prefs
    val scope = rememberCoroutineScope()
    val contacts by prefs.contacts.collectAsStateWithLifecycle(emptyList())
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun save(list: List<EmergencyContact>) = scope.launch { prefs.saveContacts(list) }
    fun tryAdd(c: EmergencyContact) {
        runCatching { EmergencyContacts.add(contacts, c) }
            .onSuccess { save(it); name = ""; number = ""; error = null }
            .onFailure { error = it.message }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.data?.let { uri -> readPicked(ctx, uri)?.let(::tryAdd) }
    }

    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Emergency contacts (${contacts.size}/${EmergencyContacts.MAX})", style = MaterialTheme.typography.titleLarge)
            Text("All of them get the SMS. Calls go in this order until someone answers or replies OK.")
        }
        itemsIndexed(contacts) { i, c ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("${i + 1}. ${c.name}" + if (i == 0) "  (called first)" else "")
                        Text(c.number, style = MaterialTheme.typography.bodySmall)
                    }
                    if (i > 0) TextButton(onClick = { save(EmergencyContacts.moveUp(contacts, i)) }) { Text("Up") }
                    TextButton(onClick = { save(EmergencyContacts.remove(contacts, i)) }) { Text("Remove") }
                }
            }
        }
        if (contacts.size < EmergencyContacts.MAX) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { picker.launch(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)) },
                    modifier = Modifier.fillMaxWidth()) { Text("Pick from phone contacts") }
                Text("…or type one in:")
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(number, { number = it }, label = { Text("Phone number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                Button(onClick = { tryAdd(EmergencyContact(name, number)) }) { Text("Add") }
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }
}

private fun readPicked(ctx: Context, uri: Uri): EmergencyContact? =
    ctx.contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)?.use {
        if (it.moveToFirst()) EmergencyContact(it.getString(0) ?: "", it.getString(1) ?: "") else null
    }
