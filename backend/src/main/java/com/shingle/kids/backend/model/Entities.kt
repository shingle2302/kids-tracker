package com.shingle.kids.backend.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "devices")
data class DeviceEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val pairingCode: String,
    val childName: String,
    val parentPhone: String,
    var lastLatitude: Double = 0.0,
    var lastLongitude: Double = 0.0,
    var lastAddress: String = "",
    var batteryLevel: Int = 100,
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

@Entity
@Table(name = "commands")
data class CommandEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val pairingCode: String,
    val commandType: String, // "LOCATE", "RECORD", "ALERT"
    var status: String = "PENDING", // "PENDING", "SENT", "ACKNOWLEDGED"
    val createdAt: LocalDateTime = LocalDateTime.now()
)
