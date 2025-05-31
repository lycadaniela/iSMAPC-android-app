package com.example.ismapc

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.res.imageResource
import com.example.ismapc.ui.theme.ISMAPCTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import android.net.Uri
import android.graphics.Bitmap
import android.provider.MediaStore
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.util.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap

class EditChildProfileActivity : ComponentActivity() {
    private var selectedImageUri: Uri? = null
    private var profileBitmap: Bitmap? = null
    private lateinit var profilePictureManager: ProfilePictureManager
    private var childId: String? = null
    private var childName: String? = null
    private var childEmail: String? = null

    private val getContent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                try {
                    profileBitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    // Force recomposition of the UI
                    setContent {
                        ISMAPCTheme {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                EditChildProfileScreen(
                                    childId = childId ?: "",
                                    childName = childName ?: "",
                                    onImageSelect = { openImagePicker() },
                                    selectedImageUri = selectedImageUri,
                                    profileBitmap = profileBitmap,
                                    onBack = { finish() }
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profilePictureManager = ProfilePictureManager(this)
        
        // Get intent extras
        childId = intent.getStringExtra("childId")
        childName = intent.getStringExtra("childName")
        childEmail = intent.getStringExtra("childEmail")
        
        // Load existing profile picture if available
        childId?.let { id ->
            profileBitmap = profilePictureManager.getProfilePictureBitmap(id)
        }
        
        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EditChildProfileScreen(
                        childId = childId ?: "",
                        childName = childName ?: "",
                        onImageSelect = { openImagePicker() },
                        selectedImageUri = selectedImageUri,
                        profileBitmap = profileBitmap,
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        getContent.launch(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditChildProfileScreen(
    childId: String,
    childName: String,
    onImageSelect: () -> Unit,
    selectedImageUri: Uri?,
    profileBitmap: Bitmap?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()
    
    // State variables
    var fullName by remember { mutableStateOf(childName) }
    var isSaving by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    Card(
                        modifier = Modifier
                            .width(48.dp)
                            .height(48.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Edit Profile",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFE0852D)
                    )
                    Text(
                        text = "Update your account information",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF333333)
                    )
                }
            }

            // Profile Picture Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                width = 2.dp,
                                color = Color(0xFFE0852D),
                                shape = CircleShape
                            )
                            .clickable(onClick = onImageSelect),
                        contentAlignment = Alignment.Center
                    ) {
                        if (profileBitmap != null) {
                            Image(
                                bitmap = profileBitmap.asImageBitmap(),
                                contentDescription = "Profile Picture",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile Picture",
                                tint = Color(0xFFE0852D),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap to change profile picture",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF4A4A4A)
                    )
                }
            }

            // Form Fields Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full Name") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFFE0852D)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Save Button
            Button(
                onClick = {
                    if (isSaving) return@Button
                    
                    isSaving = true
                    
                    // Save profile picture if changed
                    profileBitmap?.let { bitmap ->
                        val profilePicturePath = ProfilePictureManager(context).saveProfilePicture(bitmap, childId)
                        
                        // Update Firestore
                        firestore.collection("users")
                            .document("child")
                            .collection("profile")
                            .document(childId)
                            .update(
                                mapOf(
                                    "fullName" to fullName,
                                    "profilePicturePath" to profilePicturePath
                                )
                            )
                            .addOnSuccessListener {
                                Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Error updating profile: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                            .addOnCompleteListener {
                                isSaving = false
                            }
                    } ?: run {
                        // Update Firestore without image
                        firestore.collection("users")
                            .document("child")
                            .collection("profile")
                            .document(childId)
                            .update(
                                mapOf(
                                    "fullName" to fullName
                                )
                            )
                            .addOnSuccessListener {
                                Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Error updating profile: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                            .addOnCompleteListener {
                                isSaving = false
                            }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE0852D)
                ),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Save Changes")
                }
            }
        }
    }
}
