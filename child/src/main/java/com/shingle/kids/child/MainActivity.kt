package com.shingle.kids.child

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption

class MainActivity : ComponentActivity() {

    private val requestBackgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "已获得后台定位权限 (始终允许)", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "请在系统设置中将定位权限设置为“始终允许”", Toast.LENGTH_LONG).show()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            checkAndRequestBackgroundLocation()
        } else {
            Toast.makeText(this, "请授予定位与通知权限以开启完整守护", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestBackgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        AMapLocationClient.updatePrivacyShow(applicationContext, true, true)
        AMapLocationClient.updatePrivacyAgree(applicationContext, true)

        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECORD_AUDIO
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

        requestPermissionLauncher.launch(permissions)

        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                ChildMainScreen(
                    onRequestPermissions = {
                        requestPermissionLauncher.launch(permissions)
                    },
                    onRequestBackgroundLocation = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            requestBackgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else {
                            Toast.makeText(this, "当前系统版本无需单独开通后台定位权限", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildMainScreen(
    onRequestPermissions: () -> Unit = {},
    onRequestBackgroundLocation: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var serverUrlInput by remember { mutableStateOf("http://192.168.100.101:8080/") }
    var pairingCode by remember { mutableStateOf("888888") }
    var reportStatusText by remember { mutableStateOf("未上报位置") }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("儿童安全守护 - 孩子端", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("当前绑定配对码", fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(pairingCode, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("后端服务 URL (模拟器用 10.0.2.2, 真机用局域网IP)", fontSize = 12.sp)
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            reportStatusText = "正在触发高德 GPS 定位..."
                            try {
                                val locationClient = AMapLocationClient(context)
                                val option = AMapLocationClientOption().apply {
                                    locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                                    isOnceLocation = true
                                    isNeedAddress = true
                                }
                                locationClient.setLocationOption(option)
                                locationClient.setLocationListener { location ->
                                    if (location != null && location.errorCode == 0) {
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
                                                val currentBattery = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
                                                val api = com.shingle.kids.core.api.ApiService.getInstance(serverUrlInput)
                                                val response = api.reportLocation(
                                                    com.shingle.kids.core.api.LocationReportRequest(
                                                        pairingCode = pairingCode,
                                                        latitude = location.latitude,
                                                        longitude = location.longitude,
                                                        address = location.address.ifEmpty { "未知真实地址" },
                                                        batteryLevel = currentBattery
                                                    )
                                                )
                                                withContext(Dispatchers.Main) {
                                                    if (response.isSuccessful) {
                                                        reportStatusText = "真实位置成功上报云端！\n地址：${location.address}\n(经度:${location.longitude}, 纬度:${location.latitude})"
                                                        Toast.makeText(context, "真实定位已同步云端！家长端刷新即可在地图显示", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        reportStatusText = "上报失败: HTTP ${response.code()}"
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    reportStatusText = "上报异常: ${e.message}"
                                                }
                                            }
                                        }
                                    } else {
                                        reportStatusText = "定位失败 (错误码:${location?.errorCode}, 原因:${location?.errorInfo})"
                                    }
                                }
                                locationClient.startLocation()
                            } catch (e: Throwable) {
                                reportStatusText = "启动定位异常: ${e.message}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("获取并上报当前高德真实 GPS 位置")
                    }
                    Text(reportStatusText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("安全防护与权限设置", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    OutlinedButton(
                        onClick = onRequestPermissions,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("点击授予常规前台定位/通知权限")
                    }

                    OutlinedButton(
                        onClick = onRequestBackgroundLocation,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("点击申请后台定位权限 (设置为始终允许)")
                    }

                    Button(onClick = {
                        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                        val adminName = ComponentName(context, AdminReceiver::class.java)
                        if (!dpm.isAdminActive(adminName)) {
                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminName)
                                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "开启防卸载保护，防止孩子误删应用。")
                            }
                            context.startActivity(intent)
                        } else {
                            Toast.makeText(context, "防卸载保护已激活", Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Security, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("1. 激活防卸载保护 (设备管理器)")
                    }

                    Button(onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Accessibility, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("2. 开启防卸载插件 (辅助功能)")
                    }

                    Button(onClick = {
                        val intent = Intent(context, LocationService::class.java).apply {
                            action = "START_GUARDIAN"
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                        Toast.makeText(context, "守护服务已正式启动", Toast.LENGTH_SHORT).show()
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("3. 正式开启后台守护服务")
                    }
                }
            }
        }
    }
}
