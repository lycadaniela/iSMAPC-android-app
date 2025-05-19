package com.example.ismapc

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ismapc.ui.theme.ISMAPCTheme
import com.google.firebase.auth.FirebaseAuth

class ChangePasswordActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChangePasswordScreen(
                        onPasswordChanged = {
                            Toast.makeText(this, "Password changed successfully", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    onPasswordChanged: () -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    var currentPasswordError by remember { mutableStateOf(false) }
    var newPasswordError by remember { mutableStateOf(false) }
    var confirmPasswordError by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Back button row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(
                onClick = { (context as? ComponentActivity)?.finish() }
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Title
        Text(
            text = "Change Password",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Current Password
        OutlinedTextField(
            value = currentPassword,
            onValueChange = { 
                currentPassword = it
                currentPasswordError = false
            },
            label = { Text("Current Password") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (currentPasswordError) 4.dp else 16.dp),
            singleLine = true,
            isError = currentPasswordError,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            )
        )

        if (currentPasswordError) {
            Text(
                text = "Please enter your current password",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, bottom = 16.dp)
            )
        }

        // New Password
        OutlinedTextField(
            value = newPassword,
            onValueChange = { 
                newPassword = it
                newPasswordError = false
                if (confirmPassword.isNotEmpty()) {
                    confirmPasswordError = newPassword != confirmPassword
                }
            },
            label = { Text("New Password") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (newPasswordError) 4.dp else 16.dp),
            singleLine = true,
            isError = newPasswordError,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            )
        )

        if (newPasswordError) {
            Text(
                text = "Password must be at least 6 characters",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, bottom = 16.dp)
            )
        }

        // Confirm New Password
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { 
                confirmPassword = it
                confirmPasswordError = newPassword != it
            },
            label = { Text("Confirm New Password") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (confirmPasswordError) 4.dp else 32.dp),
            singleLine = true,
            isError = confirmPasswordError,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            )
        )

        if (confirmPasswordError) {
            Text(
                text = "Passwords do not match",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, bottom = 16.dp)
            )
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
                    
                    // First reauthenticate the user
                    val user = auth.currentUser
                    val credential = com.google.firebase.auth.EmailAuthProvider
                        .getCredential(user?.email ?: "", currentPassword)
                    
                    user?.reauthenticate(credential)
                        ?.addOnCompleteListener { reauthTask ->
                            if (reauthTask.isSuccessful) {
                                // Then update the password
                                user.updatePassword(newPassword)
                                    .addOnCompleteListener { updateTask ->
                                        isLoading = false
                                        if (updateTask.isSuccessful) {
                                            onPasswordChanged()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Failed to update password: ${updateTask.exception?.message}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                            } else {
                                isLoading = false
                                Toast.makeText(
                                    context,
                                    "Current password is incorrect",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
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