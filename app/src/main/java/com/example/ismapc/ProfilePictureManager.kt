package com.example.ismapc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class ProfilePictureManager(private val context: Context) {
    private val profilePicturesDir: File
        get() = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "profile_pictures")

    init {
        // Create the directory if it doesn't exist
        if (!profilePicturesDir.exists()) {
            profilePicturesDir.mkdirs()
        }
    }

    fun saveProfilePicture(bitmap: Bitmap, userId: String): String? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "profile_${userId}_$timeStamp.jpg"
        val file = File(profilePicturesDir, fileName)

        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    fun getProfilePicturePath(userId: String): String? {
        val files = profilePicturesDir.listFiles { file ->
            file.name.startsWith("profile_${userId}_")
        }
        return files?.maxByOrNull { it.lastModified() }?.absolutePath
    }

    fun getProfilePictureBitmap(userId: String): Bitmap? {
        val path = getProfilePicturePath(userId) ?: return null
        return BitmapFactory.decodeFile(path)
    }

    fun deleteProfilePicture(userId: String): Boolean {
        val files = profilePicturesDir.listFiles { file ->
            file.name.startsWith("profile_${userId}_")
        }
        return files?.all { it.delete() } ?: false
    }

    fun getProfilePictureUri(userId: String): Uri? {
        val path = getProfilePicturePath(userId) ?: return null
        val file = File(path)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
} 