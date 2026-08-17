package com.offlinemesh.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinemesh.app.ui.theme.MeshEmerald

@Composable
fun AvatarBadge(
    name: String,
    avatarColorHex: String?,
    modifier: Modifier = Modifier,
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

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
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
