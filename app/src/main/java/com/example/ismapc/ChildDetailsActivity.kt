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
import com.example.ismapc.ProfilePictureManager

class ChildDetailsActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var profilePictureManager: ProfilePictureManager
    private var childId: String? = null
    private var childName: String? = null
    private var childEmail: String? = null
    private var childProfilePicturePath: String? = null
    private var profileBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        profilePictureManager = ProfilePictureManager(this)
        
        // Get child ID from intent
        childId = intent.getStringExtra("childId")
        childName = intent.getStringExtra("childName")
        childEmail = intent.getStringExtra("childEmail")
        childProfilePicturePath = intent.getStringExtra("childProfilePicturePath")

        // Load profile picture using child ID
        childId?.let { id ->
            try {
                profileBitmap = profilePictureManager.getProfilePictureBitmap(id)
                Log.d("ChildDetails", "Loaded profile picture for child ID: $id")
            } catch (e: Exception) {
                Log.e("ChildDetails", "Error loading profile picture", e)
            }
        }

        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChildDetailsScreen(
                        onBack = { finish() },
                        childId = childId ?: "",
                        childName = childName ?: "",
                        childEmail = childEmail ?: "",
                        profileBitmap = profileBitmap,
                        onEditProfile = {
                            val intent = Intent(this, EditChildProfileActivity::class.java).apply {
                                putExtra("childId", childId)
                                putExtra("childName", childName)
                                putExtra("childEmail", childEmail)
                                putExtra("childProfilePicturePath", childProfilePicturePath)
                            }
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildDetailsScreen(
    onBack: () -> Unit,
    childId: String,
    childName: String,
    childEmail: String,
    profileBitmap: Bitmap?,
    onEditProfile: () -> Unit
) {
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()
    var screenTime by remember { mutableStateOf<Long>(0) }
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
    
    // Fetch screen time
    LaunchedEffect(Unit) {
        if (childId.isBlank()) {
            Log.e("ChildDetails", "Child ID is blank")
            return@LaunchedEffect
        }

        firestore.collection("screenTime")
            .document(childId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    screenTime = document.getLong("screenTime") ?: 0L
                    Log.d("ChildDetails", "Screen time updated: $screenTime")
                } else {
                    Log.e("ChildDetails", "No document found for child ID: $childId")
                }
            }
            .addOnFailureListener { e ->
                Log.e("ChildDetails", "Error fetching screen time", e)
            }
    }

    // Fetch installed apps when Apps button is clicked
    LaunchedEffect(showAppsList) {
        if (showAppsList && childId.isNotBlank()) {
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
        if (showLocationMap && childId.isNotBlank()) {
            var retryCount = 0
            val maxRetries = 3
            val retryDelay = 1000L // 1 second delay between retries

            fun fetchLocation() {
                firestore.collection("locations")
                    .document(childId)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            val latitude = document.getDouble("latitude")
                            val longitude = document.getDouble("longitude")
                            val timestamp = document.getTimestamp("timestamp")
                            
                            if (latitude != null && longitude != null) {
                                // Check if location data is recent (within last 5 minutes)
                                val isRecent = timestamp?.let {
                                    val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)
                                    it.seconds * 1000 > fiveMinutesAgo
                                } ?: false

                                if (isRecent) {
                                    currentLocation = GeoPoint(latitude, longitude)
                                    Log.d("ChildDetails", "Location fetched successfully: $latitude, $longitude")
                                } else {
                                    Log.w("ChildDetails", "Location data is too old")
                                    if (retryCount < maxRetries) {
                                        retryCount++
                                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                            fetchLocation()
                                        }, retryDelay)
                                    } else {
                                        Toast.makeText(context, "Unable to fetch recent location", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Log.e("ChildDetails", "Location data is incomplete")
                                if (retryCount < maxRetries) {
                                    retryCount++
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        fetchLocation()
                                    }, retryDelay)
                                } else {
                                    Toast.makeText(context, "Location data is incomplete", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Log.e("ChildDetails", "No location document found")
                            if (retryCount < maxRetries) {
                                retryCount++
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    fetchLocation()
                                }, retryDelay)
                            } else {
                                Toast.makeText(context, "No location data available", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("ChildDetails", "Error fetching location", e)
                        if (retryCount < maxRetries) {
                            retryCount++
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                fetchLocation()
                            }, retryDelay)
                        } else {
                            Toast.makeText(context, "Failed to fetch location: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            }

            // Start the initial fetch
            fetchLocation()
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
                            onClick = onEditProfile,
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
                        text = "Child Details",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFE0852D)
                    )
                    Text(
                        text = "View and manage child information",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF333333)
                    )
                }
            }

            // Profile Photo Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
                        if (profileBitmap != null) {
                            Image(
                                bitmap = profileBitmap.asImageBitmap(),
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
            }

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

                    // Child Name
                    Text(
                        text = childName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFE0852D)
                    )

                    // Child Email
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Email",
                            tint = Color(0xFF666666),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = childEmail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF666666)
                        )
                    }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Usage
                ActionButton(
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp),
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

                // Location
                ActionButton(
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp),
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
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp),
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

@Composable
fun ActionButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
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