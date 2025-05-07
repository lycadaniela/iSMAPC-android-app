package com.example.ismapc

import android.os.Bundle
import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
    val tabs = listOf("Overview", "Location", "Settings")
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
                2 -> SettingsTab(childId)
            }
        }
    }
}

@Composable
fun OverviewTab(childId: String) {
    var screenTimeState by remember { mutableStateOf<ScreenTimeState>(ScreenTimeState.Loading) }
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()

    LaunchedEffect(childId) {
        if (childId.isBlank()) {
            screenTimeState = ScreenTimeState.Error("Invalid child ID")
            return@LaunchedEffect
        }

        try {
            Log.d("ChildDetailsActivity", "Fetching screen time data for child: $childId")

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
        } catch (e: Exception) {
            screenTimeState = ScreenTimeState.Error("Error: ${e.message}")
            Log.e("ChildDetailsActivity", "Error in OverviewTab", e)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (screenTimeState) {
            is ScreenTimeState.Loading -> {
                CircularProgressIndicator()
            }
            is ScreenTimeState.Success -> {
                val screenTime = (screenTimeState as ScreenTimeState.Success).screenTime
                if (screenTime > 0) {
                    Text(
                        text = "Today's Screen Time",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = formatScreenTime(screenTime),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "No data",
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
    }
}

sealed class ScreenTimeState {
    object Loading : ScreenTimeState()
    data class Success(val screenTime: Long) : ScreenTimeState()
    data class Error(val message: String) : ScreenTimeState()
}

@Composable
private fun ScreenTimeCard(screenTime: Long) {
    val hours = TimeUnit.MILLISECONDS.toHours(screenTime)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(screenTime) % 60

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$hours hours $minutes minutes",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                
                // Location Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Current Location",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Text(
                            text = "Latitude: ${location.latitude}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "Longitude: ${location.longitude}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

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
fun SettingsTab(childId: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text("Settings Tab Content")
    }
}

private fun getDateString(calendar: Calendar): String {
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    return "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

private fun formatScreenTime(screenTime: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(screenTime)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(screenTime) % 60
    return "$hours hours $minutes minutes"
} 