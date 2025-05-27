package com.example.ismapc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ismapc.ui.theme.ISMAPCTheme
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import android.util.Log
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment

class LocationMapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize OSMDroid configuration
        Configuration.getInstance().userAgentValue = packageName
        
        // Get the child ID from the intent
        val childId = intent.getStringExtra("childId")
        val childName = intent.getStringExtra("childName")
        
        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LocationMapScreen(
                        childId = childId ?: "",
                        childName = childName ?: "Child Location"
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh map tiles when activity resumes
        Configuration.getInstance().userAgentValue = packageName
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationMapScreen(childId: String, childName: String) {
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()
    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    
    // Fetch location data
    LaunchedEffect(childId) {
        if (childId.isBlank()) {
            Log.e("LocationMap", "Child ID is blank")
            return@LaunchedEffect
        }

        // Set up real-time listener for location updates
        firestore.collection("locations")
            .document(childId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("LocationMap", "Error listening to location data", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val latitude = snapshot.getDouble("latitude")
                    val longitude = snapshot.getDouble("longitude")
                    if (latitude != null && longitude != null) {
                        currentLocation = GeoPoint(latitude, longitude)
                        // Update map marker if map is initialized
                        mapView?.let { map ->
                            map.overlays.clear()
                            val marker = Marker(map).apply {
                                position = currentLocation
                                title = childName
                            }
                            map.overlays.add(marker)
                            map.controller.animateTo(currentLocation)
                        }
                    }
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Map View
        AndroidView(
            factory = { context ->
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(18.0)
                    mapView = this
                    
                    // Set initial location if available
                    currentLocation?.let { location ->
                        controller.setCenter(location)
                        val marker = Marker(this).apply {
                            position = location
                            title = childName
                        }
                        overlays.add(marker)
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                // Refresh map tiles when the view updates
                map.invalidate()
            }
        )

        // Back Button
        IconButton(
            onClick = { 
                (context as LocationMapActivity).finish()
                Log.d("LocationMap", "Back button pressed")
            },
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
                .size(48.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back to previous screen",
                modifier = Modifier.size(24.dp)
            )
        }

        // Google Maps Button
        FloatingActionButton(
            onClick = {
                currentLocation?.let { location ->
                    val gmmIntentUri = Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}(${childName})")
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    if (mapIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(mapIntent)
                    }
                }
            },
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.BottomEnd),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Open in Google Maps",
                modifier = Modifier.size(24.dp)
            )
        }
    }
} 