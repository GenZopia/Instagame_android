package com.genzopia.Instagame.gateway

import com.genzopia.Instagame.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton providing the configured Gateway Retrofit services.
 *
 * - [api]      — Kotlin suspend-based interface ([GatewayApiService])
 * - [callApi]  — Java-callable [Call]-based interface ([GatewayCallService])
 *
 * Both share the same underlying OkHttp client and base URL.
 */
object GatewayClient {

    private val retrofit: Retrofit by lazy {
        val okHttp = OkHttpClient.Builder()
            .addInterceptor(GatewayAuthInterceptor())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val baseUrl = BuildConfig.GATEWAY_BASE_URL.let {
            if (it.endsWith("/")) it else "$it/"
        }

        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /** Kotlin suspend-function API — use from Kotlin coroutines. */
    val api: GatewayApiService by lazy { retrofit.create(GatewayApiService::class.java) }

    /** Java Call-based API — use from Java via .enqueue() or .execute(). */
    val callApi: GatewayCallService by lazy { retrofit.create(GatewayCallService::class.java) }
}
