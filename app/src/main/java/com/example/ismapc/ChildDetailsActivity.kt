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
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Location", "Content Filter")
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(childName) },
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }
            
            // Tab Content
            when (selectedTabIndex) {
                0 -> OverviewTab(childId)
                1 -> LocationTab(childId)
                2 -> ContentFilteringTab(childId, childName, "")
            }
        }
    }
}

@Composable
fun OverviewTab(childId: String) {
    var screenTimeState by remember { mutableStateOf<ScreenTimeState>(ScreenTimeState.Loading) }
    var installedAppsState by remember { mutableStateOf<InstalledAppsState>(InstalledAppsState.Loading) }
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()
    var lockedApps by remember { mutableStateOf<List<String>>(emptyList()) }

    // Set up real-time listener for locked apps
    LaunchedEffect(childId) {
        if (childId.isBlank()) {
            screenTimeState = ScreenTimeState.Error("Invalid child ID")
            installedAppsState = InstalledAppsState.Error("Invalid child ID")
            return@LaunchedEffect
        }

        try {
            // Set up locked apps listener
            firestore.collection("lockedApps")
                .document(childId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("OverviewTab", "Error listening to locked apps", error)
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        lockedApps = snapshot.get("lockedApps") as? List<String> ?: emptyList()
                        Log.d("OverviewTab", "Updated locked apps list: $lockedApps")
                    }
                }

            // Fetch screen time data
            firestore.collection("screenTime")
                .document(childId)
                .get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        val screenTime = documentSnapshot.getLong("screenTime") ?: 0L
                        screenTimeState = ScreenTimeState.Success(screenTime)
                        Log.d("ChildDetailsActivity", "Screen time data found: $screenTime")
                    } else {
                        screenTimeState = ScreenTimeState.Success(0L)
                        Log.d("ChildDetailsActivity", "No screen time data found")
                    }
                }
                .addOnFailureListener { e ->
                    screenTimeState = ScreenTimeState.Error("Error fetching screen time: ${e.message}")
                    Log.e("ChildDetailsActivity", "Error fetching screen time data", e)
                }

            // Fetch installed apps
            firestore.collection("installedApps")
                .document(childId)
                .get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        val apps = documentSnapshot.get("apps") as? List<Map<String, Any>>
                        if (apps != null) {
                            // Filter out system apps
                            val nonSystemApps = apps.filter { app ->
                                !(app["isSystemApp"] as? Boolean ?: false)
                            }
                            installedAppsState = InstalledAppsState.Success(nonSystemApps)
                            Log.d("ChildDetailsActivity", "Non-system apps found: ${nonSystemApps.size}")
                        } else {
                            installedAppsState = InstalledAppsState.Success(emptyList())
                            Log.d("ChildDetailsActivity", "No apps found in document")
                        }
                    } else {
                        installedAppsState = InstalledAppsState.Success(emptyList())
                        Log.d("ChildDetailsActivity", "No installed apps document found")
                    }
                }
                .addOnFailureListener { e ->
                    installedAppsState = InstalledAppsState.Error("Error fetching installed apps: ${e.message}")
                    Log.e("ChildDetailsActivity", "Error fetching installed apps", e)
                }
        } catch (e: Exception) {
            screenTimeState = ScreenTimeState.Error("Error: ${e.message}")
            installedAppsState = InstalledAppsState.Error("Error: ${e.message}")
            Log.e("ChildDetailsActivity", "Error in OverviewTab", e)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Screen Time Section
        when (screenTimeState) {
            is ScreenTimeState.Loading -> {
                CircularProgressIndicator()
            }
            is ScreenTimeState.Success -> {
                val screenTime = (screenTimeState as ScreenTimeState.Success).screenTime
                if (screenTime > 0) {
                    ScreenTimeCard(screenTime)
                } else {
                    Text(
                        text = "No screen time data",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is ScreenTimeState.Error -> {
                Text(
                    text = (screenTimeState as ScreenTimeState.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Installed Apps Section
        Text(
            text = "Installed Apps",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        when (installedAppsState) {
            is InstalledAppsState.Loading -> {
                CircularProgressIndicator()
            }
            is InstalledAppsState.Success -> {
                val apps = (installedAppsState as InstalledAppsState.Success).apps
                if (apps.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(apps) { app ->
                            AppListItem(
                                appName = app["appName"] as? String ?: "Unknown App",
                                packageName = app["packageName"] as? String ?: "",
                                context = context,
                                isLocked = lockedApps.contains(app["packageName"] as? String ?: ""),
                                onLockStateChanged = { packageName, newLockState ->
                                    // Update locked apps list
                                    val updatedList = if (newLockState) {
                                        lockedApps + packageName
                                    } else {
                                        lockedApps - packageName
                                    }
                                    lockedApps = updatedList
                                }
                            )
                        }
                    }
                } else {
                    Text(
                        text = "No non-system apps found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is InstalledAppsState.Error -> {
                Text(
                    text = (installedAppsState as InstalledAppsState.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun AppListItem(
    appName: String,
    packageName: String,
    context: Context,
    isLocked: Boolean,
    onLockStateChanged: (String, Boolean) -> Unit
) {
    var isUpdating by remember { mutableStateOf(false) }
    val packageManager = context.packageManager
    var appIcon by remember { mutableStateOf<ImageBitmap?>(null) }
    val firestore = FirebaseFirestore.getInstance()
    val childId = (context as? ChildDetailsActivity)?.intent?.getStringExtra("childId")

    // Load app icon
    LaunchedEffect(packageName) {
        try {
            val icon = packageManager.getApplicationIcon(packageName)
            appIcon = icon.toBitmap().asImageBitmap()
        } catch (e: Exception) {
            Log.e("AppListItem", "Error loading app icon for $packageName", e)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            if (appIcon != null) {
                Image(
                    bitmap = appIcon!!,
                    contentDescription = "App icon for $appName",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                // Placeholder icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // App Name
            Text(
                text = appName,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            // Lock/Unlock Button
            Button(
                onClick = {
                    if (!isUpdating) {
                        isUpdating = true
                        childId?.let { id ->
                            // Get current locked apps
                            firestore.collection("lockedApps")
                                .document(id)
                                .get()
                                .addOnSuccessListener { document ->
                                    val currentLockedApps = (document.get("lockedApps") as? List<String> ?: emptyList()).toMutableList()
                                    
                                    // Update the list
                                    if (isLocked) {
                                        currentLockedApps.remove(packageName)
                                    } else {
                                        currentLockedApps.add(packageName)
                                    }

                                    // Update Firestore
                                    val updates = hashMapOf(
                                        "lockedApps" to currentLockedApps,
                                        "lastUpdated" to FieldValue.serverTimestamp()
                                    )

                                    firestore.collection("lockedApps")
                                        .document(id)
                                        .set(updates)
                                        .addOnSuccessListener {
                                            Log.d("AppListItem", "Successfully updated locked apps for child $id")
                                            // Update local state
                                            onLockStateChanged(packageName, !isLocked)
                                            isUpdating = false
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("AppListItem", "Error updating locked apps", e)
                                            isUpdating = false
                                        }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("AppListItem", "Error getting locked apps document", e)
                                    isUpdating = false
                                }
                        }
                    }
                },
                enabled = !isUpdating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLocked) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.primary
                )
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = if (isLocked) "Unlock" else "Lock",
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

sealed class ScreenTimeState {
    object Loading : ScreenTimeState()
    data class Success(val screenTime: Long) : ScreenTimeState()
    data class Error(val message: String) : ScreenTimeState()
}

sealed class InstalledAppsState {
    object Loading : InstalledAppsState()
    data class Success(val apps: List<Map<String, Any>>) : InstalledAppsState()
    data class Error(val message: String) : InstalledAppsState()
}

@Composable
private fun ScreenTimeCard(screenTime: Long) {
    val hours = TimeUnit.MILLISECONDS.toHours(screenTime)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(screenTime) % 60

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            width = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Today's Screen Time",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$hours hours $minutes minutes",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// Add LocationState sealed class
sealed class LocationState {
    object Loading : LocationState()
    data class Success(val latitude: Double, val longitude: Double) : LocationState()
    data class Error(val message: String) : LocationState()
}

@Composable
fun LocationTab(childId: String) {
    var locationState by remember { mutableStateOf<LocationState>(LocationState.Loading) }
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()

    // Initialize osmdroid configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            // Set tile download limits
            tileDownloadThreads = 2
            tileFileSystemCacheMaxBytes = 1024L * 1024L * 50L // 50MB cache
            tileFileSystemCacheTrimBytes = 1024L * 1024L * 25L // 25MB trim
            // Enable tile caching
            osmdroidTileCache = File(context.getExternalFilesDir(null)?.absolutePath ?: context.cacheDir.absolutePath)
        }
    }

    LaunchedEffect(childId) {
        if (childId.isBlank()) {
            locationState = LocationState.Error("Invalid child ID")
            return@LaunchedEffect
        }

        try {
            Log.d("ChildDetailsActivity", "Fetching location data for child: $childId")

            // Set up real-time listener for location updates
            firestore.collection("locations")
                .document(childId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        locationState = LocationState.Error("Error fetching location: ${error.message}")
                        Log.e("ChildDetailsActivity", "Error fetching location data", error)
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val latitude = snapshot.getDouble("latitude") ?: 0.0
                        val longitude = snapshot.getDouble("longitude") ?: 0.0
                        locationState = LocationState.Success(latitude, longitude)
                        Log.d("ChildDetailsActivity", "Location data found: lat=$latitude, lon=$longitude")
                    } else {
                        locationState = LocationState.Error("No location data available")
                        Log.d("ChildDetailsActivity", "No location data found")
                    }
                }
        } catch (e: Exception) {
            locationState = LocationState.Error("Error: ${e.message}")
            Log.e("ChildDetailsActivity", "Error in LocationTab", e)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (locationState) {
            is LocationState.Loading -> {
                CircularProgressIndicator()
            }
            is LocationState.Success -> {
                val location = locationState as LocationState.Success

                // Map View
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    AndroidView(
                        factory = { context ->
                            MapView(context).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                
                                // Configure map settings
                                controller.setZoom(15.0)
                                controller.setCenter(GeoPoint(location.latitude, location.longitude))
                                
                                // Add compass overlay
                                overlays.add(org.osmdroid.views.overlay.compass.CompassOverlay(
                                    context,
                                    org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider(context),
                                    this
                                ).apply {
                                    enableCompass()
                                })
                                
                                // Add scale bar
                                overlays.add(org.osmdroid.views.overlay.ScaleBarOverlay(this).apply {
                                    setCentred(true)
                                    setScaleBarOffset(
                                        context.resources.displayMetrics.widthPixels / 2,
                                        10
                                    )
                                })
                                
                                // Add marker for current location
                                val marker = Marker(this).apply {
                                    position = GeoPoint(location.latitude, location.longitude)
                                    title = "Current Location"
                                    snippet = "Last updated: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                }
                                overlays.add(marker)
                                
                                // Add minimap overlay
                                overlays.add(org.osmdroid.views.overlay.MinimapOverlay(
                                    context,
                                    this.tileRequestCompleteHandler
                                ).apply {
                                    width = 150
                                    height = 150
                                    setZoomDifference(3)
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { mapView ->
                            mapView.controller.setCenter(GeoPoint(location.latitude, location.longitude))
                            mapView.invalidate()
                        }
                    )
                }

                // Google Maps Button
                Button(
                    onClick = {
                        val gmmIntentUri = android.net.Uri.parse(
                            "geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}"
                        )
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                        mapIntent.setPackage("com.google.android.apps.maps")
                        context.startActivity(mapIntent)
                    }
                ) {
                    Text("Open in Google Maps")
                }
            }
            is LocationState.Error -> {
                Text(
                    text = (locationState as LocationState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun ContentFilteringTab(childId: String, childName: String, parentId: String) {
    var contentResults by remember { mutableStateOf<List<FilteredContent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val firestore = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    LaunchedEffect(childId) {
        try {
            Log.d("ContentFilteringTab", "Setting up listener for child: $childId")
            // Listen for content filtering results in the user's filteredContent subcollection
            firestore.collection("contentToFilter")
                .document(childId)
                .collection("filteredContent")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        val errorMsg = "Error listening for results: ${error.message}"
                        Log.e("ContentFilteringTab", errorMsg)
                        errorMessage = errorMsg
                        isLoading = false
                        return@addSnapshotListener
                    }

                    Log.d("ContentFilteringTab", "Received ${snapshot?.documents?.size ?: 0} results")
                    
                    // Debug log the documents
                    snapshot?.documents?.forEach { doc ->
                        Log.d("ContentFilteringTab", "Document data: ${doc.data}")
                    }
                    
                    contentResults = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            val data = doc.data
                            Log.d("ContentFilteringTab", "Processing document ${doc.id}: $data")
                            
                            FilteredContent(
                                id = doc.id,
                                content = doc.getString("content") ?: "",
                                isBlockable = doc.getBoolean("isBlockable") ?: false,
                                reason = doc.getString("reason") ?: "",
                                timestamp = doc.getTimestamp("timestamp") ?: Timestamp.now(),
                                status = doc.getString("status") ?: "pending"
                            ).also {
                                Log.d("ContentFilteringTab", "Successfully parsed content: ${it.content.take(50)}...")
                            }
                        } catch (e: Exception) {
                            Log.e("ContentFilteringTab", "Error parsing document ${doc.id}", e)
                            null
                        }
                    } ?: emptyList()
                    
                    Log.d("ContentFilteringTab", "Final content results size: ${contentResults.size}")
                    isLoading = false
                    errorMessage = null
                }
        } catch (e: Exception) {
            val errorMsg = "Error setting up listener: ${e.message}"
            Log.e("ContentFilteringTab", errorMsg, e)
            errorMessage = errorMsg
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Content Filtering History",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            errorMessage != null -> {
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
            contentResults.isEmpty() -> {
                Text(
                    text = "No content filtering history found",
                    modifier = Modifier.padding(16.dp)
                )
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(contentResults) { content ->
                        ContentFilteringCard(
                            content = content,
                            onBlock = { contentId ->
                                Log.d("ContentFilteringTab", "Blocking content: $contentId")
                                firestore.collection("contentToFilter")
                                    .document(childId)
                                    .collection("filteredContent")
                                    .document(contentId)
                                    .update("status", "blocked")
                                    .addOnSuccessListener {
                                        Log.d("ContentFilteringTab", "Content blocked successfully")
                                        Toast.makeText(
                                            context,
                                            "Content blocked successfully",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("ContentFilteringTab", "Error blocking content", e)
                                        Toast.makeText(
                                            context,
                                            "Error blocking content",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            },
                            onAllow = { contentId ->
                                Log.d("ContentFilteringTab", "Allowing content: $contentId")
                                firestore.collection("contentToFilter")
                                    .document(childId)
                                    .collection("filteredContent")
                                    .document(contentId)
                                    .update("status", "allowed")
                                    .addOnSuccessListener {
                                        Log.d("ContentFilteringTab", "Content allowed successfully")
                                        Toast.makeText(
                                            context,
                                            "Content allowed successfully",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("ContentFilteringTab", "Error allowing content", e)
                                        Toast.makeText(
                                            context,
                                            "Error allowing content",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContentFilteringCard(
    content: FilteredContent,
    onBlock: (String) -> Unit,
    onAllow: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (content.status) {
                "blocked" -> MaterialTheme.colorScheme.errorContainer
                "allowed" -> MaterialTheme.colorScheme.primaryContainer
                else -> if (content.isBlockable) 
                    MaterialTheme.colorScheme.errorContainer 
                else 
                    MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = content.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Status: ${content.status}",
                style = MaterialTheme.typography.bodySmall
            )
            
            if (content.isBlockable) {
                Text(
                    text = "Reason: ${content.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateFormat.format(content.timestamp.toDate()),
                    style = MaterialTheme.typography.bodySmall
                )
                
                if (content.status == "processed") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onBlock(content.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Block")
                        }
                        
                        Button(
                            onClick = { onAllow(content.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Allow")
                        }
                    }
                }
            }
        }
    }
} 