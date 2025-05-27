package com.example.ismapc

import android.os.Bundle
import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import com.example.ismapc.ui.theme.ISMAPCTheme
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import java.util.concurrent.TimeUnit
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import android.content.Context
import java.io.File
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.background
import com.google.firebase.firestore.FieldValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.example.ismapc.ContentFilteringManager
import com.google.firebase.Timestamp
import android.widget.Toast
import com.example.ismapc.ContentFilteringResult
import com.example.ismapc.ContentFilteringHelper
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import coil.compose.AsyncImage
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import com.example.ismapc.EditChildProfileActivity

class ChildDetailsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get the child ID from the intent
        val childId = intent.getStringExtra("childId")
        val childName = intent.getStringExtra("childName")
        
        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChildDetailsScreen(
                        childId = childId ?: "",
                        childName = childName ?: "Child Details"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildDetailsScreen(childId: String, childName: String) {
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()
    var childPhotoUrl by remember { mutableStateOf<String?>(null) }
    var screenTime by remember { mutableStateOf<Long>(0) }
    var childEmail by remember { mutableStateOf<String?>(null) }
    var showEmailDropdown by remember { mutableStateOf(false) }
    var showAppsList by remember { mutableStateOf(false) }
    var showLocationMap by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    
    // Calculate screen time percentage and format time
    val screenTimePercentage = remember(screenTime) {
        val totalDayMilliseconds = 24 * 60 * 60 * 1000L // 24 hours in milliseconds
        ((screenTime.toFloat() / totalDayMilliseconds) * 100).coerceIn(0f, 100f)
    }

    val screenTimeUsed = remember(screenTime) {
        val hours = TimeUnit.MILLISECONDS.toHours(screenTime)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(screenTime) % 60
        "$hours hours $minutes minutes"
    }

    val screenTimeLimit = remember {
        "24 hours" // Default limit
    }
    
    // Fetch child photo, email and screen time
    LaunchedEffect(childId) {
        if (childId.isBlank()) {
            Log.e("ChildDetails", "Child ID is blank")
            return@LaunchedEffect
        }

        Log.d("ChildDetails", "Fetching data for child ID: $childId")
        
        // Fetch child photo and email
        firestore.collection("users")
            .document("child")
            .collection("profile")
            .document(childId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    childPhotoUrl = document.getString("photoUrl")
                    childEmail = document.getString("email")
                    Log.d("ChildDetails", "Successfully fetched data - Email: $childEmail, Photo URL: $childPhotoUrl")
                } else {
                    Log.e("ChildDetails", "No document found for child ID: $childId")
                }
            }
            .addOnFailureListener { e ->
                Log.e("ChildDetails", "Error fetching child data", e)
            }
    }

    // Set up real-time listener for screen time updates
    DisposableEffect(childId) {
        val screenTimeListener = firestore.collection("screenTime")
            .document(childId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChildDetails", "Error listening to screen time updates", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    screenTime = snapshot.getLong("screenTime") ?: 0L
                    Log.d("ChildDetails", "Screen time updated: $screenTime")
                }
            }

        onDispose {
            screenTimeListener.remove()
        }
    }

    // Fetch installed apps when Apps button is clicked
    LaunchedEffect(showAppsList) {
        if (showAppsList) {
            firestore.collection("installedApps")
                .document(childId)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        @Suppress("UNCHECKED_CAST")
                        installedApps = document.get("apps") as? List<Map<String, Any>> ?: emptyList()
                        }
                }
                    }
                }

    // Fetch location when Location button is clicked
    LaunchedEffect(showLocationMap) {
        if (showLocationMap) {
            firestore.collection("locations")
                .document(childId)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val latitude = document.getDouble("latitude")
                        val longitude = document.getDouble("longitude")
                        if (latitude != null && longitude != null) {
                            currentLocation = GeoPoint(latitude, longitude)
        }
    }
                }
        }
    }

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
                            onClick = { (context as? Activity)?.finish() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                actions = {
                    Card(
                        modifier = Modifier
                            .width(48.dp)
                            .height(48.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        IconButton(
                            onClick = {
                                val intent = Intent().apply {
                                    setClass(context, EditChildProfileActivity::class.java)
                                    putExtra("childId", childId)
                                    putExtra("childName", childName)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                modifier = Modifier.size(24.dp),
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
            // Profile Section
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
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Screen Time Percentage Text
                    Text(
                        text = "Spent ${String.format("%.1f", screenTimePercentage)}% of the day on the phone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Profile Picture with Progress Bar
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Profile Picture
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    width = 2.dp,
                                    color = Color(0xFFE0852D),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (childPhotoUrl != null) {
                                AsyncImage(
                                    model = childPhotoUrl,
                                    contentDescription = "Profile Picture",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Profile Picture",
                                    tint = Color(0xFFE0852D),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Child Name
                    Text(
                        text = childName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFE0852D)
                    )

                    // Child Email
                    Text(
                        text = childEmail ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black
                    )
                }
            }

            // Screen Time Group
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Screen Time",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = screenTimePercentage / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = Color(0xFFE0852D),
                        trackColor = Color(0xFFE0852D).copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$screenTimeUsed of $screenTimeLimit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black
                    )
                }
            }

            // Add a divider
            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline
            )

            // Action Buttons Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // First Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Screen Time Limits
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Timer,
                        title = "Screen Time",
                        onClick = {
                            val intent = Intent(context, ScreenTimeLimitActivity::class.java)
                            intent.putExtra("childId", childId)
                            intent.putExtra("childName", childName)
                            context.startActivity(intent)
                        }
                    )

                    // App Usage
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Apps,
                        title = "App Usage",
                        onClick = {
                            try {
                                val intent = Intent(context, AppUsageActivity::class.java).apply {
                                    putExtra("childId", childId)
                                    putExtra("childName", childName)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error opening app usage: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                // Second Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Location
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.LocationOn,
                        title = "Location",
                        onClick = {
                            val intent = Intent(context, LocationMapActivity::class.java)
                            intent.putExtra("childId", childId)
                            intent.putExtra("childName", childName)
                            context.startActivity(intent)
                        }
                    )

                    // Installed Apps
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.PhoneAndroid,
                        title = "Installed Apps",
                        onClick = {
                            val intent = Intent(context, InstalledAppsActivity::class.java)
                            intent.putExtra("childId", childId)
                            intent.putExtra("childName", childName)
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ActionButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Keep only the necessary sealed classes and remove unused ones
sealed class ScreenTimeState {
    object Loading : ScreenTimeState()
    data class Success(val screenTime: Long) : ScreenTimeState()
    data class Error(val message: String) : ScreenTimeState()
} 