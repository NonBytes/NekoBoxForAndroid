package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutTrafficLogBinding
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.widget.ListListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class TrafficLogFragment : ToolbarFragment(R.layout.layout_traffic_log),
    Toolbar.OnMenuItemClickListener {

    private lateinit var binding: LayoutTrafficLogBinding
    private var pollJob: Job? = null
    private var paused = false

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
    }
    private val gson = Gson()

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
    )

    data class ClashMetadata(
        val network: String?,
        val host: String?,
        @SerializedName("destinationIP") val destinationIP: String?,
        @SerializedName("destinationPort") val destinationPort: String?,
        @SerializedName("processPath") val processPath: String?,
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setTitle(R.string.menu_traffic_log)
        toolbar.inflateMenu(R.menu.traffic_log_menu)
        toolbar.setOnMenuItemClickListener(this)

        binding = LayoutTrafficLogBinding.bind(view)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, ListListener)

        startPolling()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                if (!paused) refresh()
                delay(2000L)
            }
        }
    }

    private suspend fun refresh() {
        if (!DataStore.enableClashAPI) {
            setText(getString(R.string.traffic_log_need_clash_api))
            return
        }

        val text = try {
            val request = Request.Builder()
                .url("http://127.0.0.1:9090/connections")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()
            val parsed = gson.fromJson(body, ClashConnections::class.java)
            val connections = parsed?.connections.orEmpty()
            if (connections.isEmpty()) {
                getString(R.string.traffic_log_no_connections)
            } else {
                connections.sortedByDescending { it.start }.joinToString("\n\n") { conn ->
                    val host = conn.metadata.host?.takeIf { it.isNotBlank() }
                        ?: conn.metadata.destinationIP ?: "?"
                    val port = conn.metadata.destinationPort ?: "?"
                    val network = conn.metadata.network ?: "?"
                    val chain = conn.chains?.reversed()?.joinToString(" -> ") ?: "?"
                    val rule = conn.rule ?: "?"
                    val process = conn.metadata.processPath
                    buildString {
                        append("$host:$port ($network)\n")
                        append("  chain: $chain  rule: $rule\n")
                        append("  ↑${formatBytes(conn.upload)}  ↓${formatBytes(conn.download)}")
                        if (!process.isNullOrBlank()) append("\n  process: $process")
                    }
                }
            }
        } catch (e: Exception) {
            getString(R.string.traffic_log_need_clash_api) + "\n\n(${e.readableMessage})"
        }

        setText(text)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "${bytes}B"
        val units = listOf("KB", "MB", "GB")
        var value = bytes / 1024.0
        for (unit in units) {
            if (value < 1024.0) return "%.1f%s".format(value, unit)
            value /= 1024.0
        }
        return "%.1fTB".format(value)
    }

    private suspend fun setText(text: String) = onMainDispatcher {
        if (!isAdded) return@onMainDispatcher
        binding.textview.text = text
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_pause_traffic_log -> {
                paused = !paused
                item.setTitle(if (paused) R.string.traffic_log_resume else R.string.traffic_log_pause)
            }
        }
        return true
    }

    override fun onDestroyView() {
        pollJob?.cancel()
        super.onDestroyView()
    }

}
