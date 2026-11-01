package com.shingle.kids.backend.repository

import com.shingle.kids.backend.model.*
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findByUsername(username: String): UserEntity?
}

interface SubscriptionRepository : JpaRepository<SubscriptionEntity, Long> {
    fun findByPairingCode(pairingCode: String): SubscriptionEntity?
}

interface PushTokenRepository : JpaRepository<PushTokenEntity, Long> {
    fun findByPairingCodeAndDeviceType(pairingCode: String, deviceType: String): PushTokenEntity?
}
