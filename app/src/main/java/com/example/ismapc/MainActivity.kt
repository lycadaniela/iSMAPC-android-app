package com.example.ismapc

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ismapc.ui.theme.ISMAPCTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.ui.graphics.Color
import android.app.AppOpsManager
import android.content.Context
import android.provider.Settings
import android.app.usage.UsageStatsManager
import android.app.Activity
import android.os.Build
import com.google.firebase.firestore.FieldValue

class MainActivity : ComponentActivity() {
    companion object {
        const val USERS_COLLECTION = "users"
        const val PARENTS_COLLECTION = "parents"
        const val CHILD_COLLECTION = "child"
        const val PROFILE_DOCUMENT = "profile"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var userType by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        enableEdgeToEdge()
        
        try {
            // Check user type first before starting any services
            val currentUser = auth.currentUser
            if (currentUser != null) {
                Log.d("MainActivity", "Current user: ${currentUser.uid}, email: ${currentUser.email}")
                // First check parent collection
                firestore.collection(USERS_COLLECTION)
                    .document(PARENTS_COLLECTION)
                    .collection(currentUser.uid)
                    .document(PROFILE_DOCUMENT)
                    .get()
                    .addOnSuccessListener { parentDoc ->
                        Log.d("MainActivity", "Checking parent collection: ${parentDoc.exists()}")
                        if (parentDoc.exists()) {
                            userType = "parent"
                            Log.d("MainActivity", "User is a parent")
                            // No need to start background services for parents
                        } else {
                            // If not found in parent, check child collection
                            Log.d("MainActivity", "Checking child collection")
                            firestore.collection(USERS_COLLECTION)
                                .document(CHILD_COLLECTION)
                                .collection("profile")
                                .document(currentUser.uid)
                                .get()
                                .addOnSuccessListener { childDoc ->
                                    Log.d("MainActivity", "Child doc exists: ${childDoc.exists()}")
                                    if (childDoc.exists()) {
                                        userType = "child"
                                        Log.d("MainActivity", "User is a child")
                                        try {
                                            // Start all background services for child users
                                            startChildServices()
                                        } catch (e: Exception) {
                                            Log.e("MainActivity", "Error starting services: ${e.message}")
                                        }
                                    } else {
                                        // User not found in either collection
                                        Log.e("MainActivity", "User not found in either collection")
                                        Toast.makeText(this, "User type not found. Please sign in again.", Toast.LENGTH_LONG).show()
                                        auth.signOut()
                                        startActivity(Intent(this, LoginActivity::class.java))
                                        finish()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("MainActivity", "Error checking child collection: ${e.message}")
                                    Log.e("MainActivity", "Error details", e)
                                    Toast.makeText(this, "Error checking user type: ${e.message}", Toast.LENGTH_LONG).show()
                                    auth.signOut()
                                    startActivity(Intent(this, LoginActivity::class.java))
                                    finish()
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("MainActivity", "Error checking parent collection: ${e.message}")
                        Log.e("MainActivity", "Error details", e)
                        Toast.makeText(this, "Error checking user type: ${e.message}", Toast.LENGTH_LONG).show()
                        auth.signOut()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
            } else {
                // No user logged in
                Log.d("MainActivity", "No user logged in")
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "Error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
        }

        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (userType) {
                        "parent" -> ParentMainScreen(
                            onLogout = {
                                auth.signOut()
                                startActivity(Intent(this, LoginActivity::class.java))
                                finish()
                            }
                        )
                        "child" -> ChildMainScreen(
                            onLogout = {
                                auth.signOut()
                                startActivity(Intent(this, LoginActivity::class.java))
                                finish()
                            }
                        )
                        else -> {
                            // Show loading state
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startChildServices() {
        try {
            Log.d("MainActivity", "Starting child services")
            
            // Start the InstalledAppsService
            startService(Intent(this, InstalledAppsService::class.java))

            // Start the AppLockService
            startAppLockService()

            // Start the DeviceLockService
            val deviceLockServiceIntent = Intent(this, DeviceLockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(deviceLockServiceIntent)
            } else {
                startService(deviceLockServiceIntent)
            }

            // Start location service
            val locationServiceIntent = Intent(this, LocationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(locationServiceIntent)
            } else {
                startService(locationServiceIntent)
            }
            
            // Start screen time service
            val screenTimeServiceIntent = Intent(this, ScreenTimeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(screenTimeServiceIntent)
            } else {
                startService(screenTimeServiceIntent)
            }

            Log.d("MainActivity", "All child services started successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting child services: ${e.message}")
        }
    }

    private fun startAppLockService() {
        try {
            val serviceIntent = Intent(this, AppLockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting AppLockService: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            // Only restart services for child users
            if (userType == "child") {
                Log.d("MainActivity", "Restarting child services in onResume")
                startChildServices()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onResume: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            // Stop services when activity is destroyed
            if (userType == "child") {
                Log.d("MainActivity", "Stopping child services in onDestroy")
                stopService(Intent(this, LocationService::class.java))
                stopService(Intent(this, ScreenTimeService::class.java))
                stopService(Intent(this, AppLockService::class.java))
                stopService(Intent(this, InstalledAppsService::class.java))
                stopService(Intent(this, DeviceLockService::class.java))
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onDestroy: ${e.message}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentMainScreen(onLogout: () -> Unit) {
    val currentUser = FirebaseAuth.getInstance().currentUser
    Log.d("AuthCheck", "User email: ${currentUser?.email}")
    var parentData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var childrenData by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val profilePictureManager = remember { ProfilePictureManager(context) }
    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Fetch parent data and children data from Firestore using real-time listeners
    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            Log.d("ParentMainScreen", "Starting data fetch for parent ID: $uid")
            
            // Real-time listener for parent data
            val parentListener = FirebaseFirestore.getInstance()
                .collection(MainActivity.USERS_COLLECTION)
                .document(MainActivity.PARENTS_COLLECTION)
                .collection(uid)
                .document(MainActivity.PROFILE_DOCUMENT)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("ParentMainScreen", "Error listening to parent data", error)
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        parentData = snapshot.data
                        Log.d("ParentMainScreen", "Parent profile updated")

                        // After getting parent data, set up real-time listener for children
                        val parentEmail = snapshot.getString("email")
                        if (parentEmail != null) {
                            Log.d("ParentMainScreen", "Setting up children listener for parent email: $parentEmail")
                            
                            // Real-time listener for children data
                            val childrenListener = FirebaseFirestore.getInstance()
                                .collectionGroup(MainActivity.PROFILE_DOCUMENT)
                                .whereEqualTo("parentEmail", parentEmail)
                                .addSnapshotListener { querySnapshot, error ->
                                    if (error != null) {
                                        Log.e("ParentMainScreen", "Error listening to children data", error)
                                        return@addSnapshotListener
                                    }

                                    if (querySnapshot != null) {
                                        val filteredChildren = querySnapshot.documents.mapNotNull { doc ->
                                            doc.data?.toMutableMap()?.apply {
                                                put("documentId", doc.id)  // Add the document ID to the data map
                                            }
                                        }
                                        childrenData = filteredChildren
                                        Log.d("ParentMainScreen", "Children data updated. Found ${filteredChildren.size} children")
                                        isLoading = false
                                    }
                                }
                        } else {
                            Log.e("ParentMainScreen", "Parent email is null")
                            isLoading = false
                        }
                    } else {
                        Log.e("ParentMainScreen", "No parent profile found for user: $uid")
                        isLoading = false
                    }
                }

            // Load profile picture
            profileBitmap = profilePictureManager.getProfilePictureBitmap(uid)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        IconButton(onClick = { /* TODO: Handle home click */ }) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Home",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Text(
                            text = "Home",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Handle settings click */ }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.Outlined.ExitToApp,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // Profile Picture
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (profileBitmap != null) {
                        Image(
                            bitmap = profileBitmap!!.asImageBitmap(),
                            contentDescription = "Profile Picture",
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile Picture",
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Parent Name
                parentData?.get("fullName")?.let { name ->
                    Text(
                        text = name.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Children Section with horizontal lines
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left horizontal line
                    Divider(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )

                    // "Your Children" text
                    Text(
                        text = "Your Children",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Right horizontal line
                    Divider(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                }

                if (childrenData.isEmpty()) {
                    Text(
                        text = "No children registered yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(childrenData) { child ->
                            ChildProfileCard(
                                childProfile = child,
                                onClick = {
                                    val intent = Intent(context, ChildDetailsActivity::class.java).apply {
                                        // Get the document ID which contains the child's UID
                                        val childDocId = child["documentId"] as? String
                                        if (childDocId != null) {
                                            putExtra("childId", childDocId)
                                            putExtra("childName", child["fullName"] as? String)
                                            Log.d("ParentMainScreen", "Starting ChildDetailsActivity with childId: $childDocId")
                                        } else {
                                            Log.e("ParentMainScreen", "Child document ID is null")
                                            Toast.makeText(context, "Error: Child ID not found", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    context.startActivity(intent)
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
fun ChildProfileCard(
    childProfile: Map<String, Any>,
    onClick: () -> Unit
) {
    var isLocked by remember { mutableStateOf(false) }
    var isUpdating by remember { mutableStateOf(false) }
    val firestore = FirebaseFirestore.getInstance()
    val childId = childProfile["documentId"] as? String ?: ""
    val context = LocalContext.current

    // Set up real-time listener for device lock state
    LaunchedEffect(childId) {
        if (childId.isNotBlank()) {
            firestore.collection("deviceLocks")
                .document(childId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("ChildProfileCard", "Error listening to device lock state", error)
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        isLocked = snapshot.getBoolean("isLocked") ?: false
                    } else {
                        isLocked = false
                    }
                }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile picture or placeholder
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Child profile picture",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Child info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = childProfile["fullName"] as? String ?: "Unknown",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black
                )
                Text(
                    text = childProfile["email"] as? String ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.DarkGray
                )
            }

            // Lock device button
            Button(
                onClick = {
                    if (!isUpdating) {
                        isUpdating = true
                        val updates = hashMapOf(
                            "isLocked" to !isLocked,
                            "lastUpdated" to FieldValue.serverTimestamp()
                        )

                        firestore.collection("deviceLocks")
                            .document(childId)
                            .set(updates)
                            .addOnSuccessListener {
                                isUpdating = false
                                Toast.makeText(
                                    context,
                                    if (!isLocked) "Device unlocked successfully" else "Device locked successfully",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .addOnFailureListener { e ->
                                Log.e("ChildProfileCard", "Error updating device lock state", e)
                                isUpdating = false
                                Toast.makeText(
                                    context,
                                    "Error: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                },
                enabled = !isUpdating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLocked) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = if (isLocked) "Unlock Device" else "Lock Device",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = if (isLocked) "Unlock" else "Lock",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildMainScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    var hasUsagePermission by remember { mutableStateOf(false) }
    var serviceStarted by remember { mutableStateOf(false) }

    // Check permissions when the screen is first loaded
    LaunchedEffect(Unit) {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        hasUsagePermission = mode == android.app.AppOpsManager.MODE_ALLOWED

        if (!hasUsagePermission || !Settings.canDrawOverlays(context)) {
            // Navigate to permission screen if permissions are not granted
            (context as? Activity)?.let { activity ->
                activity.startActivity(Intent(context, ChildPermissionActivity::class.java))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            (context as? Activity)?.let { activity ->
                                activity.startActivity(Intent(context, ChildPermissionActivity::class.java))
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Check Permissions"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.Outlined.ExitToApp,
                            contentDescription = "Logout"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Success icon
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Success",
                modifier = Modifier
                    .size(64.dp)
                    .padding(bottom = 16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            // Connected successfully text
            Text(
                text = "Connected Successfully",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}