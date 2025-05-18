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
    var showAppsList by remember { mutableStateOf(false) }
    var showLocationMap by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    
    // Calculate screen time percentage
    val screenTimePercentage = remember(screenTime) {
        val totalDayMilliseconds = 24 * 60 * 60 * 1000L // 24 hours in milliseconds
        ((screenTime.toFloat() / totalDayMilliseconds) * 100).coerceIn(0f, 100f)
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

        // Fetch screen time for progress calculation
            firestore.collection("screenTime")
                .document(childId)
                .get()
            .addOnSuccessListener { document ->
                screenTime = document.getLong("screenTime") ?: 0L
                Log.d("ChildDetails", "Screen time fetched: $screenTime")
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

        // Child Photo Section with Percentage
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                    modifier = Modifier
                        .fillMaxWidth()
                    .padding(bottom = 4.dp),
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
            
            // Screen Time Percentage Text
            Text(
                text = "Spent ${String.format("%.1f", screenTimePercentage)}% of the day on the phone",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(bottom = 16.dp),
                textAlign = TextAlign.Center
            )

            // Screen Time Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                    )
                    .padding(top = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                                text = "Today's Usage",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            val hours = TimeUnit.MILLISECONDS.toHours(screenTime)
                            val minutes = TimeUnit.MILLISECONDS.toMinutes(screenTime) % 60
                            
                            Text(
                                text = "$hours hours $minutes minutes",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Action Buttons
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { 
                                val intent = Intent(context, InstalledAppsActivity::class.java).apply {
                                    putExtra("childId", childId)
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 6.dp,
                                pressedElevation = 8.dp
                            )
                        ) {
                            Text(
                                "Apps",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Button(
                            onClick = { 
                                val intent = Intent(context, ContentFilteringActivity::class.java).apply {
                                    putExtra("childId", childId)
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 6.dp,
                                pressedElevation = 8.dp
                            )
                        ) {
                            Text(
                                "Location",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Button(
                            onClick = { 
                                val intent = Intent(context, ContentFilteringActivity::class.java).apply {
                                    putExtra("childId", childId)
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 6.dp,
                                pressedElevation = 8.dp
                            )
                        ) {
                            Text(
                                "Content Filter",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// Keep only the necessary sealed classes and remove unused ones
sealed class ScreenTimeState {
    object Loading : ScreenTimeState()
    data class Success(val screenTime: Long) : ScreenTimeState()
    data class Error(val message: String) : ScreenTimeState()
} 