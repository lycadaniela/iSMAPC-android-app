package com.example.ismapc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.example.ismapc.ui.theme.ISMAPCTheme
import java.util.concurrent.TimeUnit

class ScreenTimeLimitActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val childId = intent.getStringExtra("childId") ?: ""
        val childName = intent.getStringExtra("childName") ?: "Screen Time Limits"
        
        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ScreenTimeLimitScreen(childId = childId, childName = childName)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTimeLimitScreen(childId: String, childName: String) {
    val context = LocalContext.current
    
    // Temporary data for testing
    val tempAppLimits = listOf(
        "Facebook" to 60L, // 1 hour
        "Instagram" to 45L,  // 45 minutes
        "Twitter" to 30L,    // 30 minutes
        "WhatsApp" to 120L,           // 2 hours
        "YouTube" to 60L, // 1 hour
        "Spotify" to 180L,      // 3 hours
        "Snapchat" to 30L,   // 30 minutes
        "TikTok" to 45L      // 45 minutes
    )

    // Temporary device lock times
    val tempLockTimes = listOf(
        "Weekdays" to "9:00 PM",
        "Weekends" to "10:00 PM"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(
                        onClick = { 
                            (context as? ComponentActivity)?.finish()
                            android.util.Log.d("ScreenTime", "Back button pressed")
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back to previous screen",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$childName's Screen Time",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFE0852D),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Manage app usage and device lock times",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF424242),
                    textAlign = TextAlign.Center
                )
            }

            // Device Lock Times Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Device Lock Times",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFE0852D),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    tempLockTimes.forEach { (day, time) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = day,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = time,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFE0852D)
                            )
                        }
                        if (day != tempLockTimes.last().first) {
                            Divider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }

            // App Time Limits Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "App Time Limits",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFFE0852D),
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { /* TODO: Add new app limit */ }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add App Limit",
                                tint = Color(0xFFE0852D)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    tempAppLimits.forEach { (app, minutes) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = app,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${minutes / 60}h ${minutes % 60}m",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFFE0852D)
                                )
                                IconButton(onClick = { /* TODO: Edit app limit */ }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Limit",
                                        tint = Color(0xFFE0852D)
                                    )
                                }
                            }
                        }
                        if (app != tempAppLimits.last().first) {
                            Divider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
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