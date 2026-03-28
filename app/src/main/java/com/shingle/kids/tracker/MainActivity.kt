package com.shingle.kids.tracker

import android.Manifest
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.core.app.ActivityCompat
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
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (!results.all { it.value }) {
            Toast.makeText(this, "需授权以开启完整守护", Toast.LENGTH_SHORT).show()
        }
    }

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
        requestPermissionLauncher.launch(permissions)

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
    val statePrefs = context.getSharedPreferences("state", Context.MODE_PRIVATE)
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, AdminReceiver::class.java)

    var targetLat by remember { mutableStateOf(if (prefs.contains("lat")) prefs.getFloat("lat", 0f).toDouble() else null) }
    var targetLng by remember { mutableStateOf(if (prefs.contains("lng")) prefs.getFloat("lng", 0f).toDouble() else null) }
    var currentLat by remember { mutableStateOf<Double?>(null) }
    var currentLng by remember { mutableStateOf<Double?>(null) }
    var currentAddr by remember { mutableStateOf("正在获取位置...") }
    var targetAddr by remember { mutableStateOf("未选中心点") }
    var distanceStr by remember { mutableStateOf(prefs.getFloat("distance", 500f).toString()) }
    var phoneNumber by remember { mutableStateOf(prefs.getString("phone", "") ?: "") }
    
    var alertCall by remember { mutableStateOf(prefs.getBoolean("alert_call", true)) }
    var alertSms by remember { mutableStateOf(prefs.getBoolean("alert_sms", false)) }
    var alertRecord by remember { mutableStateOf(prefs.getBoolean("alert_record", true)) }

    // 锁定与密码逻辑
    var isConfigLocked by remember { mutableStateOf(true) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showFirstUnlockDialog by remember { mutableStateOf(false) }
    var inputPassword by remember { mutableStateOf("") }
    var newPasswordDialog by remember { mutableStateOf("") }
    var newPasswordArea by remember { mutableStateOf("") }
    val savedPassword = prefs.getString("admin_pwd", "123456") ?: "123456"
    val isFirstTime = prefs.getBoolean("is_first_unlock", true)

    var isServiceActive by remember { mutableStateOf(isServiceRunning(context, LocationService::class.java)) }
    var isGuardianActive by remember { mutableStateOf(statePrefs.getBoolean("is_guardian_active", false)) }

    LaunchedEffect(Unit) {
        while(true) {
            isServiceActive = isServiceRunning(context, LocationService::class.java)
            isGuardianActive = statePrefs.getBoolean("is_guardian_active", false)
            delay(2000)
        }
    }

    val performRegeocoding: (Double, Double, Boolean) -> Unit = { lat, lng, isTarget ->
        try {
            val geocoder = GeocodeSearch(context)
            val query = RegeocodeQuery(LatLonPoint(lat, lng), 200f, GeocodeSearch.AMAP)
            geocoder.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
                    if (rCode == 1000 && result != null) {
                        val address = result.regeocodeAddress.formatAddress
                        if (isTarget) targetAddr = address else currentAddr = address
                    }
                }
                override fun onGeocodeSearched(result: GeocodeResult?, rCode: Int) {}
            })
            geocoder.getFromLocationAsyn(query)
        } catch (e: Exception) { Log.e("Geo", "Error: ${e.message}") }
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
                Icon(Icons.Default.Notifications, null, tint = Color.Gray)
            }
            IconButton(onClick = { 
                if (isConfigLocked) showPasswordDialog = true else isConfigLocked = true 
            }) {
                Icon(if (isConfigLocked) Icons.Default.Lock else Icons.Default.LockOpen, null, tint = if (isConfigLocked) Color.Red else Color(0xFF4CAF50))
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = if (isGuardianActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("状态: ${if (isGuardianActive) "正在实时守护中" else if(isServiceActive) "测试模式中" else "守护已停止"}", 
                    fontWeight = FontWeight.Bold, color = if (isGuardianActive) Color(0xFF2E7D32) else Color.Gray)
                Text("【安全中心】: $targetAddr", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("【孩子位置】: $currentAddr", fontSize = 12.sp)
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
                            } else { Toast.makeText(context, "请先解锁", Toast.LENGTH_SHORT).show() }
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
            Spacer(modifier = Modifier.width(16.dp))
            Checkbox(checked = alertRecord, onCheckedChange = { if(!isConfigLocked) alertRecord = it }, enabled = !isConfigLocked)
            Text("自动录音", fontSize = 14.sp)
        }

        if (!isConfigLocked) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("修改管理员密码", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newPasswordArea,
                            onValueChange = { newPasswordArea = it },
                            label = { Text("新密码") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (newPasswordArea.length >= 4) {
                                prefs.edit().putString("admin_pwd", newPasswordArea).apply()
                                isConfigLocked = true
                                newPasswordArea = ""
                                Toast.makeText(context, "密码已修改并锁定", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "密码长度至少4位", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("修改并锁定") }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = {
                if (phoneNumber.isEmpty()) {
                    Toast.makeText(context, "请先填写家长电话", Toast.LENGTH_SHORT).show()
                    return@OutlinedButton
                }
                if (alertRecord && ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(context as ComponentActivity, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
                    return@OutlinedButton
                }
                val serviceIntent = Intent(context, LocationService::class.java)
                if (alertRecord) serviceIntent.putExtra("COMMAND", "TEST_RECORD")
                if (isServiceActive) context.startService(serviceIntent) else ContextCompat.startForegroundService(context, serviceIntent)

                if (alertSms) {
                    try {
                        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
                        smsManager?.sendTextMessage(phoneNumber, null, "【测试】报警模拟，目前安全。", null, null)
                    } catch (e: Exception) { Log.e("Test", "SMS failed: ${e.message}") }
                }
                if (alertCall) {
                    val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    try { context.startActivity(intent) } catch (e: Exception) { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Icon(Icons.Default.BugReport, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("测试已选告警方式")
        }

        if (isServiceActive) {
            Button(
                onClick = {
                    if (isConfigLocked) {
                        Toast.makeText(context, "请先解锁", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val stopIntent = Intent(context, LocationService::class.java).apply { putExtra("COMMAND", "STOP_SELF") }
                    context.startService(stopIntent)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Icon(Icons.Default.Stop, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("停止当前运行")
            }
        } else {
            Button(
                onClick = {
                    if (isConfigLocked) {
                        Toast.makeText(context, "请先解锁", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val dist = distanceStr.toFloatOrNull() ?: 500f
                    if (phoneNumber.isEmpty() || targetLat == null) {
                        Toast.makeText(context, "请填写完整信息", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    prefs.edit().apply {
                        putFloat("lat", targetLat?.toFloat() ?: 0f)
                        putFloat("lng", targetLng?.toFloat() ?: 0f)
                        putFloat("distance", dist)
                        putString("phone", phoneNumber)
                        putBoolean("alert_call", alertCall)
                        putBoolean("alert_sms", alertSms)
                        putBoolean("alert_record", alertRecord)
                        apply()
                    }
                    val startIntent = Intent(context, LocationService::class.java).apply { putExtra("COMMAND", "START_GUARDIAN") }
                    ContextCompat.startForegroundService(context, startIntent)
                    isConfigLocked = true
                    Toast.makeText(context, "守护已开启", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("保存并开启守护")
            }
        }
    }

    if (showPasswordDialog) {
        Dialog(onDismissRequest = { showPasswordDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("输入管理员密码", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = inputPassword,
                        onValueChange = { inputPassword = it },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        if (inputPassword == savedPassword) {
                            showPasswordDialog = false
                            inputPassword = ""
                            if (isFirstTime) {
                                showFirstUnlockDialog = true
                            } else {
                                isConfigLocked = false
                            }
                        } else {
                            Toast.makeText(context, "密码错误", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("确认解锁") }
                }
            }
        }
    }

    if (showFirstUnlockDialog) {
        Dialog(onDismissRequest = { }) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("建议修改初始密码", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("检测到您正在使用初始密码，为了安全请设置新密码。", fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                    OutlinedTextField(
                        value = newPasswordDialog,
                        onValueChange = { newPasswordDialog = it },
                        label = { Text("输入新密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        if (newPasswordDialog.length >= 4) {
                            prefs.edit().apply {
                                putString("admin_pwd", newPasswordDialog)
                                putBoolean("is_first_unlock", false)
                                apply()
                            }
                            showFirstUnlockDialog = false
                            isConfigLocked = true // 修改后强制锁定一次
                            Toast.makeText(context, "新密码已生效并锁定", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "密码至少4位", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("确认修改并锁定") }
                }
            }
        }
    }
}
