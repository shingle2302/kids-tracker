package com.shingle.kids.core.model

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val timestamp: Long,
    val batteryLevel: Int
)

data class DeviceCommand(
    val commandId: String,
    val type: String, // "LOCATE", "RECORD", "ALERT"
    val timestamp: Long
)
