package com.shingle.kids.core.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

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

data class AuthRequest(
    val username: String,
    val passwordHash: String,
    val role: String
)

data class SubscribeRequest(
    val pairingCode: String,
    val planType: String
)

data class PushTokenRequest(
    val pairingCode: String,
    val fcmToken: String,
    val deviceType: String
)

data class SubscriptionResponse(
    val id: Long,
    val pairingCode: String,
    val isVip: Boolean,
    val planType: String,
    val expireDate: String?
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
    @POST("api/v1/auth/register")
    suspend fun register(@Body request: AuthRequest): Response<Map<String, Any>>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: AuthRequest): Response<Map<String, Any>>

    @GET("api/v1/subscription/{pairingCode}")
    suspend fun getSubscription(@Path("pairingCode") pairingCode: String): Response<SubscriptionResponse>

    @POST("api/v1/subscription/subscribe")
    suspend fun subscribe(@Body request: SubscribeRequest): Response<Map<String, Any>>

    @POST("api/v1/push/register")
    suspend fun registerPushToken(@Body request: PushTokenRequest): Response<Map<String, Any>>

    @POST("api/v1/device/pair")
    suspend fun pairDevice(@Body request: PairRequest): Response<Map<String, Any>>

    @POST("api/v1/device/location")
    suspend fun reportLocation(@Body request: LocationReportRequest): Response<Map<String, Any>>

    @GET("api/v1/device/{pairingCode}")
    suspend fun getDeviceStatus(@Path("pairingCode") pairingCode: String): Response<DeviceStatusResponse>

    @POST("api/v1/command/send")
    suspend fun sendCommand(@Query("pairingCode") pairingCode: String, @Query("type") type: String): Response<Map<String, Any>>

    companion object {
        var baseUrl = "http://192.168.100.101:8080/"
        var authToken: String? = null

        private val client by lazy {
            val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .addInterceptor(Interceptor { chain ->
                    val original = chain.request()
                    val token = authToken
                    if (!token.isNullOrEmpty()) {
                        val authorized = original.newBuilder()
                            .header("Authorization", "Bearer $token")
                            .build()
                        chain.proceed(authorized)
                    } else {
                        chain.proceed(original)
                    }
                })
                .build()
        }

        fun getInstance(customUrl: String? = null): ApiService {
            val url = customUrl?.takeIf { it.isNotBlank() } ?: baseUrl
            val formattedUrl = if (url.endsWith("/")) url else "$url/"
            return Retrofit.Builder()
                .baseUrl(formattedUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }

        val instance: ApiService
            get() = getInstance()
    }
}
