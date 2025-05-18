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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Installed Apps", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            if (isLoading || !iconsLoaded) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(installedApps) { app ->
                        val appName = app["appName"] as? String ?: ""
                        val packageName = app["packageName"] as? String ?: ""
                        val isSystemApp = app["isSystemApp"] as? Boolean ?: false
                        if (!isSystemApp) {
                            val isLocked = lockedApps.contains(packageName)
                            var isUpdating by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight()
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
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
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = appName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Button(
                                            onClick = {
                                                if (!isUpdating) {
                                                    isUpdating = true
                                                    scope.launch {
                                                        try {
                                                            val newLockedApps = if (isLocked) {
                                                                lockedApps - packageName
                                                            } else {
                                                                lockedApps + packageName
                                                            }
                                                            firestore.collection("lockedApps")
                                                                .document(childId)
                                                                .set(mapOf("lockedApps" to newLockedApps))
                                                                .await()
                                                            lockedApps = newLockedApps
                                                        } catch (e: Exception) {
                                                            Log.e("InstalledAppsScreen", "Error updating lock state", e)
                                                        } finally {
                                                            isUpdating = false
                                                        }
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
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
                        }
                    }
                }
            }
        }
    }
} 