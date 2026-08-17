package com.offlinemesh.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.offlinemesh.app.core.model.DeliveryStatus
import com.offlinemesh.app.ui.theme.MeshAmber
import com.offlinemesh.app.ui.theme.MeshEmerald
import com.offlinemesh.app.ui.theme.MeshRose

@Composable
fun DeliveryStatusIcon(
    status: DeliveryStatus,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp
) {
    when (status) {
        DeliveryStatus.PENDING -> {
            Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = "Pending",
                tint = Color.Gray,
                modifier = modifier.size(size)
            )
        }
        DeliveryStatus.SENDING -> {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = "Sending",
                tint = MeshAmber,
                modifier = modifier.size(size)
            )
        }
        DeliveryStatus.SENT -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Sent",
                tint = Color.LightGray,
                modifier = modifier.size(size)
            )
        }
        DeliveryStatus.DELIVERED -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Delivered",
                tint = MeshEmerald,
                modifier = modifier.size(size)
            )
        }
        DeliveryStatus.FAILED -> {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Failed",
                tint = MeshRose,
                modifier = modifier.size(size)
            )
        }
    }
}
