package com.penumbraos.bridge

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class NetworkService : INetworkService.Stub() {
    override fun fetchUrl(urlString: String): String {
        Log.d("NetworkService", "Fetching URL: $urlString")
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000 // 15 seconds
            connection.readTimeout = 15000 // 15 seconds

            val responseCode = connection.responseCode
            Log.d("NetworkService", "Response Code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                Log.d(
                    "NetworkService",
                    "Response: ${response.substring(0, Math.min(response.length, 100))}..."
                )
                return response.toString()
            } else {
                Log.e("NetworkService", "Error fetching URL. Response Code: $responseCode")
                return "Error: HTTP $responseCode ${connection.responseMessage}"
            }
        } catch (e: Exception) {
            Log.e("NetworkService", "Exception fetching URL", e)
            return "Error: ${e.message}"
        } finally {
            connection?.disconnect()
        }
    }
}
