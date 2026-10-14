package com.alper.alpy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class AlertActivity : ComponentActivity() {
    private var info by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        info = intent
        setContent { Themed { info?.let { Screen(it) } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        info = intent
    }

    @Composable
    private fun Screen(i: Intent) {
        val kind = i.getStringExtra("kind") ?: return
        val id = i.getIntExtra("id", 0)
        fun go(action: String, min: Int = Store.data.value.snoozeMin) {
            act(this, action, kind, id, min)
            finish()
        }
        val big = Modifier.fillMaxWidth().height(64.dp)
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.systemBarsPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(i.getStringExtra("title").orEmpty(), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                i.getStringExtra("text")?.takeIf { it.isNotBlank() }?.let { Text(it, textAlign = TextAlign.Center) }
                Button({ go("done") }, big) { Text("Done") }
                Text("Snooze (minutes)")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(5, 10, 15, 30, 60).forEach {
                        OutlinedButton({ go("snooze", it) }, Modifier.weight(1f)) { Text("$it") }
                    }
                }
                var custom by remember { mutableIntStateOf(20) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Minutes", custom, Modifier.weight(1f)) { custom = it }
                    Button({ go("snooze", custom.coerceAtLeast(1)) }) { Text("Snooze") }
                }
            }
        }
    }
}
