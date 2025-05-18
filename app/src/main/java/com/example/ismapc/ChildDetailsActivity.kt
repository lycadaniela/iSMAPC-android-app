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
            
        // Fetch screen time for progress calculation
        firestore.collection("screenTime")
            .document(childId)
            .get()
            .addOnSuccessListener { document ->
                screenTime = document.getLong("screenTime") ?: 0L
                Log.d("ChildDetails", "Screen time fetched: $screenTime")
            }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary)
    ) {
        // Top Bar with Back Button and Email Icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Button
            IconButton(
                onClick = { (context as? Activity)?.finish() }
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Email Icon and Dropdown
            Box {
                IconButton(
                    onClick = { 
                        showEmailDropdown = !showEmailDropdown
                        Log.d("ChildDetails", "Email dropdown toggled. Current email: $childEmail")
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Show email",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                DropdownMenu(
                    expanded = showEmailDropdown,
                    onDismissRequest = { showEmailDropdown = false },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp)
                ) {
                    if (childEmail != null) {
                        Text(
                            text = childEmail!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    } else {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Loading email...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 72.dp) // Add padding to account for the top bar
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Child Photo Section
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Circular Progress Bar
                    CircularProgressIndicator(
                        progress = (screenTime.toFloat() / (24 * 60 * 60 * 1000)).coerceIn(0f, 1f), // 24 hours max
                        modifier = Modifier.size(160.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 6.dp
                    )
                    
                    // Child Photo
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (childPhotoUrl != null) {
                            AsyncImage(
                                model = childPhotoUrl,
                                contentDescription = "Child photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = childName.firstOrNull()?.toString()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Screen Time Section
            item {
                Text(
                    text = "Screen Time",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                var screenTimeState by remember { mutableStateOf<ScreenTimeState>(ScreenTimeState.Loading) }
                val firestore = FirebaseFirestore.getInstance()

                LaunchedEffect(childId) {
                    if (childId.isBlank()) {
                        screenTimeState = ScreenTimeState.Error("Invalid child ID")
                        return@LaunchedEffect
                    }

                    try {
                        firestore.collection("screenTime")
                            .document(childId)
                            .get()
                            .addOnSuccessListener { documentSnapshot ->
                                if (documentSnapshot.exists()) {
                                    val screenTime = documentSnapshot.getLong("screenTime") ?: 0L
                                    screenTimeState = ScreenTimeState.Success(screenTime)
                                } else {
                                    screenTimeState = ScreenTimeState.Success(0L)
                                }
                            }
                            .addOnFailureListener { e ->
                                screenTimeState = ScreenTimeState.Error("Error fetching screen time: ${e.message}")
                            }
                    } catch (e: Exception) {
                        screenTimeState = ScreenTimeState.Error("Error: ${e.message}")
                    }
                }

                when (screenTimeState) {
                    is ScreenTimeState.Loading -> {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    is ScreenTimeState.Success -> {
                        val screenTime = (screenTimeState as ScreenTimeState.Success).screenTime
                        if (screenTime > 0) {
                            ScreenTimeCard(screenTime)
                        } else {
                            Text(
                                text = "No screen time data",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimary
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
            }

            // Location Section
            item {
                Text(
                    text = "Location",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                var locationState by remember { mutableStateOf<LocationState>(LocationState.Loading) }
                val firestore = FirebaseFirestore.getInstance()

                LaunchedEffect(Unit) {
                    Configuration.getInstance().apply {
                        userAgentValue = context.packageName
                        tileDownloadThreads = 2
                        tileFileSystemCacheMaxBytes = 1024L * 1024L * 50L
                        tileFileSystemCacheTrimBytes = 1024L * 1024L * 25L
                        osmdroidTileCache = File(context.getExternalFilesDir(null)?.absolutePath ?: context.cacheDir.absolutePath)
                    }
                }

                LaunchedEffect(childId) {
                    if (childId.isBlank()) {
                        locationState = LocationState.Error("Invalid child ID")
                        return@LaunchedEffect
                    }

                    try {
                        firestore.collection("locations")
                            .document(childId)
                            .addSnapshotListener { snapshot, error ->
                                if (error != null) {
                                    locationState = LocationState.Error("Error fetching location: ${error.message}")
                                    return@addSnapshotListener
                                }

                                if (snapshot != null && snapshot.exists()) {
                                    val latitude = snapshot.getDouble("latitude") ?: 0.0
                                    val longitude = snapshot.getDouble("longitude") ?: 0.0
                                    locationState = LocationState.Success(latitude, longitude)
                                } else {
                                    locationState = LocationState.Error("No location data available")
                                }
                            }
                    } catch (e: Exception) {
                        locationState = LocationState.Error("Error: ${e.message}")
                    }
                }

                when (locationState) {
                    is LocationState.Loading -> {
                        CircularProgressIndicator()
                    }
                    is LocationState.Success -> {
                        val location = locationState as LocationState.Success
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            AndroidView(
                                factory = { context ->
                                    MapView(context).apply {
                                        setTileSource(TileSourceFactory.MAPNIK)
                                        setMultiTouchControls(true)
                                        controller.setZoom(15.0)
                                        controller.setCenter(GeoPoint(location.latitude, location.longitude))
                                        
                                        overlays.add(org.osmdroid.views.overlay.compass.CompassOverlay(
                                            context,
                                            org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider(context),
                                            this
                                        ).apply {
                                            enableCompass()
                                        })
                                        
                                        overlays.add(org.osmdroid.views.overlay.ScaleBarOverlay(this).apply {
                                            setCentred(true)
                                            setScaleBarOffset(
                                                context.resources.displayMetrics.widthPixels / 2,
                                                10
                                            )
                                        })
                                        
                                        val marker = Marker(this).apply {
                                            position = GeoPoint(location.latitude, location.longitude)
                                            title = "Current Location"
                                            snippet = "Last updated: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}"
                                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                        }
                                        overlays.add(marker)
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                update = { mapView ->
                                    mapView.controller.setCenter(GeoPoint(location.latitude, location.longitude))
                                    mapView.invalidate()
                                }
                            )
                        }

                        Button(
                            onClick = {
                                val gmmIntentUri = android.net.Uri.parse(
                                    "geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}"
                                )
                                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                mapIntent.setPackage("com.google.android.apps.maps")
                                context.startActivity(mapIntent)
                            },
                            modifier = Modifier.padding(top = 8.dp)
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

            // Installed Apps Section
            item {
                Text(
                    text = "Installed Apps",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                var installedAppsState by remember { mutableStateOf<InstalledAppsState>(InstalledAppsState.Loading) }
                var lockedApps by remember { mutableStateOf<List<String>>(emptyList()) }
                val firestore = FirebaseFirestore.getInstance()

                LaunchedEffect(childId) {
                    if (childId.isBlank()) {
                        installedAppsState = InstalledAppsState.Error("Invalid child ID")
                        return@LaunchedEffect
                    }

                    try {
                        firestore.collection("lockedApps")
                            .document(childId)
                            .addSnapshotListener { snapshot, error ->
                                if (error != null) {
                                    Log.e("OverviewTab", "Error listening to locked apps", error)
                                    return@addSnapshotListener
                                }

                                if (snapshot != null && snapshot.exists()) {
                                    lockedApps = snapshot.get("lockedApps") as? List<String> ?: emptyList()
                                }
                            }

                        firestore.collection("installedApps")
                            .document(childId)
                            .get()
                            .addOnSuccessListener { documentSnapshot ->
                                if (documentSnapshot.exists()) {
                                    val apps = documentSnapshot.get("apps") as? List<Map<String, Any>>
                                    if (apps != null) {
                                        val nonSystemApps = apps.filter { app ->
                                            !(app["isSystemApp"] as? Boolean ?: false)
                                        }
                                        installedAppsState = InstalledAppsState.Success(nonSystemApps)
                                    } else {
                                        installedAppsState = InstalledAppsState.Success(emptyList())
                                    }
                                } else {
                                    installedAppsState = InstalledAppsState.Success(emptyList())
                                }
                            }
                            .addOnFailureListener { e ->
                                installedAppsState = InstalledAppsState.Error("Error fetching installed apps: ${e.message}")
                            }
                    } catch (e: Exception) {
                        installedAppsState = InstalledAppsState.Error("Error: ${e.message}")
                    }
                }

                when (installedAppsState) {
                    is InstalledAppsState.Loading -> {
                        CircularProgressIndicator()
                    }
                    is InstalledAppsState.Success -> {
                        val apps = (installedAppsState as InstalledAppsState.Success).apps
                        if (apps.isNotEmpty()) {
                            apps.forEach { app ->
                                AppListItem(
                                    appName = app["appName"] as? String ?: "Unknown App",
                                    packageName = app["packageName"] as? String ?: "",
                                    context = context,
                                    isLocked = lockedApps.contains(app["packageName"] as? String ?: ""),
                                    onLockStateChanged = { packageName, newLockState ->
                                        val updatedList = if (newLockState) {
                                            lockedApps + packageName
                                        } else {
                                            lockedApps - packageName
                                        }
                                        lockedApps = updatedList
                                    }
                                )
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

            // Content Filtering Section
            item {
                Text(
                    text = "Content Filtering History",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                var contentResults by remember { mutableStateOf<List<FilteredContent>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                var errorMessage by remember { mutableStateOf<String?>(null) }
                val firestore = FirebaseFirestore.getInstance()

                LaunchedEffect(childId) {
                    try {
                        firestore.collection("contentToFilter")
                            .document(childId)
                            .collection("filteredContent")
                            .orderBy("timestamp", Query.Direction.DESCENDING)
                            .addSnapshotListener { snapshot, error ->
                                if (error != null) {
                                    errorMessage = "Error listening for results: ${error.message}"
                                    isLoading = false
                                    return@addSnapshotListener
                                }

                                contentResults = snapshot?.documents?.mapNotNull { doc ->
                                    try {
                                        val data = doc.data
                                        FilteredContent(
                                            id = doc.id,
                                            content = doc.getString("content") ?: "",
                                            isBlockable = doc.getBoolean("isBlockable") ?: false,
                                            reason = doc.getString("reason") ?: "",
                                            timestamp = doc.getTimestamp("timestamp") ?: Timestamp.now(),
                                            status = doc.getString("status") ?: "pending"
                                        )
                                    } catch (e: Exception) {
                                        null
                                    }
                                } ?: emptyList()
                                
                                isLoading = false
                                errorMessage = null
                            }
                    } catch (e: Exception) {
                        errorMessage = "Error setting up listener: ${e.message}"
                        isLoading = false
                    }
                }

                when {
                    isLoading -> {
                        CircularProgressIndicator()
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
                        contentResults.forEach { content ->
                            ContentFilteringCard(
                                content = content,
                                onBlock = { contentId ->
                                    firestore.collection("contentToFilter")
                                        .document(childId)
                                        .collection("filteredContent")
                                        .document(contentId)
                                        .update("status", "blocked")
                                        .addOnSuccessListener {
                                            Toast.makeText(
                                                context,
                                                "Content blocked successfully",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(
                                                context,
                                                "Error blocking content",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                },
                                onAllow = { contentId ->
                                    firestore.collection("contentToFilter")
                                        .document(childId)
                                        .collection("filteredContent")
                                        .document(contentId)
                                        .update("status", "allowed")
                                        .addOnSuccessListener {
                                            Toast.makeText(
                                                context,
                                                "Content allowed successfully",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        .addOnFailureListener { e ->
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
                // Placeholder with first letter of app name
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = appName.firstOrNull()?.toString()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
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