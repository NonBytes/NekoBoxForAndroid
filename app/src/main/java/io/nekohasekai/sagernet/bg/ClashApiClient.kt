package io.nekohasekai.sagernet.bg

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class ClashConnections(
    val connections: List<ClashConnection>?,
)

data class ClashConnection(
    val id: String,
    val metadata: ClashMetadata,
    val upload: Long,
    val download: Long,
    val start: String?,
    val chains: List<String>?,
    val rule: String?,
    val rulePayload: String?,
)

data class ClashMetadata(
    val network: String?,
    val type: String?,
    val host: String?,
    @SerializedName("sourceIP") val sourceIP: String?,
    @SerializedName("sourcePort") val sourcePort: String?,
    @SerializedName("destinationIP") val destinationIP: String?,
    @SerializedName("destinationPort") val destinationPort: String?,
    @SerializedName("processPath") val processPath: String?,
) {
    fun displayHost() = host?.takeIf { it.isNotBlank() } ?: destinationIP ?: "?"
}

/**
 * Thin blocking client for sing-box's built-in Clash API (127.0.0.1:9090, only
 * reachable while DataStore.enableClashAPI is on and the service is running).
 */
object ClashApiClient {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
    }
    private val gson = Gson()

    /** Blocking call — run off the main thread. Returns empty list on any failure. */
    fun fetchConnections(): List<ClashConnection> {
        return try {
            val request = Request.Builder().url("http://127.0.0.1:9090/connections").build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()
            gson.fromJson(body, ClashConnections::class.java)?.connections.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Writes connections as pretty JSON into the app's own external files dir. Returns the file, or null if nothing to write / write failed. */
    fun exportToAppStorage(context: Context, connections: List<ClashConnection>): File? {
        if (connections.isEmpty()) return null
        return try {
            val dir = File(context.getExternalFilesDir(null), "traffic-logs").apply { mkdirs() }
            val file = File(dir, "traffic-log-${System.currentTimeMillis()}.json")
            val pretty = GsonBuilder().setPrettyPrinting().create()
            file.writeText(pretty.toJson(connections))
            file
        } catch (_: Exception) {
            null
        }
    }

}
