package io.nekohasekai.sagernet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.databinding.LayoutTrafficLogBinding
import io.nekohasekai.sagernet.databinding.LayoutTrafficLogItemBinding
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
    private var lastConnections: List<ClashConnection> = emptyList()
    private var filterText: String = ""
    private val adapter = ConnectionAdapter()

    private val exportJsonLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) doExport(uri, json = true)
        }
    private val exportCsvLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) doExport(uri, json = false)
        }

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setTitle(R.string.menu_traffic_log)
        toolbar.inflateMenu(R.menu.traffic_log_menu)
        toolbar.setOnMenuItemClickListener(this)

        binding = LayoutTrafficLogBinding.bind(view)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, ListListener)

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterText = s?.toString().orEmpty()
                applyFilter()
            }
        })

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
            lastConnections = emptyList()
            setEmptyMessage(getString(R.string.traffic_log_need_clash_api))
            return
        }

        try {
            val request = Request.Builder()
                .url("http://127.0.0.1:9090/connections")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()
            val parsed = gson.fromJson(body, ClashConnections::class.java)
            lastConnections = parsed?.connections.orEmpty()
        } catch (e: Exception) {
            lastConnections = emptyList()
            onMainDispatcher { setEmptyMessage(getString(R.string.traffic_log_need_clash_api) + "\n\n(${e.readableMessage})") }
            return
        }

        onMainDispatcher { applyFilter() }
    }

    private fun applyFilter() {
        if (!isAdded) return
        val filtered = if (filterText.isBlank()) {
            lastConnections
        } else {
            val needle = filterText.lowercase()
            lastConnections.filter {
                it.metadata.displayHost().lowercase().contains(needle) ||
                        it.metadata.processPath?.lowercase()?.contains(needle) == true
            }
        }
        val sorted = filtered.sortedByDescending { it.start }
        adapter.submit(sorted)
        if (sorted.isEmpty()) {
            setEmptyMessage(getString(R.string.traffic_log_no_connections))
        } else {
            binding.emptyView.visibility = View.GONE
        }
    }

    private fun setEmptyMessage(text: String) {
        if (!isAdded) return
        adapter.submit(emptyList())
        binding.emptyView.text = text
        binding.emptyView.visibility = View.VISIBLE
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

    private fun summaryOf(conn: ClashConnection): String {
        val host = conn.metadata.displayHost()
        val port = conn.metadata.destinationPort ?: "?"
        val network = conn.metadata.network ?: "?"
        val chain = conn.chains?.reversed()?.joinToString(" -> ") ?: "?"
        val rule = conn.rule ?: "?"
        val process = conn.metadata.processPath
        return buildString {
            append("$host:$port ($network)\n")
            append("  chain: $chain  rule: $rule\n")
            append("  ↑${formatBytes(conn.upload)}  ↓${formatBytes(conn.download)}")
            if (!process.isNullOrBlank()) append("\n  process: $process")
        }
    }

    private fun detailOf(conn: ClashConnection): String = buildString {
        appendLine("host: ${conn.metadata.displayHost()}")
        appendLine("network: ${conn.metadata.network ?: "?"}  type: ${conn.metadata.type ?: "?"}")
        appendLine("source: ${conn.metadata.sourceIP ?: "?"}:${conn.metadata.sourcePort ?: "?"}")
        appendLine("destination: ${conn.metadata.destinationIP ?: "?"}:${conn.metadata.destinationPort ?: "?"}")
        appendLine("chain: ${conn.chains?.reversed()?.joinToString(" -> ") ?: "?"}")
        appendLine("rule: ${conn.rule ?: "?"}${conn.rulePayload?.let { " ($it)" } ?: ""}")
        appendLine("upload: ${formatBytes(conn.upload)}  download: ${formatBytes(conn.download)}")
        appendLine("started: ${conn.start ?: "?"}")
        if (!conn.metadata.processPath.isNullOrBlank()) {
            appendLine("process: ${conn.metadata.processPath}")
        }
    }

    private fun showDetail(conn: ClashConnection) {
        val detail = detailOf(conn)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.traffic_log_detail_title)
            .setMessage(detail)
            .setPositiveButton(R.string.traffic_log_copy) { _, _ -> copyToClipboard(detail) }
            .setNeutralButton(R.string.traffic_log_block_domain) { _, _ -> confirmBlock(conn) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun copyToClipboard(text: String) {
        val cm = requireContext().getSystemService<ClipboardManager>()
        cm?.setPrimaryClip(ClipData.newPlainText("traffic-log", text))
        snackbar(R.string.traffic_log_copied).show()
    }

    private fun confirmBlock(conn: ClashConnection) {
        val host = conn.metadata.displayHost()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.traffic_log_block_domain)
            .setMessage(getString(R.string.traffic_log_block_confirm, host))
            .setPositiveButton(R.string.route_block) { _, _ ->
                runOnDefaultDispatcher {
                    ProfileManager.createRule(RuleEntity().apply {
                        name = "Block $host"
                        domains = host
                        outbound = -2L
                        enabled = true
                    })
                    onMainDispatcher { needReload() }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun doExport(uri: android.net.Uri, json: Boolean) {
        val connections = lastConnections
        try {
            requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                if (json) {
                    val pretty = GsonBuilder().setPrettyPrinting().create()
                    out.write(pretty.toJson(connections).toByteArray())
                } else {
                    val sb = StringBuilder()
                    sb.append("host,port,network,chain,rule,upload,download,process,start\n")
                    for (conn in connections) {
                        fun esc(s: String?) = "\"${(s ?: "").replace("\"", "\"\"")}\""
                        sb.append(esc(conn.metadata.displayHost())).append(',')
                        sb.append(esc(conn.metadata.destinationPort)).append(',')
                        sb.append(esc(conn.metadata.network)).append(',')
                        sb.append(esc(conn.chains?.reversed()?.joinToString(" -> "))).append(',')
                        sb.append(esc(conn.rule)).append(',')
                        sb.append(conn.upload).append(',')
                        sb.append(conn.download).append(',')
                        sb.append(esc(conn.metadata.processPath)).append(',')
                        sb.append(esc(conn.start)).append('\n')
                    }
                    out.write(sb.toString().toByteArray())
                }
            }
            snackbar(getString(R.string.traffic_log_export_done, connections.size)).show()
        } catch (e: Exception) {
            snackbar(e.readableMessage).show()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_pause_traffic_log -> {
                paused = !paused
                item.setTitle(if (paused) R.string.traffic_log_resume else R.string.traffic_log_pause)
            }

            R.id.action_export_json -> {
                if (lastConnections.isEmpty()) {
                    snackbar(R.string.traffic_log_export_empty).show()
                } else {
                    exportJsonLauncher.launch("traffic-log-${System.currentTimeMillis()}.json")
                }
            }

            R.id.action_export_csv -> {
                if (lastConnections.isEmpty()) {
                    snackbar(R.string.traffic_log_export_empty).show()
                } else {
                    exportCsvLauncher.launch("traffic-log-${System.currentTimeMillis()}.csv")
                }
            }
        }
        return true
    }

    override fun onDestroyView() {
        pollJob?.cancel()
        super.onDestroyView()
    }

    private inner class ConnectionAdapter : RecyclerView.Adapter<ConnectionAdapter.Holder>() {
        private var items: List<ClashConnection> = emptyList()

        fun submit(newItems: List<ClashConnection>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class Holder(val binding: LayoutTrafficLogItemBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = LayoutTrafficLogItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return Holder(binding)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val conn = items[position]
            holder.binding.text.text = summaryOf(conn)
            holder.binding.text.setOnClickListener { showDetail(conn) }
        }

        override fun getItemCount() = items.size
    }

}
