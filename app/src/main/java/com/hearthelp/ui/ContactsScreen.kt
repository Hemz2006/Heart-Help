package com.hearthelp.ui

import android.content.ActivityNotFoundException
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearthelp.app
import com.hearthelp.domain.EmergencyContact
import com.hearthelp.domain.EmergencyContacts
import com.hearthelp.ui.theme.*
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

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp)) {
        item {
            Text("Emergency contacts", style = HhType.Title, color = Hh.Moon)
            Text("Everyone here gets a text with your location. Calls go down the list until someone " +
                "answers or replies OK.", style = HhType.Small, color = Hh.Mist)
            Spacer(Modifier.height(20.dp))
        }
        if (contacts.isEmpty()) item {
            Text("No one added yet. Add up to ${EmergencyContacts.MAX} people you trust.",
                style = HhType.Body, color = Hh.Moon, modifier = Modifier.padding(bottom = 20.dp))
        }
        itemsIndexed(contacts) { i, c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = HhType.Heading, color = if (i == 0) Hh.Pulse else Hh.Mist,
                    modifier = Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = HhType.Body, color = Hh.Moon)
                    Text(if (i == 0) "${c.number}, called first" else c.number, style = HhType.Small, color = Hh.Mist)
                }
                if (i > 0) LinkButton("Move up", { save(EmergencyContacts.moveUp(contacts, i)) })
                LinkButton("Remove", { save(EmergencyContacts.remove(contacts, i)) })
            }
        }
        if (contacts.size < EmergencyContacts.MAX) item {
            Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("Choose from your contacts", {
                    try { picker.launch(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)) }
                    catch (e: ActivityNotFoundException) {
                        error = "This phone has no contact picker. Enter the number below instead."
                    }
                })
                Text("Or enter one yourself", style = HhType.Small, color = Hh.Mist, modifier = Modifier.padding(top = 8.dp))
                TextField(name, { name = it }, label = { Text("Name") }, singleLine = true,
                    colors = hhFieldColors(), shape = FieldShape, modifier = Modifier.fillMaxWidth())
                TextField(number, { number = it }, label = { Text("Phone number") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = hhFieldColors(), shape = FieldShape, modifier = Modifier.fillMaxWidth())
                QuietButton("Add contact", { tryAdd(EmergencyContact(name, number)) },
                    enabled = number.isNotBlank(), modifier = Modifier.fillMaxWidth())
                Text("${contacts.size} of ${EmergencyContacts.MAX} added", style = HhType.Small, color = Hh.Mist)
            }
        }
        error?.let { item { Text(it, style = HhType.Body, color = Hh.Pulse, modifier = Modifier.padding(top = 12.dp)) } }
    }
}

private fun readPicked(ctx: Context, uri: Uri): EmergencyContact? =
    ctx.contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)?.use {
        if (it.moveToFirst()) EmergencyContact(it.getString(0) ?: "", it.getString(1) ?: "") else null
    }
