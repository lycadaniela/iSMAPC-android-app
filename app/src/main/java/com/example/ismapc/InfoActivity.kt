package com.example.ismapc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ismapc.ui.theme.ISMAPCTheme
import androidx.compose.ui.graphics.Color

class InfoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FAQScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FAQScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
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
            .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            // Header Card
            Card(
            modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
        Text(
                        text = "Frequently Asked Questions",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFE0852D)
                    )
                    Text(
                        text = "Find answers to common questions about ISMAPC",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD6D7D3)
                    )
                }
            }

            // FAQ Categories
            FAQCategory(
                title = "Getting Started",
                icon = Icons.Default.PlayArrow,
                faqs = listOf(
                    FAQItem(
                        "How do I add a child to my account?",
                        "To add a child, go to the dashboard and tap the 'Add Child' button. Follow the setup process to create a profile for your child and link their device."
                    ),
                    FAQItem(
                        "How do I monitor my child's device?",
                        "Once your child's device is linked, you can monitor their activity through the dashboard. You'll see their screen time, app usage, and location in real-time."
                    )
                )
            )

            FAQCategory(
                title = "Screen Time Management",
                icon = Icons.Default.Timer,
                faqs = listOf(
                    FAQItem(
                        "How do I set screen time limits?",
                        "Navigate to your child's profile and tap 'Screen Time'. Here you can set daily limits and schedule device-free time."
                    ),
                    FAQItem(
                        "Can I lock my child's device remotely?",
                        "Yes, you can lock your child's device at any time from the dashboard. Tap the 'Lock Device' button in your child's profile."
                    )
                )
            )

            FAQCategory(
                title = "Location Tracking",
                icon = Icons.Default.LocationOn,
                faqs = listOf(
                    FAQItem(
                        "How accurate is the location tracking?",
                        "Our location tracking uses GPS and network signals to provide accurate location data. The accuracy may vary depending on the device's signal strength."
                    ),
                    FAQItem(
                        "Can I set up location alerts?",
                        "Yes, you can set up geofencing alerts in the location settings. You'll receive notifications when your child enters or leaves designated areas."
                    )
                )
            )

            FAQCategory(
                title = "Troubleshooting",
                icon = Icons.Default.Build,
                faqs = listOf(
                    FAQItem(
                        "What if the app isn't working properly?",
                        "Try restarting the app and checking your internet connection. If issues persist, go to Settings > Help & Support to contact our support team."
                    ),
                    FAQItem(
                        "How do I update the app?",
                        "The app will automatically check for updates. You can also manually check for updates in the Google Play Store."
                    )
                )
            )

            // Contact Support Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Need More Help?",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFE0852D)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Contact our support team for assistance",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD6D7D3)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { /* TODO: Implement contact support */ },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE0852D)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Contact Support")
                    }
                }
            }
        }
    }
}

@Composable
fun FAQCategory(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    faqs: List<FAQItem>
) {
    val expandedItems = remember { mutableStateOf(mutableSetOf<Int>()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
                modifier = Modifier
                    .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFE0852D),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFE0852D)
                )
            }

            faqs.forEachIndexed { index, faq ->
                Column {
                    Row(
                    modifier = Modifier
                        .fillMaxWidth()
                            .clickable {
                                if (expandedItems.value.contains(index)) {
                                    expandedItems.value.remove(index)
                                } else {
                                    expandedItems.value.add(index)
                                }
                            }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Text(
                            text = faq.question,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (expandedItems.value.contains(index)) {
                                Icons.Default.ExpandLess
                            } else {
                                Icons.Default.ExpandMore
                            },
                            contentDescription = if (expandedItems.value.contains(index)) "Collapse" else "Expand",
                            tint = Color(0xFFE0852D)
                        )
                    }

                    if (expandedItems.value.contains(index)) {
                    Text(
                            text = faq.answer,
                        style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFD6D7D3),
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 16.dp
                            )
                        )
                    }

                    if (index < faqs.size - 1) {
                        Divider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
} 

data class FAQItem(
    val question: String,
    val answer: String
) 