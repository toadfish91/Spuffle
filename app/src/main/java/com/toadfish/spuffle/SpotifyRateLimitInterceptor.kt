package com.toadfish.spuffle

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

class SpotifyRateLimitInterceptor : Interceptor {

    companion object {
        private const val TAG = "Spuffle"
        private const val MAX_RETRIES = 5
        private const val DEFAULT_RETRY_DELAY_MS = 1000L
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        var retries = 0

        while (response.code == 429 && retries < MAX_RETRIES) {
            val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull() ?: 1L

            Log.w(TAG, "Rate limited (429). Retry-After: ${retryAfterSeconds}s. Attempt ${retries + 1}/$MAX_RETRIES")

            // If Spotify wants us to wait more than 30 seconds, the account is
            // heavily throttled. Stop retrying and let the error surface to the user.
            if (retryAfterSeconds > 30) {
                Log.e(TAG, "Retry-After of ${retryAfterSeconds}s is too long. Surfacing 429 to user.")
                break
            }

            response.close()
            Thread.sleep(retryAfterSeconds * 1000L)
            response = chain.proceed(request)
            retries++
        }

        if (response.code == 429) {
            Log.e(TAG, "Still rate limited after $MAX_RETRIES retries. Giving up.")
        }

        return response
    }
}