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
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.GsonBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.ClashApiClient
import io.nekohasekai.sagernet.bg.ClashConnection
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

class TrafficLogFragment : ToolbarFragment(R.layout.layout_traffic_log),
    Toolbar.OnMenuItemClickListener {

    private lateinit var binding: LayoutTrafficLogBinding
    private var pollJob: Job? = null
    private var paused = false
    private var lastConnections: List<ClashConnection> = emptyList()
    private var filterText: String = ""
    private val adapter = ConnectionAdapter()
    private val processSuggestionAdapter by lazy {
        ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line)
    }
    private var knownProcesses = emptySet<String>()

    private val exportJsonLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) doExport(uri, json = true)
        }
    private val exportCsvLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) doExport(uri, json = false)
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

        binding.search.setAdapter(processSuggestionAdapter)
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
            onMainDispatcher { setEmptyMessage(getString(R.string.traffic_log_need_clash_api)) }
            return
        }

        lastConnections = onIoDispatcher { ClashApiClient.fetchConnections() }
        onMainDispatcher {
            updateProcessSuggestions()
            applyFilter()
        }
    }

    private fun updateProcessSuggestions() {
        val processes = lastConnections.mapNotNull { it.metadata.processPath?.takeIf { p -> p.isNotBlank() } }
            .toSet()
        if (processes == knownProcesses) return
        knownProcesses = processes
        processSuggestionAdapter.clear()
        processSuggestionAdapter.addAll(processes.sorted())
        processSuggestionAdapter.notifyDataSetChanged()
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
            .setNeutralButton(R.string.traffic_log_more_actions) { _, _ -> showMoreActions(conn) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showMoreActions(conn: ClashConnection) {
        val options = arrayOf(
            getString(R.string.traffic_log_block_domain),
            getString(R.string.traffic_log_add_scope),
        )
        MaterialAlertDialogBuilder(requireContext())
            .setItems(options) { _, which ->
                when (which) {
                    0 -> confirmBlock(conn)
                    1 -> addToProxyScope(conn)
                }
            }
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

    private fun addToProxyScope(conn: ClashConnection) {
        val pkg = conn.metadata.processPath
        if (pkg.isNullOrBlank()) {
            snackbar(R.string.traffic_log_add_scope_no_process).show()
            return
        }
        val individual = DataStore.individual.split('\n').filter { it.isNotBlank() }.toMutableList()
        if (pkg in individual && DataStore.proxyApps && !DataStore.bypass) {
            snackbar(getString(R.string.traffic_log_add_scope_already, pkg)).show()
            return
        }
        if (pkg !in individual) individual.add(pkg)
        DataStore.individual = individual.joinToString("\n")
        DataStore.proxyApps = true
        DataStore.bypass = false
        needReload()
        snackbar(getString(R.string.traffic_log_add_scope_done, pkg)).show()
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
