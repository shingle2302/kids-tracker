package com.shingle.kids.core.api

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

data class PairRequest(
    val pairingCode: String,
    val childName: String,
    val parentPhone: String
)

data class LocationReportRequest(
    val pairingCode: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val batteryLevel: Int
)

data class DeviceStatusResponse(
    val device: DeviceInfo,
    val pendingCommands: List<CommandInfo>
)

data class DeviceInfo(
    val id: Long,
    val pairingCode: String,
    val childName: String,
    val parentPhone: String,
    val lastLatitude: Double,
    val lastLongitude: Double,
    val lastAddress: String,
    val batteryLevel: Int,
    val updatedAt: String
)

data class CommandInfo(
    val id: Long,
    val pairingCode: String,
    val commandType: String,
    val status: String,
    val createdAt: String
)

interface ApiService {
    @POST("api/v1/device/pair")
    suspend fun pairDevice(@Body request: PairRequest): Response<Map<String, Any>>

    @POST("api/v1/device/location")
    suspend fun reportLocation(@Body request: LocationReportRequest): Response<Map<String, Any>>

    @GET("api/v1/device/{pairingCode}")
    suspend fun getDeviceStatus(@Path("pairingCode") pairingCode: String): Response<DeviceStatusResponse>

    @POST("api/v1/command/send")
    suspend fun sendCommand(@Query("pairingCode") pairingCode: String, @Query("type") type: String): Response<Map<String, Any>>

    companion object {
        private const val BASE_URL = "http://10.0.2.2:8080/" // Android Emulator localhost alias

        val instance: ApiService by lazy {
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
}
