package com.example.ismapc

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ismapc.ui.theme.ISMAPCTheme
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toBitmap

class InstalledAppsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val childId = intent.getStringExtra("childId") ?: ""
        val packageManager = packageManager

        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var appIconMap by remember { mutableStateOf<Map<String, ImageBitmap?>>(emptyMap()) }
                    var installedApps by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
                    var iconsLoaded by remember { mutableStateOf(false) }

                    // Fetch installed apps and icons
                    LaunchedEffect(childId) {
                        try {
                            val firestore = FirebaseFirestore.getInstance()
                            val appsDoc = firestore.collection("installedApps")
                                .document(childId)
                                .get()
                                .await()
                            if (appsDoc.exists()) {
                                @Suppress("UNCHECKED_CAST")
                                installedApps = appsDoc.get("apps") as? List<Map<String, Any>> ?: emptyList()
                            }
                            // Fetch icons for all apps
                            val iconMap = mutableMapOf<String, ImageBitmap?>()
                            for (app in installedApps) {
                                val packageName = app["packageName"] as? String ?: continue
                                try {
                                    val drawable: Drawable = packageManager.getApplicationIcon(packageName)
                                    val bitmap = drawable.toBitmap(96, 96)
                                    iconMap[packageName] = bitmap.asImageBitmap()
                                } catch (e: Exception) {
                                    iconMap[packageName] = null
                                }
                            }
                            appIconMap = iconMap
                            iconsLoaded = true
                        } catch (e: Exception) {
                            Log.e("InstalledAppsScreen", "Error fetching data", e)
                        }
                    }

                    InstalledAppsScreen(childId, installedApps, appIconMap, iconsLoaded)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledAppsScreen(
    childId: String,
    installedApps: List<Map<String, Any>>,
    appIconMap: Map<String, ImageBitmap?>,
    iconsLoaded: Boolean
) {
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()
    var lockedApps by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Fetch locked apps
    LaunchedEffect(childId) {
        try {
            val lockedAppsDoc = firestore.collection("lockedApps")
                .document(childId)
                .get()
                .await()
            if (lockedAppsDoc.exists()) {
                lockedApps = lockedAppsDoc.get("lockedApps") as? List<String> ?: emptyList()
            }
            isLoading = false
        } catch (e: Exception) {
            Log.e("InstalledAppsScreen", "Error fetching locked apps", e)
            isLoading = false
        }
    }

    // Filter apps based on search query and sort locked apps to top
    val filteredApps = remember(installedApps, searchQuery, lockedApps) {
        val filtered = if (searchQuery.isEmpty()) {
            installedApps
        } else {
            installedApps.filter { app ->
                val appName = app["appName"] as? String ?: ""
                appName.contains(searchQuery, ignoreCase = true)
            }
        }
        
        // Sort apps: locked apps first, then alphabetically
        filtered.sortedWith(compareBy(
            { !lockedApps.contains(it["packageName"] as? String) }, // Locked apps first
            { (it["appName"] as? String)?.uppercase() ?: "" } // Then alphabetically
        ))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(
                        onClick = { 
                            (context as? ComponentActivity)?.finish()
                            Log.d("InstalledApps", "Back button pressed")
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back to previous screen",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Installed Apps",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFE0852D),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Manage app access and restrictions",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF424242),
                    textAlign = TextAlign.Center
                )
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("Search apps") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color(0xFFE0852D),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Apps List
            if (isLoading || !iconsLoaded) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFE0852D)
                    )
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isEmpty()) "No apps found" else "No apps match your search",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    // Show locked apps count if there are any
                    val lockedAppsCount = filteredApps.count { 
                        !(it["isSystemApp"] as? Boolean ?: false) && 
                        lockedApps.contains(it["packageName"] as? String)
                    }
                    
                    if (lockedAppsCount > 0) {
                        Text(
                            text = "Locked Apps ($lockedAppsCount)",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // Display locked apps first
                        filteredApps.filter { 
                            !(it["isSystemApp"] as? Boolean ?: false) && 
                            lockedApps.contains(it["packageName"] as? String)
                        }.forEach { app ->
                            AppCard(
                                app = app,
                                appIconMap = appIconMap,
                                isLocked = true,
                                onLockStateChanged = { packageName ->
                                    scope.launch {
                                        try {
                                            val newLockedApps = lockedApps - packageName
                                            firestore.collection("lockedApps")
                                                .document(childId)
                                                .set(mapOf("lockedApps" to newLockedApps))
                                                .await()
                                            lockedApps = newLockedApps
                                        } catch (e: Exception) {
                                            Log.e("InstalledAppsScreen", "Error updating lock state", e)
                                        }
                                    }
                                }
                            )
                        }
                        
                        // Add divider after locked apps section
                        Divider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            thickness = 1.dp
                        )
                    }
                    
                    Text(
                        text = "All Apps (${filteredApps.count { !(it["isSystemApp"] as? Boolean ?: false) }})",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFE0852D),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Display remaining apps
                    filteredApps.filter { 
                        !(it["isSystemApp"] as? Boolean ?: false) && 
                        !lockedApps.contains(it["packageName"] as? String)
                    }.forEach { app ->
                        AppCard(
                            app = app,
                            appIconMap = appIconMap,
                            isLocked = false,
                            onLockStateChanged = { packageName ->
                                scope.launch {
                                    try {
                                        val newLockedApps = lockedApps + packageName
                                        firestore.collection("lockedApps")
                                            .document(childId)
                                            .set(mapOf("lockedApps" to newLockedApps))
                                            .await()
                                        lockedApps = newLockedApps
                                    } catch (e: Exception) {
                                        Log.e("InstalledAppsScreen", "Error updating lock state", e)
                                    }
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
private fun AppCard(
    app: Map<String, Any>,
    appIconMap: Map<String, ImageBitmap?>,
    isLocked: Boolean,
    onLockStateChanged: (String) -> Unit
) {
    val appName = app["appName"] as? String ?: ""
    val packageName = app["packageName"] as? String ?: ""
    var isUpdating by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLocked) 
                Color(0xFFFFE0E0) // Light red background for locked apps
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // App Icon or First Letter
                val icon = appIconMap[packageName]
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = appName,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = appName.firstOrNull()?.uppercase() ?: "?",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                }
                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Button(
                onClick = {
                    if (!isUpdating) {
                        isUpdating = true
                        onLockStateChanged(packageName)
                        isUpdating = false
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLocked) MaterialTheme.colorScheme.error else Color(0xFFE0852D),
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(36.dp)
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Outlined.Lock else Icons.Default.Lock,
                            contentDescription = if (isLocked) "Unlock App" else "Lock App"
                        )
                        Text(
                            text = if (isLocked) "Unlock" else "Lock",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
} 