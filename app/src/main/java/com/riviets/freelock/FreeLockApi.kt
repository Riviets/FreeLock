package com.riviets.freelock

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class WorkoutSyncRequest(val user_id: Int, val pushups_count: Int, val earned_minutes: Int)
data class UsageSyncRequest(val user_id: Int, val app_package_name: String, val spent_minutes: Int)
data class SyncResponse(val success: Boolean, val message: String)
data class UserStatusResponse(val balance: Int, val blocked_apps: List<String>)

interface FreeLockApi {
    @POST("/api/sync/workout")
    suspend fun syncWorkout(@Body request: WorkoutSyncRequest): SyncResponse

    @POST("/api/sync/usage")
    suspend fun syncUsage(@Body request: UsageSyncRequest): SyncResponse

    @GET("/api/user/{id}/status")
    suspend fun getUserStatus(@Path("id") userId: Int): UserStatusResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://freelock-api-server.onrender.com/"

    val api: FreeLockApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FreeLockApi::class.java)
    }
}