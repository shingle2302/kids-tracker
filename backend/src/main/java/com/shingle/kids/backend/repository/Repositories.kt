package com.shingle.kids.backend.repository

import com.shingle.kids.backend.model.CommandEntity
import com.shingle.kids.backend.model.DeviceEntity
import org.springframework.data.jpa.repository.JpaRepository

interface DeviceRepository : JpaRepository<DeviceEntity, Long> {
    fun findByPairingCode(pairingCode: String): DeviceEntity?
}

interface CommandRepository : JpaRepository<CommandEntity, Long> {
    fun findByPairingCodeAndStatus(pairingCode: String, status: String): List<CommandEntity>
}
