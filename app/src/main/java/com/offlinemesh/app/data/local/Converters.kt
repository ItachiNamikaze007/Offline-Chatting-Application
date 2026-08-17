package com.offlinemesh.app.data.local

import androidx.room.TypeConverter
import com.offlinemesh.app.core.model.DeliveryStatus

class Converters {
    @TypeConverter
    fun fromDeliveryStatus(status: DeliveryStatus): String = status.name

    @TypeConverter
    fun toDeliveryStatus(value: String): DeliveryStatus {
        return try {
            DeliveryStatus.valueOf(value)
        } catch (e: Exception) {
            DeliveryStatus.PENDING
        }
    }
}
