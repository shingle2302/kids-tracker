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
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.AMapUtils
import com.amap.api.maps.model.LatLng
import java.io.File
import java.util.*
import kotlinx.coroutines.*

class LocationService : Service(), AMapLocationListener {

    private var locationClient: AMapLocationClient? = null
    private val CHANNEL_ID = "LocationServiceChannel"
    private var lastAlertTime = 0L

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var isGuardianActive = false
    private var currentIntervalMs = 10000L
    private var lastLocation: AMapLocation? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else 0

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, createNotification("守护服务运行中，实时保护您的安全"), type)
            } else {
                startForeground(1, createNotification("守护服务运行中，实时保护您的安全"))
            }
        } catch (e: Exception) {
            Log.e("LocationService", "Foreground error: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "START_GUARDIAN" || action == null) {
            initLocation()
        } else if (action == "STOP_GUARDIAN") {
            stopGuardian()
        }
        return START_STICKY
    }

    private fun initLocation() {
        if (isGuardianActive) return
        AMapLocationClient.updatePrivacyShow(applicationContext, true, true)
        AMapLocationClient.updatePrivacyAgree(applicationContext, true)

        val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
        currentIntervalMs = prefs.getLong("report_interval_ms", 10000L)

        try {
            locationClient = AMapLocationClient(applicationContext).apply {
                val option = AMapLocationClientOption().apply {
                    locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                    interval = currentIntervalMs
                    isNeedAddress = true
                }
                setLocationOption(option)
                setLocationListener(this@LocationService)
                startLocation()
            }
        } catch (e: Exception) {
            Log.e("LocationService", "Init AMap client error: ${e.message}")
        }

        isGuardianActive = true
        getSharedPreferences("state", MODE_PRIVATE).edit().putBoolean("is_guardian_active", true).apply()
        updateNotification("【守护进行中 - 自动刷新频率: ${currentIntervalMs / 1000}s】")

        startAutoReportLoop()
        startCommandPoller()
    }

    private fun startAutoReportLoop() {
        serviceScope.launch {
            while (isActive && isGuardianActive) {
                try {
                    val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
                    var pairingCode = prefs.getString("pairing_code", null)
                    if (pairingCode.isNullOrEmpty()) {
                        pairingCode = (100000..999999).random().toString()
                        prefs.edit().putString("pairing_code", pairingCode).apply()
                    }
                    val serverUrl = prefs.getString("server_url", "http://192.168.100.101:8080/") ?: "http://192.168.100.101:8080/"

                    val loc = lastLocation ?: locationClient?.lastKnownLocation
                    val lat = loc?.latitude ?: 39.9087
                    val lng = loc?.longitude ?: 116.3975
                    val addr = if (loc != null && !loc.address.isNullOrEmpty()) loc.address else "定位正常守护中"

                    val api = com.shingle.kids.core.api.ApiService.getInstance(serverUrl)
                    val res = api.reportLocation(
                        com.shingle.kids.core.api.LocationReportRequest(
                            pairingCode = pairingCode,
                            latitude = lat,
                            longitude = lng,
                            address = addr,
                            batteryLevel = getBatteryLevel()
                        )
                    )
                    if (res.isSuccessful) {
                        Log.d("LocationService", "Auto location report success for $pairingCode at ${Date()}")
                    }
                } catch (e: Exception) {
                    Log.e("LocationService", "Auto report error: ${e.message}")
                }
                delay(currentIntervalMs)
            }
        }
    }

    private fun startCommandPoller() {
        serviceScope.launch {
            while (isActive && isGuardianActive) {
                try {
                    val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
                    val pairingCode = prefs.getString("pairing_code", "888888") ?: "888888"
                    val serverUrl = prefs.getString("server_url", "http://192.168.100.101:8080/") ?: "http://192.168.100.101:8080/"
                    val api = com.shingle.kids.core.api.ApiService.getInstance(serverUrl)
                    val res = api.getDeviceStatus(pairingCode)
                    if (res.isSuccessful) {
                        val commands = res.body()?.pendingCommands ?: emptyList()
                        for (cmd in commands) {
                            if (cmd.commandType.startsWith("INTERVAL_")) {
                                val newInterval = cmd.commandType.removePrefix("INTERVAL_").toLongOrNull()
                                if (newInterval != null && newInterval != currentIntervalMs) {
                                    currentIntervalMs = newInterval
                                    prefs.edit().putLong("report_interval_ms", newInterval).apply()
                                    updateLocationInterval(newInterval)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LocationService", "Poller error: ${e.message}")
                }
                delay(15000)
            }
        }
    }

    private fun updateLocationInterval(newIntervalMs: Long) {
        locationClient?.let { client ->
            val option = AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                interval = newIntervalMs
                isNeedAddress = true
            }
            client.setLocationOption(option)
            updateNotification("【守护进行中 - 自动刷新频率: ${newIntervalMs / 1000}s】")
        }
    }

    private fun stopGuardian() {
        serviceScope.cancel()
        locationClient?.stopLocation()
        locationClient?.onDestroy()
        locationClient = null
        isGuardianActive = false
        getSharedPreferences("state", MODE_PRIVATE).edit().putBoolean("is_guardian_active", false).apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun getBatteryLevel(): Int {
        return try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        } catch (e: Exception) {
            100
        }
    }

    private fun startAutoRecording() {
        if (isRecording) return
        try {
            val audioFile = File(filesDir, "alert_record_${System.currentTimeMillis()}.3gp")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(applicationContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            Log.d("LocationService", "Auto-recording started: ${audioFile.absolutePath}")

            Handler(Looper.getMainLooper()).postDelayed({
                stopAutoRecording()
            }, 30000)
        } catch (e: Exception) {
            Log.e("LocationService", "MediaRecorder error: ${e.message}")
            isRecording = false
        }
    }

    private fun stopAutoRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            Log.d("LocationService", "Auto-recording stopped.")
        } catch (e: Exception) {
            Log.e("LocationService", "Stop recorder error: ${e.message}")
            isRecording = false
        }
    }

    override fun onLocationChanged(location: AMapLocation?) {
        if (location == null || location.errorCode != 0) return
        lastLocation = location

        if (!isGuardianActive) return

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
                val phone = prefs.getString("parent_phone", "") ?: prefs.getString("phone", "") ?: ""
                val address = location.address ?: "未知位置"

                if (phone.isNotEmpty()) {
                    // 1. 自动短信
                    try {
                        val smsManager = getSystemService(SmsManager::class.java)
                        smsManager.sendTextMessage(phone, null, "【安全警告】孩子已离开安全区域！当前位置：$address", null, null)
                    } catch (e: Exception) {
                        Log.e("LocationService", "SMS error: ${e.message}")
                    }

                    // 2. 自动拨号
                    try {
                        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(callIntent)
                    } catch (e: Exception) {
                        Log.e("LocationService", "Call error: ${e.message}")
                    }
                }

                // 3. 自动录音
                startAutoRecording()
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
        serviceScope.cancel()
        stopAutoRecording()
        locationClient?.stopLocation()
        locationClient?.onDestroy()
        super.onDestroy()
    }
}
