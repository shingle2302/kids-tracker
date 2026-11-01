package com.shingle.kids.backend.controller

import com.shingle.kids.backend.model.*
import com.shingle.kids.backend.repository.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.util.UUID

data class AuthRequest(val username: String, val passwordHash: String, val role: String)
data class SubscribeRequest(val pairingCode: String, val planType: String)
data class PushTokenRequest(val pairingCode: String, val fcmToken: String, val deviceType: String)

@RestController
@RequestMapping("/api/v1")
class CommercialController(
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val pushTokenRepository: PushTokenRepository
) {

    @PostMapping("/auth/register")
    fun register(@RequestBody req: AuthRequest): ResponseEntity<Any> {
        if (userRepository.findByUsername(req.username) != null) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Username already exists"))
        }
        val token = UUID.randomUUID().toString()
        val user = UserEntity(username = req.username, passwordHash = req.passwordHash, role = req.role, token = token)
        userRepository.save(user)
        return ResponseEntity.ok(mapOf("status" to "success", "token" to token))
    }

    @PostMapping("/auth/login")
    fun login(@RequestBody req: AuthRequest): ResponseEntity<Any> {
        val user = userRepository.findByUsername(req.username) ?: return ResponseEntity.status(401).body(mapOf("error" to "User not found"))
        if (user.passwordHash != req.passwordHash) {
            return ResponseEntity.status(401).body(mapOf("error" to "Invalid password"))
        }
        val token = user.token ?: UUID.randomUUID().toString().also { user.token = it; userRepository.save(user) }
        return ResponseEntity.ok(mapOf("status" to "success", "token" to token, "role" to user.role))
    }

    @GetMapping("/subscription/{pairingCode}")
    fun getSubscription(@PathVariable pairingCode: String): ResponseEntity<Any> {
        val sub = subscriptionRepository.findByPairingCode(pairingCode) ?: SubscriptionEntity(pairingCode = pairingCode, isVip = false)
        return ResponseEntity.ok(sub)
    }

    @PostMapping("/subscription/subscribe")
    fun subscribe(@RequestBody req: SubscribeRequest): ResponseEntity<Any> {
        var sub = subscriptionRepository.findByPairingCode(req.pairingCode)
        val expire = if (req.planType == "YEARLY") LocalDateTime.now().plusYears(1) else LocalDateTime.now().plusMonths(1)
        if (sub == null) {
            sub = SubscriptionEntity(pairingCode = req.pairingCode, isVip = true, planType = req.planType, expireDate = expire)
        } else {
            sub.isVip = true
            sub.planType = req.planType
            sub.expireDate = expire
        }
        subscriptionRepository.save(sub)
        return ResponseEntity.ok(mapOf("status" to "success", "message" to "Subscription activated successfully"))
    }

    @PostMapping("/push/register")
    fun registerPushToken(@RequestBody req: PushTokenRequest): ResponseEntity<Any> {
        var tokenEntity = pushTokenRepository.findByPairingCodeAndDeviceType(req.pairingCode, req.deviceType)
        if (tokenEntity == null) {
            tokenEntity = PushTokenEntity(pairingCode = req.pairingCode, fcmToken = req.fcmToken, deviceType = req.deviceType)
        }
        pushTokenRepository.save(tokenEntity)
        return ResponseEntity.ok(mapOf("status" to "success"))
    }
}
