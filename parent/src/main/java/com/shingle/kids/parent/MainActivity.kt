package com.shingle.kids.parent

import android.Manifest
import android.os.Bundle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.*
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (!results.all { it.value }) {
            Toast.makeText(this, "需授权以获取完整地图和监控体验", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        MapsInitializer.updatePrivacyShow(applicationContext, true, true)
        MapsInitializer.updatePrivacyAgree(applicationContext, true)
        try {
            MapsInitializer.initialize(applicationContext)
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                ParentMainScreen(savedInstanceState)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentMainScreen(savedInstanceState: Bundle?) {
    var serverUrlInput by remember { mutableStateOf("http://192.168.100.101:8080/") }
    var pairingCodeInput by remember { mutableStateOf("888888") }
    var connectedChildInfo by remember { mutableStateOf("暂无绑定设备") }
    var aMapInstance by remember { mutableStateOf<com.amap.api.maps.AMap?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("儿童安全守护 - 家长端", fontWeight = FontWeight.Bold) },
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("后端服务配置", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        label = { Text("后端服务 URL (模拟器用 10.0.2.2, 真机用局域网IP)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("绑定孩子设备", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    OutlinedTextField(
                        value = pairingCodeInput,
                        onValueChange = { pairingCodeInput = it },
                        label = { Text("输入孩子端配对码") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (pairingCodeInput.isNotEmpty()) {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val api = com.shingle.kids.core.api.ApiService.getInstance(serverUrlInput)
                                        val response = api.pairDevice(
                                            com.shingle.kids.core.api.PairRequest(
                                                pairingCode = pairingCodeInput,
                                                childName = "宝宝手机",
                                                parentPhone = "13800000000"
                                            )
                                        )
                                        if (response.isSuccessful) {
                                            val statusRes = api.getDeviceStatus(pairingCodeInput)
                                            if (statusRes.isSuccessful) {
                                                val dev = statusRes.body()?.device
                                                withContext(Dispatchers.Main) {
                                                    if (dev != null) {
                                                        connectedChildInfo = "已连接: ${dev.childName} | 位置: ${dev.lastAddress.ifEmpty { "未定位" }} | 电量: ${dev.batteryLevel}%"
                                                        if (dev.lastLatitude != 0.0 && dev.lastLongitude != 0.0) {
                                                            val childLatLng = com.amap.api.maps.model.LatLng(dev.lastLatitude, dev.lastLongitude)
                                                            aMapInstance?.clear()
                                                            aMapInstance?.addMarker(
                                                                com.amap.api.maps.model.MarkerOptions()
                                                                    .position(childLatLng)
                                                                    .title(dev.childName)
                                                                    .snippet(dev.lastAddress)
                                                            )
                                                            aMapInstance?.moveCamera(
                                                                com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(childLatLng, 16f)
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                withContext(Dispatchers.Main) {
                                                    connectedChildInfo = "配对成功，但获取设备状态失败"
                                                }
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                connectedChildInfo = "配对请求失败: HTTP ${response.code()}"
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            connectedChildInfo = "连接后端异常: ${e.message}"
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("连接后端并同步设备位置")
                    }
                    Text(connectedChildInfo, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("实时地图监控", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                MapView(ctx).apply {
                                    onCreate(savedInstanceState)
                                    val map = this.map
                                    aMapInstance = map
                                    map?.uiSettings?.isZoomControlsEnabled = true
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
