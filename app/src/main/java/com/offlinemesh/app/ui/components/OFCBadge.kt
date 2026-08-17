package com.offlinemesh.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinemesh.app.ui.theme.MeshCyan

@Composable
fun OFCBadge(
    userId: String,
    modifier: Modifier = Modifier,
    enableCopy: Boolean = true
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MeshCyan.copy(alpha = 0.12f))
            .then(
                if (enableCopy) {
                    Modifier.clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("OfflineMesh ID", userId)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "ID copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                } else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = userId,
            color = MeshCyan,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        if (enableCopy) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy ID",
                tint = MeshCyan.copy(alpha = 0.8f),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
