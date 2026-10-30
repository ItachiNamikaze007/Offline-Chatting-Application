package com.offlinemesh.app.core.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object AvatarStorageHelper {

    private const val AVATARS_DIR = "avatars"
    private const val SELF_AVATAR_FILENAME = "self_avatar.jpg"
    private const val MAX_DIMENSION = 128
    private const val JPEG_QUALITY = 75

    fun getAvatarsDirectory(context: Context): File {
        val dir = File(context.filesDir, AVATARS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun saveSelfAvatarFromUri(context: Context, imageUri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null

            val croppedAndScaled = processAndScaleBitmap(originalBitmap)
            val dir = getAvatarsDirectory(context)
            val outputFile = File(dir, SELF_AVATAR_FILENAME)

            val outputStream = FileOutputStream(outputFile)
            croppedAndScaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            outputStream.flush()
            outputStream.close()

            outputFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun savePeerAvatarFromBase64(context: Context, peerUserId: String, base64Data: String): String? {
        return try {
            val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null
            val cropped = processAndScaleBitmap(bitmap)

            val sanitizedId = peerUserId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val dir = getAvatarsDirectory(context)
            val outputFile = File(dir, "peer_$sanitizedId.jpg")

            val outputStream = FileOutputStream(outputFile)
            cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            outputStream.flush()
            outputStream.close()

            outputFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun getSelfAvatarBase64(context: Context): String? {
        return try {
            val dir = getAvatarsDirectory(context)
            val file = File(dir, SELF_AVATAR_FILENAME)
            if (!file.exists()) return null

            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    fun deleteSelfAvatar(context: Context) {
        try {
            val dir = getAvatarsDirectory(context)
            val file = File(dir, SELF_AVATAR_FILENAME)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
        }
    }

    private fun processAndScaleBitmap(src: Bitmap): Bitmap {
        // Crop to square centered
        val size = min(src.width, src.height)
        val x = (src.width - size) / 2
        val y = (src.height - size) / 2
        val square = Bitmap.createBitmap(src, x, y, size, size)

        // Scale to max dimension
        return if (size > MAX_DIMENSION) {
            Bitmap.createScaledBitmap(square, MAX_DIMENSION, MAX_DIMENSION, true)
        } else {
            square
        }
    }
}
