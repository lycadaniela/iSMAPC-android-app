package com.example.ismapc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ismapc.ui.theme.ISMAPCTheme
import java.util.concurrent.TimeUnit

class AppUsageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val childId = intent.getStringExtra("childId") ?: ""
        val childName = intent.getStringExtra("childName") ?: "App Usage"
        
        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppUsageScreen(childId = childId, childName = childName)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUsageScreen(childId: String, childName: String) {
    val context = LocalContext.current
    
    // Temporary data for testing
    data class AppUsage(
        val name: String,
        val dailyMinutes: Long,
        val weeklyMinutes: Long
    )

    val tempAppUsage = listOf(
        AppUsage("Facebook", 120L, 840L),      // 2h daily, 14h weekly
        AppUsage("Instagram", 90L, 630L),      // 1.5h daily, 10.5h weekly
        AppUsage("Twitter", 60L, 420L),        // 1h daily, 7h weekly
        AppUsage("WhatsApp", 45L, 315L),       // 45m daily, 5.25h weekly
        AppUsage("YouTube", 30L, 210L),        // 30m daily, 3.5h weekly
        AppUsage("Spotify", 20L, 140L),        // 20m daily, 2.33h weekly
        AppUsage("Snapchat", 15L, 105L),       // 15m daily, 1.75h weekly
        AppUsage("TikTok", 10L, 70L)           // 10m daily, 1.17h weekly
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$childName's App Usage") },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tempAppUsage.sortedByDescending { it.dailyMinutes }) { appUsage ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = appUsage.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Today",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatUsageTime(appUsage.dailyMinutes),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "This Week",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatUsageTime(appUsage.weeklyMinutes),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatUsageTime(minutes: Long): String {
    val hours = TimeUnit.MINUTES.toHours(minutes)
    val remainingMinutes = minutes % 60
    return when {
        hours > 0 -> "$hours hours $remainingMinutes minutes"
        else -> "$remainingMinutes minutes"
    }
} 