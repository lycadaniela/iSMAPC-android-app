package com.example.ismapc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ismapc.ui.theme.ISMAPCTheme

class FAQActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FAQScreenContent(
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FAQScreenContent(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    Card(
                        modifier = Modifier
                            .width(48.dp)
                            .height(48.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
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
                        color = Color(0xFF333333)
                    )
                }
            }

            // FAQ Items
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Child Device Setup
                    FAQItemContent(
                        question = "How do I set up my child's device?",
                        answer = "1. Install ISMAPC on your child's device\n2. Open the app and log in with your child's account\n3. Grant the following permissions when prompted:\n   • Usage Access (for screen time monitoring)\n   • Location (for location tracking)\n   • Overlay Permission (for app locking)\n   • Notification Access (for alerts)\n   • Battery Optimization (for background monitoring)\n4. After granting all permissions, close the app completely\n5. Reopen the app to ensure all permissions are properly initialized\n6. The app will then automatically start monitoring the device"
                    )

                    Divider(modifier = Modifier.padding(vertical = 16.dp))

                    // Account Setup
                    FAQItemContent(
                        question = "How do I add a child to my account?",
                        answer = "1. Go to the 'Add Child' section in the app\n2. Provide your child's name and email\n3. Create a password for your child's account\n4. Optionally add a profile picture\n5. Complete the setup process\n6. Sign out of your parent account\n7. Sign back in to your parent account to access all features\n8. For the child's device, you (the parent) will need to sign in using the child's credentials you just created"
                    )

                    Divider(modifier = Modifier.padding(vertical = 16.dp))

                    // Child Details Access
                    FAQItemContent(
                        question = "How do I access my child's details?",
                        answer = "1. Go to the main screen where your children are listed\n2. Tap on your child's card\n3. This will open the child details page where you can see:\n   • Screen time usage\n   • App usage statistics\n   • Location information\n   • Device status\n   • Allow logout toggle (controls whether the child can log out of the app)\n   • And more detailed information"
                    )

                    Divider(modifier = Modifier.padding(vertical = 16.dp))

                    // Screen Time Monitoring
                    FAQItemContent(
                        question = "How does screen time monitoring work?",
                        answer = "1. ISMAPC automatically tracks your child's device usage\n2. View daily device usage reports in the dashboard\n3. Access separate daily and weekly reports for:\n   • Overall device usage\n   • Individual app usage\n4. See detailed breakdowns of app usage and duration"
                    )

                    Divider(modifier = Modifier.padding(vertical = 16.dp))

                    // Location Tracking
                    FAQItemContent(
                        question = "How accurate is the location tracking?",
                        answer = "1. ISMAPC uses multiple location services:\n   • GPS for high accuracy\n   • Network location as backup\n2. Location accuracy depends on:\n   • GPS signal strength\n   • Network connectivity\n   • Device settings\n3. Real-time updates are provided in the dashboard"
                    )

                    Divider(modifier = Modifier.padding(vertical = 16.dp))

                    // Device Locking
                    FAQItemContent(
                        question = "How do I lock or unlock my child's device?",
                        answer = "1. Go to the main screen where your children are listed\n2. Find your child's card\n3. Tap the lock/unlock icon on the right side of the card\n4. The device will be locked/unlocked immediately\nNote: When locked, your child won't be able to use any apps except for emergency calls."
                    )

                    Divider(modifier = Modifier.padding(vertical = 16.dp))

                    // App Locking
                    FAQItemContent(
                        question = "How do I lock or unlock specific apps?",
                        answer = "1. Go to your child's details page\n2. Navigate to the 'Installed Apps' section\n3. Find the app you want to lock/unlock\n4. Tap the lock icon next to the app\n5. The app will be locked/unlocked immediately\nNote: When an app is locked:\n   • Your child won't be able to open it\n   • They'll see a lock screen when trying to access it\n   • You can unlock it anytime from your parent dashboard"
                    )

                    Divider(modifier = Modifier.padding(vertical = 16.dp))

                    // Delete Child Account
                    FAQItemContent(
                        question = "How do I delete a child's account?",
                        answer = "1. Go to the main screen where your children are listed\n2. Tap the trash icon in the top right to enter delete mode\n3. Tap the delete icon on the child's card you want to remove\n4. Confirm the deletion\nNote: This will permanently delete the child's account and all associated data. This action cannot be undone."
                    )

                    Divider(modifier = Modifier.padding(vertical = 16.dp))

                    // Privacy
                    FAQItemContent(
                        question = "How is my child's privacy protected?",
                        answer = "1. All data is encrypted and stored securely\n2. Only you, as the parent, can access the monitoring data\n3. We comply with all relevant privacy laws\n4. Regular security audits are performed\n5. Data is never shared with third parties"
                    )

                    Divider(modifier = Modifier.padding(vertical = 16.dp))

                    // Technical Support
                    FAQItemContent(
                        question = "What if I need technical support?",
                        answer = "1. Access the 'Help & Support' section in the app\n2. Choose from the following options:\n   • Email support\n   • Knowledge base\n   • Troubleshooting guides\n3. Our support team typically responds within 24 hours\n4. For urgent issues, use the emergency contact option"
                    )
                }
            }
        }
    }
}

@Composable
private fun FAQItemContent(
    question: String,
    answer: String
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = question,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFE0852D),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = Color(0xFFE0852D),
                modifier = Modifier.size(24.dp)
            )
        }
        
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = answer,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF666666)
            )
        }
    }
} 