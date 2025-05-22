package com.example.ismapc

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ismapc.ui.theme.ISMAPCTheme
import com.google.firebase.auth.FirebaseAuth

class ChangePasswordActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChangePasswordScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen() {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showCurrentPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var currentPasswordError by remember { mutableStateOf(false) }
    var newPasswordError by remember { mutableStateOf(false) }
    var confirmPasswordError by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
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
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Change Password",
                        modifier = Modifier.size(48.dp),
                        tint = Color(0xFFE0852D)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Change Password",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFE0852D)
                    )
                    Text(
                        text = "Update your account password",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD6D7D3)
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
                        value = currentPassword,
                        onValueChange = { 
                            currentPassword = it
                            currentPasswordError = false
                        },
                        label = { Text("Current Password") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFD6D7D3)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { showCurrentPassword = !showCurrentPassword }) {
                                Icon(
                                    imageVector = if (showCurrentPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (showCurrentPassword) "Hide password" else "Show password",
                                    tint = Color(0xFFE0852D)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isError = currentPasswordError,
                        visualTransformation = if (showCurrentPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (currentPasswordError) {
                        Text(
                            text = "Please enter your current password",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { 
                            newPassword = it
                            newPasswordError = false
                            if (confirmPassword.isNotEmpty()) {
                                confirmPasswordError = it != confirmPassword
                            }
                        },
                        label = { Text("New Password") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFD6D7D3)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { showNewPassword = !showNewPassword }) {
                                Icon(
                                    imageVector = if (showNewPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (showNewPassword) "Hide password" else "Show password",
                                    tint = Color(0xFFE0852D)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isError = newPasswordError,
                        visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (newPasswordError) {
                        Text(
                            text = "Password must be at least 6 characters",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { 
                            confirmPassword = it
                            confirmPasswordError = it != newPassword
                        },
                        label = { Text("Confirm New Password") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFD6D7D3)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                                Icon(
                                    imageVector = if (showConfirmPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (showConfirmPassword) "Hide password" else "Show password",
                                    tint = Color(0xFFE0852D)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isError = confirmPasswordError,
                        visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (confirmPasswordError) {
                        Text(
                            text = "Passwords do not match",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }

            // Change Password Button
            Button(
                onClick = {
                    // Validate all fields
                    currentPasswordError = currentPassword.isBlank()
                    newPasswordError = newPassword.length < 6
                    confirmPasswordError = newPassword != confirmPassword

                    if (!currentPasswordError && !newPasswordError && !confirmPasswordError) {
                        isLoading = true
                        val user = FirebaseAuth.getInstance().currentUser
                        val credential = com.google.firebase.auth.EmailAuthProvider
                            .getCredential(user?.email ?: "", currentPassword)

                        user?.reauthenticate(credential)
                            ?.addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    user.updatePassword(newPassword)
                                        .addOnCompleteListener { updateTask ->
                                            isLoading = false
                                            if (updateTask.isSuccessful) {
                                                Toast.makeText(context, "Password updated successfully", Toast.LENGTH_SHORT).show()
                                                (context as? ComponentActivity)?.finish()
                                            } else {
                                                Toast.makeText(context, "Failed to update password: ${updateTask.exception?.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                } else {
                                    isLoading = false
                                    Toast.makeText(context, "Current password is incorrect", Toast.LENGTH_SHORT).show()
                                }
                            }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE0852D)
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        "Change Password",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
} 