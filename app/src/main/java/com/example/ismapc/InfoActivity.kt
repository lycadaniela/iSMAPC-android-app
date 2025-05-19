package com.example.ismapc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ismapc.ui.theme.ISMAPCTheme
import androidx.compose.foundation.clickable

class InfoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    InstructionsScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionsScreen() {
    val context = LocalContext.current
    var expandedSection by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Back button row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(
                onClick = { (context as? ComponentActivity)?.finish() }
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Title
        Text(
            text = "Instructions",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Instruction Sections
        InstructionSection(
            title = "Adding a Child Account",
            content = "To add a child, tap the plus (+) icon, then fill out the form that appears. You can either enter your child's email or sign in using their Google account.",
            isExpanded = expandedSection == 0,
            onClick = { expandedSection = if (expandedSection == 0) null else 0 }
        )

        InstructionSection(
            title = "Deleting a Child Account",
            content = "On your dashboard, tap the trash can icon. This will switch the lock icon beside your child's name to a delete button.\n\n⚠️ Deleting a child account will permanently erase all their data.",
            isExpanded = expandedSection == 1,
            onClick = { expandedSection = if (expandedSection == 1) null else 1 }
        )

        InstructionSection(
            title = "Locking/Unlocking Your Child's Device",
            content = "To lock or unlock a child's device, tap the button next to their name.\n\nA red button means the device is locked.\n\nTap again to unlock.",
            isExpanded = expandedSection == 2,
            onClick = { expandedSection = if (expandedSection == 2) null else 2 }
        )

        InstructionSection(
            title = "Accessing Your Child's Data",
            content = "To view your child's data, simply tap on their name. This will open a page showing their activity and information.",
            isExpanded = expandedSection == 3,
            onClick = { expandedSection = if (expandedSection == 3) null else 3 }
        )

        InstructionSection(
            title = "Locking/Unlocking Specific Apps",
            content = "To manage apps on your child's device:\n\n1. Tap the Apps button.\n\n2. Inside, you'll see a list of apps.\n\n3. Tap the toggle next to the app you want to lock or unlock.\n\n✅ If the toggle turns red, the app is locked.",
            isExpanded = expandedSection == 4,
            onClick = { expandedSection = if (expandedSection == 4) null else 4 }
        )

        InstructionSection(
            title = "Accessing Your Child's Location",
            content = "To find your child's location:\n\n1. Tap the Location button.\n\n2. A map will show their current position with a pin.\n\n3. Tap the Google Maps icon at the bottom right to open their location in your phone's Maps app for a more accurate view.",
            isExpanded = expandedSection == 5,
            onClick = { expandedSection = if (expandedSection == 5) null else 5 }
        )

        InstructionSection(
            title = "Content Filtering",
            content = "Currently not available.",
            isExpanded = expandedSection == 6,
            onClick = { expandedSection = if (expandedSection == 6) null else 6 }
        )

        InstructionSection(
            title = "Accessing Your Child's Device",
            content = "To start monitoring your child's device:\n\n1. Install the \"ismapc\" app on your child's device.\n\n2. Log in using the child account you created.\n\n3. Grant all requested permissions so the app can collect the necessary data.\n\n⚠️ Permissions are required for features like location tracking, app monitoring, and device locking.",
            isExpanded = expandedSection == 7,
            onClick = { expandedSection = if (expandedSection == 7) null else 7 }
        )
    }
}

@Composable
fun InstructionSection(
    title: String,
    content: String,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Divider(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Justify
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
} 