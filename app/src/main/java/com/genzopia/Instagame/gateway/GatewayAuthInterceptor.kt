package com.genzopia.Instagame.gateway

import android.util.Log
import com.genzopia.Instagame.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that attaches:
 *  - x-api-key header (static gateway secret from BuildConfig)
 *  - Authorization: Bearer <firebase-id-token> (refreshed on 401)
 *
 * Requirements: 1.1, 1.2
 */
class GatewayAuthInterceptor : Interceptor {

    companion object {
        private const val TAG = "GatewayAuthInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // Always attach x-api-key
        val requestBuilder = original.newBuilder()
            .header("x-api-key", BuildConfig.GATEWAY_API_KEY)

        // Attach Bearer token for authenticated routes (skip /auth/* paths)
        val path = original.url.encodedPath
        if (!path.contains("/auth/")) {
            val idToken = getIdToken(forceRefresh = false)
            if (idToken != null) {
                requestBuilder.header("Authorization", "Bearer $idToken")
            }
        }

        val response = chain.proceed(requestBuilder.build())

        // On 401, try once more with a force-refreshed token
        if (response.code == 401 && !path.contains("/auth/")) {
            response.close()
            val freshToken = getIdToken(forceRefresh = true)
            if (freshToken != null) {
                val retryRequest = original.newBuilder()
                    .header("x-api-key", BuildConfig.GATEWAY_API_KEY)
                    .header("Authorization", "Bearer $freshToken")
                    .build()
                return chain.proceed(retryRequest)
            }
        }

        return response
    }

    private fun getIdToken(forceRefresh: Boolean): String? {
        return try {
            val user = FirebaseAuth.getInstance().currentUser ?: return null
            runBlocking {
                user.getIdToken(forceRefresh).await().token
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get id token (forceRefresh=$forceRefresh): ${e.message}")
            null
        }
    }
}
