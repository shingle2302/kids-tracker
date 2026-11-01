package com.shingle.kids.backend.controller

import com.shingle.kids.backend.model.CommandEntity
import com.shingle.kids.backend.model.DeviceEntity
import com.shingle.kids.backend.repository.CommandRepository
import com.shingle.kids.backend.repository.DeviceRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

data class LocationReportRequest(
    val pairingCode: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val batteryLevel: Int
)

data class PairRequest(
    val pairingCode: String,
    val childName: String,
    val parentPhone: String
)

@RestController
@RequestMapping("/api/v1")
class DeviceController(
    private val deviceRepository: DeviceRepository,
    private val commandRepository: CommandRepository
) {

    @PostMapping("/device/pair")
    fun pairDevice(@RequestBody request: PairRequest): ResponseEntity<Any> {
        val existing = deviceRepository.findByPairingCode(request.pairingCode)
        if (existing != null) {
            return ResponseEntity.ok(mapOf("status" to "success", "message" to "设备已连接"))
        }
        val newDevice = DeviceEntity(
            pairingCode = request.pairingCode,
            childName = request.childName,
            parentPhone = request.parentPhone
        )
        deviceRepository.save(newDevice)
        return ResponseEntity.ok(mapOf("status" to "success", "message" to "配对成功"))
    }

    @PostMapping("/device/location")
    fun reportLocation(@RequestBody request: LocationReportRequest): ResponseEntity<Any> {
        val device = deviceRepository.findByPairingCode(request.pairingCode)
        if (device != null) {
            device.lastLatitude = request.latitude
            device.lastLongitude = request.longitude
            device.lastAddress = request.address
            device.batteryLevel = request.batteryLevel
            device.updatedAt = LocalDateTime.now()
            deviceRepository.save(device)
            return ResponseEntity.ok(mapOf("status" to "success"))
        }
        return ResponseEntity.status(404).body(mapOf("error" to "Device not found"))
    }

    @GetMapping("/device/{pairingCode}")
    fun getDeviceStatus(@PathVariable pairingCode: String): ResponseEntity<Any> {
        val device = deviceRepository.findByPairingCode(pairingCode) ?: return ResponseEntity.status(404).body(mapOf("error" to "Not found"))
        val pendingCommands = commandRepository.findByPairingCodeAndStatus(pairingCode, "PENDING")
        return ResponseEntity.ok(mapOf("device" to device, "pendingCommands" to pendingCommands))
    }

    @PostMapping("/command/send")
    fun sendCommand(@RequestParam pairingCode: String, @RequestParam type: String): ResponseEntity<Any> {
        val cmd = CommandEntity(pairingCode = pairingCode, commandType = type)
        commandRepository.save(cmd)
        return ResponseEntity.ok(mapOf("status" to "success", "commandId" to cmd.id))
    }
}
