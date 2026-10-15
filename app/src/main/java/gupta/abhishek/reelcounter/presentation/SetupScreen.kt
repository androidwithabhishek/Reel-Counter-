package gupta.abhishek.reelcounter.presentation

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SetupScreen(
    modifier: Modifier = Modifier,
    viewModel: ReelViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val timeFormat = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("1. Tap the button\n2. Find \"Reel Counter\"\n3. Turn it ON")

        Button(
            modifier = Modifier.padding(top = 16.dp),
            onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        ) {
            Text("Open Accessibility Settings")
        }

        // ---- counters ----
        Text(
            text = "Today: ${state.todayCount}",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text("Total saved: ${state.totalCount}", style = MaterialTheme.typography.bodyMedium)

        // shown only after "Start new day" was used, so you know the clock is shifted
        if (state.daysSkipped > 0) {
            Text(
                "Test mode: ${state.daysSkipped} day(s) skipped",
                style = MaterialTheme.typography.bodySmall
            )
        }

        // ---- testing tools ----
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { viewModel.startNewDay() }) { Text("Start new day") }
            OutlinedButton(onClick = { viewModel.clearAll() }) { Text("Delete all") }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // ---- every saved reel ----
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.reels, key = { it.id }) { reel ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("#${reel.id}  ${reel.creator ?: "unknown"}")
                        Text(reel.platform, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = reel.caption ?: "(no caption)",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val seen = if (reel.timesSeen > 1) " • seen ${reel.timesSeen}x" else ""
                    Text(
                        text = "${reel.app} • ${timeFormat.format(Date(reel.watchedAt))}$seen",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}