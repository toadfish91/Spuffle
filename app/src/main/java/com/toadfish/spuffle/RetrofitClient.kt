package com.toadfish.spuffle

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(SpotifyRateLimitInterceptor())  // handles 429s automatically
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)  // allow time for retries
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val accountsService: SpotifyAccountsService by lazy {
        Retrofit.Builder()
            .baseUrl("https://accounts.spotify.com/api/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SpotifyAccountsService::class.java)
    }

    val apiService: SpotifyApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.spotify.com/v1/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SpotifyApiService::class.java)
    }
}