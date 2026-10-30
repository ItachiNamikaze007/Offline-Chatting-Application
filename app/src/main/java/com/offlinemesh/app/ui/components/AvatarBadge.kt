package com.offlinemesh.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinemesh.app.ui.theme.MeshEmerald
import java.io.File

@Composable
fun AvatarBadge(
    name: String,
    avatarColorHex: String?,
    modifier: Modifier = Modifier,
    imagePath: String? = null,
    size: Dp = 44.dp,
    isOnline: Boolean = false
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val primaryColor = MaterialTheme.colorScheme.primary
    val color = remember(avatarColorHex, primaryColor) {
        if (!avatarColorHex.isNullOrEmpty()) {
            try {
                Color(android.graphics.Color.parseColor(avatarColorHex))
            } catch (e: Exception) {
                primaryColor
            }
        } else {
            primaryColor
        }
    }

    val imageBitmap = remember(imagePath) {
        if (!imagePath.isNullOrEmpty()) {
            try {
                val file = File(imagePath)
                if (file.exists() && file.length() > 0) {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } else null
            } catch (_: Exception) {
                null
            }
        } else null
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = Color.White,
                    fontSize = (size.value * 0.45).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(size * 0.3f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MeshEmerald)
            )
        }
    }
}
