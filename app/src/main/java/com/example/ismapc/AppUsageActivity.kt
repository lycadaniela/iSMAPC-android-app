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
    val tempAppUsage = listOf(
        "com.facebook.katana" to 120L, // 2 hours
        "com.instagram.android" to 90L,  // 1.5 hours
        "com.twitter.android" to 60L,    // 1 hour
        "com.whatsapp" to 45L,           // 45 minutes
        "com.google.android.youtube" to 30L, // 30 minutes
        "com.spotify.music" to 20L,      // 20 minutes
        "com.snapchat.android" to 15L,   // 15 minutes
        "com.tiktok.android" to 10L      // 10 minutes
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
            items(tempAppUsage.sortedByDescending { it.second }) { (packageName, usageMinutes) ->
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
                            text = packageName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatUsageTime(usageMinutes),
                            style = MaterialTheme.typography.bodyMedium
                        )
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