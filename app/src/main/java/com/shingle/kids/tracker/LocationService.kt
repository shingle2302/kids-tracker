package com.shingle.kids.tracker

import android.R
import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
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
import kotlin.math.roundToInt

class LocationService : Service(), AMapLocationListener {

    private var locationClient: AMapLocationClient? = null
    private val CHANNEL_ID = "LocationServiceChannel"
    private var lastAlertTime = 0L

    override fun onCreate() {
        super.onCreate()
        AMapLocationClient.updatePrivacyShow(applicationContext, true, true)
        AMapLocationClient.updatePrivacyAgree(applicationContext, true)
        createNotificationChannel()
        startForeground(1, createNotification())
        
        try {
            initLocation()
        } catch (e: Exception) {
            Log.e("LocationService", "Init location failed: ${e.message}")
        }
    }

    private fun initLocation() {
        locationClient = AMapLocationClient(applicationContext)
        val option = AMapLocationClientOption()
        option.locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
        option.interval = 10000 // 10秒检测一次
        option.isNeedAddress = true
        locationClient?.setLocationOption(option)
        locationClient?.setLocationListener(this)
        locationClient?.startLocation()
    }

    override fun onLocationChanged(location: AMapLocation?) {
        if (location == null) return
        
        if (location.errorCode != 0) {
            Log.e("LocationService", "定位失败! 错误码: ${location.errorCode}")
            return
        }

        val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
        val targetLat = prefs.getFloat("lat", 0f).toDouble()
        val targetLng = prefs.getFloat("lng", 0f).toDouble()
        val safeDistance = prefs.getFloat("distance", 500f)
        val phoneNumber = prefs.getString("phone", "") ?: ""

        if (targetLat == 0.0 || phoneNumber.isEmpty()) return

        val distance = AMapUtils.calculateLineDistance(
            LatLng(location.latitude, location.longitude),
            LatLng(targetLat, targetLng)
        )

        // 调试日志：确认后台在运行
        Log.d("LocationService", "距离中心: ${distance.roundToInt()}米, 安全半径: ${safeDistance}米")

        if (distance > safeDistance) {
            val currentTime = System.currentTimeMillis()
            // 报警冷却：每分钟最多报警一次
            if (currentTime - lastAlertTime > 60000) {
                lastAlertTime = currentTime
                performDirectAlert(location, distance.roundToInt(), phoneNumber, prefs)
            }
        }
    }

    private fun performDirectAlert(location: AMapLocation, distance: Int, phoneNumber: String, prefs: android.content.SharedPreferences) {
        val geocoder = GeocodeSearch(this)
        val query = RegeocodeQuery(LatLonPoint(location.latitude, location.longitude), 200f, GeocodeSearch.AMAP)
        
        geocoder.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
            override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
                val address = if (rCode == 1000 && result?.regeocodeAddress != null) {
                    result.regeocodeAddress.formatAddress
                } else {
                    location.address ?: "坐标(${location.latitude},${location.longitude})"
                }

                val content = "【儿童守护报警】孩子已离开安全区域 ${distance}米！孩子位置：$address"
                val alertCall = prefs.getBoolean("alert_call", true)
                val alertSms = prefs.getBoolean("alert_sms", false)

                Log.d("LocationService", "触发自动报警: 拨号=$alertCall, 短信=$alertSms")

                // 直接执行，不再通过应用内通知确认
                if (alertSms) sendSms(phoneNumber, content)
                if (alertCall) makeCall(phoneNumber)
            }
            override fun onGeocodeSearched(result: GeocodeResult?, rCode: Int) {}
        })
        geocoder.getFromLocationAsyn(query)
    }

    private fun makeCall(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_CALL)
        intent.data = Uri.parse("tel:$phoneNumber")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // 回退到拨号盘
            val dialIntent = Intent(Intent.ACTION_DIAL)
            dialIntent.data = Uri.parse("tel:$phoneNumber")
            dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(dialIntent)
        }
    }

    private fun sendSms(phoneNumber: String, content: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                this.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            // 发送短信：如果系统弹窗，请在手机设置中将该应用的短信权限设为“始终允许”
            smsManager?.sendTextMessage(phoneNumber, null, content, null, null)
            Log.d("LocationService", "短信已提交发送队列")
        } catch (e: Exception) {
            Log.e("LocationService", "短信发送失败: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "位置守护服务", NotificationManager.IMPORTANCE_LOW
            ))
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("守护进行中")
            .setContentText("正在实时监测孩子位置安全")
            .setSmallIcon(R.drawable.ic_menu_mylocation)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        locationClient?.stopLocation()
        locationClient?.onDestroy()
    }
}
