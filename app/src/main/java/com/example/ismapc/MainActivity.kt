package com.example.ismapc

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.WriteBatch
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

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
                            // Stop any running child services when switching to parent
                            stopChildServices()
                            // Initialize UI
                            initializeUI()
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
                                        // Initialize UI first
                                        initializeUI()
                                        // Schedule content suggestion worker
                                        scheduleContentSuggestionWorker()
                                        // Then check permissions before starting services
                                        checkAndRequestPermissions()
                                    } else {
                                        // User not found in either collection
                                        handleUserNotFound()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    handleError("Error checking child collection", e)
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        handleError("Error checking parent collection", e)
                    }
            } else {
                // No user logged in
                Log.d("MainActivity", "No user logged in")
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        } catch (e: Exception) {
            handleError("Error in onCreate", e)
        }
    }

    private fun initializeUI() {
        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (userType) {
                        "parent" -> ParentMainScreen(
                            onLogout = {
                                stopChildServices()
                                auth.signOut()
                                startActivity(Intent(this, LoginActivity::class.java))
                                finish()
                            }
                        )
                        "child" -> ChildMainScreen(
                            onLogout = {
                                stopChildServices()
                                auth.signOut()
                                startActivity(Intent(this, LoginActivity::class.java))
                                finish()
                            }
                        )
                        else -> {
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

    private fun checkAndRequestPermissions() {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        val hasUsagePermission = mode == AppOpsManager.MODE_ALLOWED
        val hasOverlayPermission = Settings.canDrawOverlays(this)

        if (!hasUsagePermission || !hasOverlayPermission) {
            // Navigate to permission screen
            startActivity(Intent(this, ChildPermissionActivity::class.java))
        } else {
            // All permissions granted, start services
            startChildServices()
        }
    }

    private fun handleUserNotFound() {
        Log.e("MainActivity", "User not found in either collection")
        Toast.makeText(this, "User type not found. Please sign in again.", Toast.LENGTH_LONG).show()
        auth.signOut()
        GoogleSignIn.getClient(this, GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build())
            .signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun handleError(message: String, e: Exception) {
        Log.e("MainActivity", "$message: ${e.message}")
        Log.e("MainActivity", "Error details", e)
        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        auth.signOut()
        GoogleSignIn.getClient(this, GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build())
            .signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun stopChildServices() {
        try {
            Log.d("MainActivity", "Stopping child services")
            
            // Stop AppUsageService
            stopService(Intent(this, AppUsageService::class.java))
            
            // Stop other existing services...
            stopService(Intent(this, AppLockService::class.java))
            stopService(Intent(this, ScreenTimeService::class.java))
            stopService(Intent(this, ContentFilteringService::class.java))
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Error stopping child services", e)
        }
    }

    private fun startChildServices() {
        try {
            Log.d("MainActivity", "Starting child services")
            
            // Start AppUsageService
            startAppUsageService()
            
            // Start other existing services...
            // For example:
            startService(Intent(this, AppLockService::class.java))
            startService(Intent(this, ScreenTimeService::class.java))
            startService(Intent(this, ContentFilteringService::class.java))
            
            // Start DeviceLockService for device lock/unlock functionality
            val deviceLockIntent = Intent(this, DeviceLockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(deviceLockIntent)
            } else {
                startService(deviceLockIntent)
            }
            Log.d("MainActivity", "DeviceLockService started")
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting child services", e)
        }
    }

    private fun startAppUsageService() {
        try {
            Log.d("MainActivity", "Starting AppUsageService")
            val serviceIntent = Intent(this, AppUsageService::class.java)
            
            // Start as a foreground service to ensure it keeps running
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting AppUsageService", e)
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Check if user is a child and restart services if needed
        if (userType == "child") {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
            
            // Only start services if we have permissions
            if (mode == AppOpsManager.MODE_ALLOWED) {
                Log.d("MainActivity", "Child account resumed, ensuring services are running")
                startAppUsageService()
                
                // Ensure DeviceLockService is running
                val deviceLockIntent = Intent(this, DeviceLockService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(deviceLockIntent)
                } else {
                    startService(deviceLockIntent)
                }
                Log.d("MainActivity", "DeviceLockService restarted in onResume")
            }
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
                stopService(Intent(this, ContentFilteringService::class.java))
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onDestroy: ${e.message}")
        }
    }

    private fun startContentFilteringService(childId: String, childName: String) {
        try {
            val serviceIntent = Intent(this, ContentFilteringService::class.java).apply {
                putExtra("childId", childId)
                putExtra("childName", childName)
                putExtra("parentId", FirebaseAuth.getInstance().currentUser?.uid)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting ContentFilteringService", e)
        }
    }

    private fun stopContentFilteringService() {
        stopService(Intent(this, ContentFilteringService::class.java))
    }

    private fun scheduleContentSuggestionWorker() {
        try {
            Log.d("MainActivity", "Scheduling content suggestion worker")
            
            // Create a periodic work request
            val contentSuggestionWorkRequest = PeriodicWorkRequestBuilder<ContentSuggestionWorker>(
                ContentSuggestionWorker.REFRESH_INTERVAL_HOURS,
                TimeUnit.HOURS
            ).build()
            
            // Schedule the work
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                ContentSuggestionWorker.WORKER_TAG,
                ExistingPeriodicWorkPolicy.KEEP, // Keep existing if already scheduled
                contentSuggestionWorkRequest
            )
            
            Log.d("MainActivity", "Content suggestion worker scheduled")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error scheduling content suggestion worker", e)
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
    var isTrashMode by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val profilePictureManager = remember { ProfilePictureManager(context) }
    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showNotificationsMenu by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }

    // Function to delete parent account and associated child accounts
    fun deleteParentAccount() {
        isDeletingAccount = true
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        val parentEmail = currentUser?.email

        if (currentUser != null && parentEmail != null) {
            // First, get all child accounts associated with this parent
            firestore.collection(MainActivity.USERS_COLLECTION)
                .document(MainActivity.CHILD_COLLECTION)
                .collection("profile")
                .whereEqualTo("parentEmail", parentEmail)
                .get()
                .addOnSuccessListener { childDocuments ->
                    val batch = firestore.batch()
                    
                    // Create a deletion request document
                    val deletionRequest = hashMapOf(
                        "parentId" to currentUser.uid,
                        "parentEmail" to parentEmail,
                        "requestedAt" to FieldValue.serverTimestamp(),
                        "status" to "pending",
                        "type" to "account_deletion",
                        // Add verification data
                        "verificationData" to hashMapOf(
                            "parentName" to (parentData?.get("fullName") as? String ?: "Unknown"),
                            "parentPhone" to (parentData?.get("phoneNumber") as? String ?: "Unknown"),
                            "childAccounts" to childDocuments.map { doc ->
                                hashMapOf(
                                    "childId" to doc.id,
                                    "childName" to (doc.getString("fullName") ?: "Unknown"),
                                    "childEmail" to (doc.getString("email") ?: "Unknown")
                                )
                            },
                            "requestSource" to "parent_dashboard",
                            "deviceInfo" to hashMapOf(
                                "model" to Build.MODEL,
                                "manufacturer" to Build.MANUFACTURER,
                                "androidVersion" to Build.VERSION.RELEASE
                            )
                        )
                    )
                    
                    // Add the deletion request to a special collection
                    firestore.collection("deletionRequests")
                        .document(currentUser.uid)
                        .set(deletionRequest)
                        .addOnSuccessListener {
                            Log.d("AccountDeletion", "Deletion request created successfully for parent: $parentEmail")
                            Log.d("AccountDeletion", "Associated child accounts: ${childDocuments.map { it.id }}")
                            
                            // Delete all child accounts and their data
                            for (childDoc in childDocuments) {
                                val childId = childDoc.id
                                
                                // Delete child's profile
                                batch.delete(childDoc.reference)
                                
                                // Delete child's data from other collections
                                val collectionsToDelete = listOf(
                                    "screenTime",
                                    "locations",
                                    "installedApps",
                                    "lockedApps",
                                    "contentFiltering",
                                    "contentToFilter",
                                    "deviceLocks"
                                )
                                
                                collectionsToDelete.forEach { collectionName ->
                                    // Delete documents where childId matches
                                    firestore.collection(collectionName)
                                        .whereEqualTo("childId", childId)
                                        .get()
                                        .addOnSuccessListener { docs ->
                                            docs.forEach { doc ->
                                                batch.delete(doc.reference)
                                            }
                                        }
                                    
                                    // Also delete documents where the document ID is the childId
                                    batch.delete(firestore.collection(collectionName).document(childId))
                                }
                            }
                            
                            // Delete parent's profile
                            batch.delete(
                                firestore.collection(MainActivity.USERS_COLLECTION)
                                    .document(MainActivity.PARENTS_COLLECTION)
                                    .collection(currentUser.uid)
                                    .document(MainActivity.PROFILE_DOCUMENT)
                            )
                            
                            // Commit all deletions
                            batch.commit()
                                .addOnSuccessListener {
                                    isDeletingAccount = false
                                    Toast.makeText(context, "Account deletion request submitted. Your account will be permanently deleted within 24 hours.", Toast.LENGTH_LONG).show()
                                    // Sign out and navigate to login
                                    auth.signOut()
                                    (context as? Activity)?.let { activity ->
                                        activity.startActivity(Intent(context, LoginActivity::class.java))
                                        activity.finish()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    isDeletingAccount = false
                                    Toast.makeText(context, "Error deleting data: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                        .addOnFailureListener { e ->
                            isDeletingAccount = false
                            Toast.makeText(context, "Error submitting deletion request: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener { e ->
                    isDeletingAccount = false
                    Toast.makeText(context, "Error finding child accounts: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            isDeletingAccount = false
            Toast.makeText(context, "Error: User not found", Toast.LENGTH_SHORT).show()
        }
    }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Main Content
                Column(
                    modifier = Modifier
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        // Top Section with Theme Background
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation = 8.dp,
                                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                                    clip = false
                                )
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFFE0852D), // ISMAPC Orange
                                            Color(0xFFFFB27D)  // ISMAPC LightOrange
                                        )
                                    ),
                                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                                )
                                .padding(vertical = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Top Bar Row with Info and Action Icons
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Info Icon with curved white background
                                    Box(
                                        modifier = Modifier
                                            .width(64.dp)
                                            .height(48.dp)
                                            .offset(x = (-16).dp)
                                            .background(
                                                color = Color.White,
                                                shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
                                            )
                                            .clickable { 
                                                val intent = Intent(context, InfoActivity::class.java)
                                                context.startActivity(intent)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Information",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }

                                    // Settings and Notifications Icons
                                    Row(
                                        modifier = Modifier.padding(end = 16.dp)
                                    ) {
                                        // Notifications Icon with Dropdown
                                        Box {
                                            IconButton(
                                                onClick = { showNotificationsMenu = true }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Notifications,
                                                    contentDescription = "Notifications",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                            
                                            DropdownMenu(
                                                expanded = showNotificationsMenu,
                                                onDismissRequest = { showNotificationsMenu = false },
                                                modifier = Modifier
                                                    .width(280.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.surface,
                                                        RoundedCornerShape(12.dp)
                                                    )
                                            ) {
                                                // Notifications Header
                                                Text(
                                                    text = "NOTIFICATIONS",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                                )
                                                
                                                // Sample notification items (replace with actual notifications)
                                                DropdownMenuItem(
                                                    text = { 
                                                        Column {
                                                            Text(
                                                                "New Message",
                                                                style = MaterialTheme.typography.bodyLarge
                                                            )
                                                            Text(
                                                                "You have a new message from John",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        showNotificationsMenu = false
                                                        // TODO: Handle notification click
                                                    },
                                                    modifier = Modifier.height(64.dp)
                                                )
                                                
                                                DropdownMenuItem(
                                                    text = { 
                                                        Column {
                                                            Text(
                                                                "Location Update",
                                                                style = MaterialTheme.typography.bodyLarge
                                                            )
                                                            Text(
                                                                "Sarah has arrived at school",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        showNotificationsMenu = false
                                                        // TODO: Handle notification click
                                                    },
                                                    modifier = Modifier.height(64.dp)
                                                )
                                                
                                                Divider(
                                                    modifier = Modifier.padding(horizontal = 16.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant
                                                )
                                                
                                                // View All option
                                                DropdownMenuItem(
                                                    text = { 
                                                        Text(
                                                            "View All Notifications",
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    },
                                                    onClick = {
                                                        showNotificationsMenu = false
                                                        // TODO: Navigate to all notifications
                                                    },
                                                    modifier = Modifier.height(48.dp)
                                                )
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.width(8.dp))
                                        
                                        // Settings Icon with Dropdown
                                        Box {
                                            IconButton(
                                                onClick = { showSettingsMenu = true }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Settings,
                                                    contentDescription = "Settings",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                            
                                            DropdownMenu(
                                                expanded = showSettingsMenu,
                                                onDismissRequest = { showSettingsMenu = false },
                                                modifier = Modifier
                                                    .width(240.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.surface,
                                                        RoundedCornerShape(12.dp)
                                                    )
                                            ) {
                                                // General Section
                                                Text(
                                                    text = "GENERAL",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                                )
                                                
                                                DropdownMenuItem(
                                                    text = { 
                                                        Text(
                                                            "Notifications",
                                                            style = MaterialTheme.typography.bodyLarge
                                                        )
                                                    },
                                                    onClick = {
                                                        showSettingsMenu = false
                                                        // TODO: Navigate to Notifications
                                                    },
                                                    modifier = Modifier.height(48.dp)
                                                )
                                                
                                                Divider(
                                                    modifier = Modifier.padding(horizontal = 16.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant
                                                )
                                                
                                                // Account Section
                                                Text(
                                                    text = "ACCOUNT",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                                )
                                                
                                                DropdownMenuItem(
                                                    text = { 
                                                        Text(
                                                            "Change Password",
                                                            style = MaterialTheme.typography.bodyLarge
                                                        )
                                                    },
                                                    onClick = {
                                                        showSettingsMenu = false
                                                        val intent = Intent(context, ChangePasswordActivity::class.java)
                                                        context.startActivity(intent)
                                                    },
                                                    modifier = Modifier.height(48.dp)
                                                )
                                                
                                                DropdownMenuItem(
                                                    text = { 
                                                        Text(
                                                            "Delete Account",
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    },
                                                    onClick = {
                                                        showSettingsMenu = false
                                                        showDeleteAccountDialog = true
                                                    },
                                                    modifier = Modifier.height(48.dp)
                                                )
                                                
                                                DropdownMenuItem(
                                                    text = { 
                                                        Text(
                                                            "Logout",
                                                            style = MaterialTheme.typography.bodyLarge
                                                        )
                                                    },
                                                    onClick = {
                                                        showSettingsMenu = false
                                                        onLogout()
                                                    },
                                                    modifier = Modifier.height(48.dp)
                                                )
                                                
                                                Divider(
                                                    modifier = Modifier.padding(horizontal = 16.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant
                                                )
                                                
                                                // Info Section
                                                Text(
                                                    text = "INFO",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                                )
                                                
                                                DropdownMenuItem(
                                                    text = { 
                                                        Text(
                                                            "About",
                                                            style = MaterialTheme.typography.bodyLarge
                                                        )
                                                    },
                                                    onClick = {
                                                        showSettingsMenu = false
                                                        // TODO: Navigate to About
                                                    },
                                                    modifier = Modifier.height(48.dp)
                                                )
                                                
                                                DropdownMenuItem(
                                                    text = { 
                                                        Text(
                                                            "Terms & Conditions",
                                                            style = MaterialTheme.typography.bodyLarge
                                                        )
                                                    },
                                                    onClick = {
                                                        showSettingsMenu = false
                                                        // TODO: Navigate to Terms & Conditions
                                                    },
                                                    modifier = Modifier.height(48.dp)
                                                )
                                                
                                                DropdownMenuItem(
                                                    text = { 
                                                        Text(
                                                            "Privacy Policy",
                                                            style = MaterialTheme.typography.bodyLarge
                                                        )
                                                    },
                                                    onClick = {
                                                        showSettingsMenu = false
                                                        // TODO: Navigate to Privacy Policy
                                                    },
                                                    modifier = Modifier.height(48.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Parent Profile Section
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Profile Picture
                                    Box(
                                        modifier = Modifier
                                            .size(160.dp)
                                            .border(
                                                width = 4.dp,
                                                color = Color.White,
                                                shape = CircleShape
                                            )
                                            .clip(CircleShape)
                                            .background(Color(0xFFD6D7D3)), // LightGray background
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (profileBitmap != null) {
                                            Image(
                                                bitmap = profileBitmap!!.asImageBitmap(),
                                                contentDescription = "Profile Picture",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = "Profile Picture",
                                                tint = Color.Black,
                                                modifier = Modifier.size(80.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Parent Name
                                    parentData?.get("fullName")?.let { name ->
                                        Text(
                                            text = name.toString(),
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = Color.White
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Parent Email
                                    parentData?.get("email")?.let { email ->
                                        Text(
                                            text = email.toString(),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Parent and Phone Number Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Parent",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color.White
                                        )
                                        
                                        VerticalDivider(
                                            modifier = Modifier
                                                .height(24.dp)
                                                .padding(horizontal = 16.dp),
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                        
                                        parentData?.get("phoneNumber")?.let { phone ->
                                            Text(
                                                text = phone.toString(),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Children Section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))

                            // Add Child Button Section
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { 
                                        val intent = Intent(context, ChildSignUpActivity::class.java).apply {
                                            putExtra("parentEmail", parentData?.get("email") as? String)
                                        }
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD6D7D3) // LightGray from theme
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .width(200.dp)
                                        .shadow(
                                            elevation = 4.dp,
                                            shape = RoundedCornerShape(24.dp),
                                            clip = false
                                        ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add Child",
                                            tint = Color.Black,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Button(
                                    onClick = { isTrashMode = !isTrashMode },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isTrashMode) Color(0xFF8B0000) else Color.Red
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .width(48.dp)
                                        .shadow(
                                            elevation = 4.dp,
                                            shape = RoundedCornerShape(24.dp),
                                            clip = false
                                        ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            // Children List with Scrollable Content
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(vertical = 16.dp)
                                ) {
                                    items(childrenData) { child ->
                                        ChildProfileCard(
                                            childProfile = child,
                                            onClick = {
                                                if (!isTrashMode) {
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
                                            },
                                            isTrashMode = isTrashMode,
                                            onDelete = { childId ->
                                                // Delete child account from Firestore and Auth
                                                val auth = FirebaseAuth.getInstance()
                                                val firestore = FirebaseFirestore.getInstance()
                                                
                                                // Create a batch operation
                                                val batch = firestore.batch()
                                                
                                                // Function to delete all documents in a collection
                                                fun deleteCollection(collectionRef: CollectionReference, batch: WriteBatch) {
                                                    collectionRef.get()
                                                        .addOnSuccessListener { documents ->
                                                            for (document in documents) {
                                                                // Add document to batch for deletion
                                                                batch.delete(document.reference)
                                                            }
                                                        }
                                                }

                                                // Delete main profile
                                                val profileRef = firestore.collection(MainActivity.USERS_COLLECTION)
                                                    .document(MainActivity.CHILD_COLLECTION)
                                                    .collection("profile")
                                                    .document(childId)
                                                batch.delete(profileRef)

                                                // Delete all data in collections that use the child's ID
                                                val collectionsToDelete = listOf(
                                                    "screenTime",
                                                    "locations",
                                                    "installedApps",
                                                    "lockedApps",
                                                    "contentFiltering",
                                                    "contentToFilter",
                                                    "deviceLocks"
                                                )

                                                // Add all documents to batch
                                                collectionsToDelete.forEach { collectionName ->
                                                    val collectionRef = firestore.collection(collectionName)
                                                    // Delete documents where childId matches
                                                    collectionRef.whereEqualTo("childId", childId)
                                                        .get()
                                                        .addOnSuccessListener { documents ->
                                                            for (document in documents) {
                                                                batch.delete(document.reference)
                                                            }
                                                        }
                                                }

                                                // Also delete documents where the document ID is the childId
                                                collectionsToDelete.forEach { collectionName ->
                                                    val docRef = firestore.collection(collectionName).document(childId)
                                                    batch.delete(docRef)
                                                }

                                                // Commit the batch
                                                batch.commit()
                                                    .addOnSuccessListener {
                                                        // Then delete from Auth
                                                        try {
                                                            // Delete the user from Firebase Auth
                                                            auth.currentUser?.let { currentUser ->
                                                                if (currentUser.uid == childId) {
                                                                    currentUser.delete()
                                                                        .addOnSuccessListener {
                                                                            Toast.makeText(context, "Child account and all associated data deleted successfully", Toast.LENGTH_SHORT).show()
                                                                        }
                                                                        .addOnFailureListener { e ->
                                                                            Toast.makeText(context, "Error deleting child account: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                                        }
                                                                } else {
                                                                    // If we can't delete directly, we need to use Admin SDK
                                                                    // For now, just show a message
                                                                    Toast.makeText(context, "Please use the Firebase Console to delete the child account", Toast.LENGTH_LONG).show()
                                                                }
                                                            }
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                    .addOnFailureListener { e ->
                                                        Toast.makeText(context, "Error deleting child data: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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
        }
    }

    // Delete Account Confirmation Dialog
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { 
                if (!isDeletingAccount) {
                    showDeleteAccountDialog = false 
                }
            },
            title = {
                Text(
                    "Delete Account",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column {
                    Text(
                        "Are you sure you want to delete your account? This action cannot be undone and will:",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "• Delete your parent account",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "• Delete all associated child accounts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "• Permanently delete all data",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Note: Your account will be permanently deleted within 24 hours after submitting the request.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!isDeletingAccount) {
                            deleteParentAccount()
                        }
                    },
                    enabled = !isDeletingAccount
                ) {
                    if (isDeletingAccount) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            "Delete",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        if (!isDeletingAccount) {
                            showDeleteAccountDialog = false 
                        }
                    },
                    enabled = !isDeletingAccount
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ChildProfileCard(
    childProfile: Map<String, Any>,
    onClick: () -> Unit,
    isTrashMode: Boolean,
    onDelete: (String) -> Unit
) {
    var isLocked by remember { mutableStateOf(false) }
    var isUpdating by remember { mutableStateOf(false) }
    val firestore = FirebaseFirestore.getInstance()
    val childId = childProfile["documentId"] as? String ?: ""
    val context = LocalContext.current
    val profilePictureManager = remember { ProfilePictureManager(context) }
    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Load profile picture
    LaunchedEffect(childId) {
        profileBitmap = profilePictureManager.getProfilePictureBitmap(childId)
    }

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
            .clickable(onClick = onClick)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(16.dp),
                clip = false
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile picture with white border
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .border(
                        width = 2.dp,
                        color = Color.White,
                        shape = CircleShape
                    )
                    .clip(CircleShape)
                    .background(Color(0xFFD6D7D3)), // LightGray background
                contentAlignment = Alignment.Center
            ) {
                if (profileBitmap != null) {
                    Image(
                        bitmap = profileBitmap!!.asImageBitmap(),
                        contentDescription = "Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile Picture",
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Child name
            Text(
                text = childProfile["fullName"] as? String ?: "Unknown Child",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )

            // Lock/Delete Button
            if (isTrashMode) {
                // Delete Button
                Button(
                    onClick = { onDelete(childId) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Child",
                            tint = Color.White
                        )
                        Text(
                            text = "Delete",
                            color = Color.White
                        )
                    }
                }
            } else {
                // Lock Button
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
                                    Log.d("ChildProfileCard", "Successfully updated device lock state")
                                    isUpdating = false
                                }
                                .addOnFailureListener { e ->
                                    Log.e("ChildProfileCard", "Error updating device lock state", e)
                                    Toast.makeText(
                                        context,
                                        "Error: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    isUpdating = false
                                }
                        }
                    },
                    enabled = !isUpdating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLocked) 
                            Color.Red
                        else 
                            Color.White
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = if (isLocked) Color.White else Color.Black
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = if (isLocked) "Unlock Device" else "Lock Device",
                                tint = if (isLocked) Color.White else Color.Black
                            )
                            Text(
                                text = if (isLocked) "Unlock" else "Lock",
                                color = if (isLocked) Color.White else Color.Black
                            )
                        }
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
                            imageVector = Icons.Filled.ExitToApp,
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
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Dashboard button
            Button(
                onClick = {
                    context.startActivity(Intent(context, ChildDashboardActivity::class.java))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 32.dp)
            ) {
                Text("Open My Dashboard")
            }
        }
    }
}