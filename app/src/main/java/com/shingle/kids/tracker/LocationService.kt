package com.shingle.kids.tracker

import android.R
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

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

    override fun onLocationChanged(location: AMapLocation?) {
        if (!isGuardianActive || isTestMode || location == null || location.errorCode != 0) return

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
                val phoneNumber = prefs.getString("phone", "") ?: ""
                performDirectAlert(location, distance.roundToInt(), phoneNumber, prefs)
                if (prefs.getBoolean("alert_record", true)) startRecording()
            }
        } else {
            if (isRecording) stopRecording()
        }
    }

    private fun startRecording(retryCount: Int = 0) {
        if (isRecording) return
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("LocationService", "No RECORD_AUDIO permission")
            return
        }

        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val musicDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            val file = File(musicDir, "KIDS_REC_$timeStamp.mp4")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setAudioChannels(1)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            Log.d("LocationService", "Recording SUCCESS: ${file.absolutePath}")
            updateNotification(if(isTestMode) "测试录音中..." else "检测到异常：正在录音")
        } catch (e: Exception) {
            Log.e("LocationService", "Recording FAILED (Retry $retryCount): ${e.message}")
            if (retryCount < 3) {
                Handler(Looper.getMainLooper()).postDelayed({ startRecording(retryCount + 1) }, 1000)
            }
            isRecording = false
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
            mediaRecorder = null
            isRecording = false
            Log.d("LocationService", "Recording STOPPED")
        } catch (e: Exception) { Log.e("LocationService", "Stop Rec error: ${e.message}") }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val cmd = intent?.getStringExtra("COMMAND")
        Log.d("LocationService", "Receive CMD: $cmd")
        when (cmd) {
            "START_GUARDIAN" -> initLocation()
            "TEST_RECORD" -> {
                isTestMode = true
                isGuardianActive = false
                updateNotification("测试模式：录音验证")
                startRecording()
                Timer().schedule(object : TimerTask() {
                    override fun run() {
                        stopRecording()
                        if (isTestMode) {
                            stopForeground(true)
                            stopSelf()
                        }
                    }
                }, 10000)
            }
            "STOP_SELF" -> {
                getSharedPreferences("state", MODE_PRIVATE).edit().putBoolean("is_guardian_active", false).apply()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun performDirectAlert(location: AMapLocation, distance: Int, phoneNumber: String, prefs: android.content.SharedPreferences) {
        if (phoneNumber.isEmpty()) return
        val geocoder = GeocodeSearch(this)
        val query = RegeocodeQuery(LatLonPoint(location.latitude, location.longitude), 200f, GeocodeSearch.AMAP)
        geocoder.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
            override fun onRegeocodeSearched(result: RegeocodeResult, rCode: Int) {
                val address = if (rCode == 1000) result.regeocodeAddress.formatAddress else "位置解析中"
                val content = "【报警】孩子离开安全区 ${distance}米！位置：$address"
                if (prefs.getBoolean("alert_sms", false)) sendSms(phoneNumber, content)
                if (prefs.getBoolean("alert_call", true)) makeCall(phoneNumber)
            }
            override fun onGeocodeSearched(result: GeocodeResult, rCode: Int) {}
        })
        geocoder.getFromLocationAsyn(query)
    }

    private fun makeCall(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        try { startActivity(intent) } catch (e: Exception) { 
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) 
        }
    }

    private fun sendSms(phoneNumber: String, content: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) this.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
            smsManager?.sendTextMessage(phoneNumber, null, content, null, null)
        } catch (e: Exception) { Log.e("LocationService", "SMS error: ${e.message}") }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(NotificationChannel(CHANNEL_ID, "位置守护服务", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("儿童安全守护")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_menu_mylocation)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, createNotification(content))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d("LocationService", "Service Destroyed")
        isGuardianActive = false
        if (isRecording) stopRecording()
        locationClient?.stopLocation()
        locationClient?.unRegisterLocationListener(this)
        locationClient?.onDestroy()
        locationClient = null
        super.onDestroy()
    }
}