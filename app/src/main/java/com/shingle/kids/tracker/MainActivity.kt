package com.shingle.kids.tracker

import android.Manifest
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.AMapUtils
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.shingle.kids.tracker.ui.theme.MyApplicationTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.SEND_SMS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        val appContext = applicationContext
        AMapLocationClient.updatePrivacyShow(appContext, true, true)
        AMapLocationClient.updatePrivacyAgree(appContext, true)
        MapsInitializer.updatePrivacyShow(appContext, true, true)
        MapsInitializer.updatePrivacyAgree(appContext, true)
        
        try {
            MapsInitializer.initialize(appContext)
        } catch (e: Throwable) {
            Log.e("AMapInit", "Init error: ${e.message}")
        }

        super.onCreate(savedInstanceState)
        
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (!results.all { it.value }) {
                Toast.makeText(this, "警告：请开启所有权限以保证守护功能正常工作", Toast.LENGTH_LONG).show()
            }
        }.launch(permissions)

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ConfigScreen()
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
        if (serviceClass.name == service.service.className) {
            return true
        }
    }
    return false
}

@Composable
fun ConfigScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, AdminReceiver::class.java)

    var targetLat by remember { mutableStateOf(if (prefs.contains("lat")) prefs.getFloat("lat", 0f).toDouble() else null) }
    var targetLng by remember { mutableStateOf(if (prefs.contains("lng")) prefs.getFloat("lng", 0f).toDouble() else null) }
    var currentLat by remember { mutableStateOf<Double?>(null) }
    var currentLng by remember { mutableStateOf<Double?>(null) }
    var currentAddr by remember { mutableStateOf("正在获取当前位置...") }
    var targetAddr by remember { mutableStateOf("未选择安全中心") }
    var distanceStr by remember { mutableStateOf(prefs.getFloat("distance", 500f).toString()) }
    var phoneNumber by remember { mutableStateOf(prefs.getString("phone", "") ?: "") }
    
    // 报警配置
    var alertCall by remember { mutableStateOf(prefs.getBoolean("alert_call", true)) }
    var alertSms by remember { mutableStateOf(prefs.getBoolean("alert_sms", false)) }

    var isConfigLocked by remember { mutableStateOf(true) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var inputPassword by remember { mutableStateOf("") }
    val defaultPassword = "123456"

    var isServiceActive by remember { mutableStateOf(isServiceRunning(context, LocationService::class.java)) }

    val performRegeocoding: (Double, Double, Boolean) -> Unit = { lat, lng, isTarget ->
        try {
            val geocoder = GeocodeSearch(context)
            val query = RegeocodeQuery(LatLonPoint(lat, lng), 200f, GeocodeSearch.AMAP)
            geocoder.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
                    if (rCode == 1000 && result != null) {
                        val address = result.regeocodeAddress.formatAddress
                        if (isTarget) {
                            targetAddr = address
                        } else {
                            currentAddr = address
                        }
                    }
                }
                override fun onGeocodeSearched(result: GeocodeResult?, rCode: Int) {}
            })
            geocoder.getFromLocationAsyn(query)
        } catch (e: Exception) {
            Log.e("Geo", "Error: ${e.message}")
        }
    }

    LaunchedEffect(Unit) {
        if (targetLat != null && targetLng != null) {
            performRegeocoding(targetLat!!, targetLng!!, true)
        }
    }

    val distance = remember(currentLat, currentLng, targetLat, targetLng) {
        if (currentLat != null && currentLng != null && targetLat != null && targetLng != null) {
            AMapUtils.calculateLineDistance(LatLng(currentLat!!, currentLng!!), LatLng(targetLat!!, targetLng!!)).roundToInt()
        } else null
    }

    Column(modifier = Modifier.safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("安全守护配置", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                Icon(Icons.Default.Notifications, contentDescription = "辅助功能", tint = Color.Gray)
            }
            IconButton(onClick = {
                if (isConfigLocked) showPasswordDialog = true else isConfigLocked = true
            }) {
                Icon(if (isConfigLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = "锁", tint = if (isConfigLocked) Color.Red else Color(0xFF4CAF50))
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = if (isServiceActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("状态: ${if (isServiceActive) "正在实时守护中" else "守护已停止"}", fontWeight = FontWeight.Bold, color = if (isServiceActive) Color(0xFF2E7D32) else Color.Gray)
                Text("【安全中心】: $targetAddr", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                Text("【孩子位置】: $currentAddr", fontSize = 12.sp, color = Color(0xFF0D47A1))
                distance?.let {
                    Text("实时距离: ${it}米", color = if (it > (distanceStr.toFloatOrNull() ?: 500f)) Color.Red else Color.Unspecified, fontWeight = FontWeight.Bold)
                }
            }
        }

        Box(modifier = Modifier.height(300.dp).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        onCreate(null)
                        val amap = this.map
                        amap.uiSettings.isMyLocationButtonEnabled = true
                        amap.isMyLocationEnabled = true
                        
                        amap.setOnMapClickListener { latLng ->
                            if (!isConfigLocked) {
                                amap.clear()
                                amap.addMarker(MarkerOptions().position(latLng).title("安全中心"))
                                targetLat = latLng.latitude
                                targetLng = latLng.longitude
                                performRegeocoding(latLng.latitude, latLng.longitude, true)
                            } else {
                                Toast.makeText(context, "请先解锁", Toast.LENGTH_SHORT).show()
                            }
                        }
                        
                        amap.setOnMyLocationChangeListener { location ->
                            if (location != null) {
                                currentLat = location.latitude
                                currentLng = location.longitude
                                performRegeocoding(location.latitude, location.longitude, false)
                                
                                if (targetLat == null) {
                                    val latLng = LatLng(location.latitude, location.longitude)
                                    amap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                                    targetLat = location.latitude
                                    targetLng = location.longitude
                                    performRegeocoding(location.latitude, location.longitude, true)
                                    amap.clear()
                                    amap.addMarker(MarkerOptions().position(latLng).title("安全中心"))
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            if (targetLat != null && !isConfigLocked) {
                IconButton(
                    onClick = {
                        targetLat = null
                        targetLng = null
                        targetAddr = "未选择安全中心"
                        prefs.edit().remove("lat").remove("lng").apply()
                    },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "清除", tint = Color.Red)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = distanceStr,
                onValueChange = { if (!isConfigLocked) distanceStr = it },
                label = { Text("安全半径(米)") },
                enabled = !isConfigLocked,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { if (!isConfigLocked) phoneNumber = it },
                label = { Text("家长电话") },
                enabled = !isConfigLocked,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        Text("报警方式配置", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = alertCall, onCheckedChange = { if(!isConfigLocked) alertCall = it }, enabled = !isConfigLocked)
            Text("自动拨号", fontSize = 14.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Checkbox(checked = alertSms, onCheckedChange = { if(!isConfigLocked) alertSms = it }, enabled = !isConfigLocked)
            Text("自动发短信", fontSize = 14.sp)
        }

        val isAdminActive = dpm.isAdminActive(adminComponent)
        Button(
            onClick = {
                if (!isAdminActive) {
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "开启防卸载保护")
                    }
                    context.startActivity(intent)
                } else if (!isConfigLocked) {
                    dpm.removeActiveAdmin(adminComponent)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isAdminActive) Color(0xFF4CAF50) else Color.Red)
        ) {
            Icon(if(isAdminActive) Icons.Default.Shield else Icons.Default.GppMaybe, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isAdminActive) "防卸载保护：已开启" else "立即开启防卸载保护(必须)")
        }

        // 测试报警按钮
        OutlinedButton(
            onClick = {
                if (phoneNumber.isEmpty()) {
                    Toast.makeText(context, "请先填写家长电话", Toast.LENGTH_SHORT).show()
                    return@OutlinedButton
                }
                Toast.makeText(context, "正在发起测试报警...", Toast.LENGTH_SHORT).show()
                
                // 执行测试逻辑
                if (alertSms) {
                    try {
                        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.getSystemService(SmsManager::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            SmsManager.getDefault()
                        }
                        smsManager?.sendTextMessage(phoneNumber, null, "【儿童守护测试】这是一条模拟报警短信，孩子目前安全。", null, null)
                        Toast.makeText(context, "测试短信已发出", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "短信发送失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                
                if (alertCall) {
                    val intent = Intent(Intent.ACTION_CALL)
                    intent.data = Uri.parse("tel:$phoneNumber")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        val dialIntent = Intent(Intent.ACTION_DIAL)
                        dialIntent.data = Uri.parse("tel:$phoneNumber")
                        dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(dialIntent)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Icon(Icons.Default.BugReport, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("测试报警 (模拟触发电话/短信)")
        }

        if (isServiceActive) {
            Button(
                onClick = {
                    if (isConfigLocked) {
                        Toast.makeText(context, "请先解锁", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    context.stopService(Intent(context, LocationService::class.java))
                    isServiceActive = false
                    Toast.makeText(context, "守护已关闭", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("停止当前守护")
            }
        } else {
            Button(
                onClick = {
                    if (isConfigLocked) {
                        Toast.makeText(context, "请先解锁", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (!Settings.System.canWrite(context)) {
                            Toast.makeText(context, "请授予“修改系统设置”权限", Toast.LENGTH_LONG).show()
                            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                            return@Button
                        }
                    }

                    val dist = distanceStr.toFloatOrNull() ?: 500f
                    if (phoneNumber.isEmpty() || targetLat == null) {
                        Toast.makeText(context, "请填写完整信息", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (!alertCall && !alertSms) {
                        Toast.makeText(context, "请至少选择一种报警方式", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    prefs.edit().apply {
                        putFloat("lat", targetLat?.toFloat() ?: 0f)
                        putFloat("lng", targetLng?.toFloat() ?: 0f)
                        putFloat("distance", dist)
                        putString("phone", phoneNumber)
                        putBoolean("alert_call", alertCall)
                        putBoolean("alert_sms", alertSms)
                        apply()
                    }
                    ContextCompat.startForegroundService(context, Intent(context, LocationService::class.java))
                    isServiceActive = true
                    isConfigLocked = true
                    Toast.makeText(context, "守护已启动", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("保存并开启守护")
            }
        }
    }

    if (showPasswordDialog) {
        Dialog(onDismissRequest = { showPasswordDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("输入管理员密码 (123456)", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = inputPassword,
                        onValueChange = { inputPassword = it },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        if (inputPassword == defaultPassword) {
                            isConfigLocked = false
                            showPasswordDialog = false
                            inputPassword = ""
                        } else {
                            Toast.makeText(context, "密码错误", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("确认解锁") }
                }
            }
        }
    }
}
