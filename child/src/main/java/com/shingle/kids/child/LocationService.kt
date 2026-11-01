package com.shingle.kids.child

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.AMapUtils
import com.amap.api.maps.model.LatLng
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class LocationService : Service(), AMapLocationListener {

    private var locationClient: AMapLocationClient? = null
    private val CHANNEL_ID = "LocationServiceChannel"
    private var lastAlertTime = 0L

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var isGuardianActive = false
    private var isTestMode = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else 0

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, createNotification("守护服务已就绪"), type)
            } else {
                startForeground(1, createNotification("守护服务已就绪"))
            }
        } catch (e: Exception) {
            Log.e("LocationService", "Foreground error: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "START_GUARDIAN") {
            initLocation()
        } else if (action == "STOP_GUARDIAN") {
            stopGuardian()
        }
        return START_STICKY
    }

    private fun initLocation() {
        if (locationClient != null) return
        AMapLocationClient.updatePrivacyShow(applicationContext, true, true)
        AMapLocationClient.updatePrivacyAgree(applicationContext, true)

        locationClient = AMapLocationClient(applicationContext)
        val option = AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            interval = 10000
            isNeedAddress = true
        }
        locationClient?.setLocationOption(option)
        locationClient?.setLocationListener(this)
        locationClient?.startLocation()

        isGuardianActive = true
        isTestMode = false
        getSharedPreferences("state", MODE_PRIVATE).edit().putBoolean("is_guardian_active", true).apply()
        updateNotification("【正在实时守护中】")
    }

    private fun stopGuardian() {
        locationClient?.stopLocation()
        locationClient?.onDestroy()
        locationClient = null
        isGuardianActive = false
        getSharedPreferences("state", MODE_PRIVATE).edit().putBoolean("is_guardian_active", false).apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onLocationChanged(location: AMapLocation?) {
        if (!isGuardianActive || isTestMode || location == null || location.errorCode != 0) return

        // 异步上报位置到后端
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
                val pairingCode = prefs.getString("pairing_code", "888888") ?: "888888"
                com.shingle.kids.core.api.ApiService.instance.reportLocation(
                    com.shingle.kids.core.api.LocationReportRequest(
                        pairingCode = pairingCode,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        address = location.address ?: "未知地址",
                        batteryLevel = 100
                    )
                )
            } catch (e: Exception) {
                Log.e("LocationService", "Report location error: ${e.message}")
            }
        }

        val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
        val targetLat = prefs.getFloat("lat", 0f).toDouble()
        val targetLng = prefs.getFloat("lng", 0f).toDouble()
        val safeDistance = prefs.getFloat("distance", 500f)

        if (targetLat == 0.0) return

        val distance = AMapUtils.calculateLineDistance(
            LatLng(location.latitude, location.longitude),
            LatLng(targetLat, targetLng)
        )

        if (distance > safeDistance) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastAlertTime > 60000) {
                lastAlertTime = currentTime
                val phone = prefs.getString("phone", "") ?: ""
                val address = location.address ?: "未知位置"
                if (phone.isNotEmpty()) {
                    try {
                        val smsManager = getSystemService(SmsManager::class.java)
                        smsManager.sendTextMessage(phone, null, "【安全警告】孩子已离开安全区域！当前位置：$address", null, null)
                    } catch (e: Exception) {
                        Log.e("LocationService", "SMS error: ${e.message}")
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "儿童安全守护后台服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("儿童安全守护(孩子端)")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        locationClient?.stopLocation()
        locationClient?.onDestroy()
        super.onDestroy()
    }
}
