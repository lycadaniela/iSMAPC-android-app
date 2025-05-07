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
import androidx.compose.ui.unit.dp
import com.example.ismapc.ui.theme.ISMAPCTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.ui.graphics.Color

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

        // Check user type
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
                                // Try to get more information about the error
                                if (e.message?.contains("permission-denied") == true) {
                                    Log.e("MainActivity", "Permission denied error. Current user: ${currentUser.uid}, email: ${currentUser.email}")
                                }
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

    // Fetch parent data and children data from Firestore
    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            Log.d("ParentMainScreen", "Starting data fetch for parent ID: $uid")
            FirebaseFirestore.getInstance()
                .collection(MainActivity.USERS_COLLECTION)
                .document(MainActivity.PARENTS_COLLECTION)
                .collection(uid)
                .document(MainActivity.PROFILE_DOCUMENT)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        parentData = document.data
                        Log.d("ParentMainScreen", "Parent profile found")

                        // After getting parent data, fetch children data
                        val parentEmail = document.getString("email")
                        if (parentEmail != null) {
                            Log.d("ParentMainScreen", "Parent email: $parentEmail")
                            FirebaseFirestore.getInstance()
                                .collectionGroup(MainActivity.PROFILE_DOCUMENT)
                                .whereEqualTo("parentEmail", parentEmail)
                                .get()
                                .addOnSuccessListener { querySnapshot ->
                                    Log.d("ParentMainScreen", "Query path: collectionGroup('${MainActivity.PROFILE_DOCUMENT}')")
                                    Log.d("ParentMainScreen", "Total documents in query: ${querySnapshot.size()}")

                                    val filteredChildren = querySnapshot.documents.mapNotNull { it.data }

                                    childrenData = filteredChildren
                                    Log.d("ParentMainScreen", "Found ${filteredChildren.size} children with matching parentEmail")
                                    isLoading = false
                                }
                                .addOnFailureListener { e ->
                                    Log.e("ParentMainScreen", "Error fetching children", e)
                                    Log.e("ParentMainScreen", "Error details: ${e.message}")
                                    isLoading = false
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
                .addOnFailureListener { e ->
                    Log.e("ParentMainScreen", "Error fetching parent profile", e)
                    isLoading = false
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
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // TODO: Handle child card click
                                        Toast.makeText(context, "Opening ${child["fullName"]}'s profile", Toast.LENGTH_SHORT).show()
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = child["fullName"]?.toString() ?: "Unknown",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = child["email"]?.toString() ?: "No email",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
fun ChildProfileCard(childProfile: Map<String, Any>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Child profile picture",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Child info
            Column {
                Text(
                    text = childProfile["fullName"] as? String ?: "Unknown",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = childProfile["email"] as? String ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildMainScreen(onLogout: () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Child Dashboard") },
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
            Text(
                text = "Welcome Child!",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "This is your child dashboard",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}