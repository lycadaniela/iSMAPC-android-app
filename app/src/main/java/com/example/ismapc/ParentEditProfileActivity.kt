package com.example.ismapc

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ismapc.ui.theme.ISMAPCTheme
import com.google.firebase.firestore.FirebaseFirestore

class ParentEditProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val parentId = intent.getStringExtra("parentId") ?: ""
        val parentName = intent.getStringExtra("parentName") ?: ""
        val parentEmail = intent.getStringExtra("parentEmail") ?: ""
        val parentPhone = intent.getStringExtra("parentPhone") ?: ""

        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ParentEditProfileScreen(
                        parentId = parentId,
                        initialName = parentName,
                        initialEmail = parentEmail,
                        initialPhone = parentPhone,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentEditProfileScreen(
    parentId: String,
    initialName: String,
    initialEmail: String,
    initialPhone: String,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var isSaving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (name.isNotBlank() && phone.isNotBlank()) {
                                isSaving = true
                                val updates = hashMapOf(
                                    "fullName" to name,
                                    "phoneNumber" to phone
                                ) as Map<String, Any>

                                firestore.collection(MainActivity.USERS_COLLECTION)
                                    .document(MainActivity.PARENTS_COLLECTION)
                                    .collection(parentId)
                                    .document(MainActivity.PROFILE_DOCUMENT)
                                    .update(updates)
                                    .addOnSuccessListener {
                                        Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                                        isSaving = false
                                        onBack()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(context, "Error updating profile: ${e.message}", Toast.LENGTH_SHORT).show()
                                        isSaving = false
                                    }
                            } else {
                                Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save"
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            )

            OutlinedTextField(
                value = initialEmail,
                onValueChange = { },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                enabled = false
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            )
        }
    }
} 