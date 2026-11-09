package io.nekohasekai.sagernet.bg

import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * "Secure Browsing": a one-shot downloader that turns a public malware-domain
 * blocklist into a normal route rule (outbound = block), which then shows up
 * and can be toggled/deleted on the Route screen like any other rule.
 */
object SecureBrowsing {
    const val RULE_NAME = "[Secure Browsing] Threat domains (HaGeZi TIF)"
    private const val SOURCE_URL =
        "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/tif.mini-onlydomains.txt"
    private const val MAX_DOMAINS = 5000

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** Blocking — run off the main thread. Returns the number of domains applied, or null on failure. */
    fun updateBlocklist(): Int? {
        val domains = try {
            val request = Request.Builder().url(SOURCE_URL).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }
            val body = response.body?.string() ?: ""
            response.close()
            body.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .distinct()
                .take(MAX_DOMAINS)
                .toList()
        } catch (_: Exception) {
            null
        } ?: return null

        if (domains.isEmpty()) return null

        val existing = SagerDatabase.rulesDao.allRules().find { it.name == RULE_NAME }
        val rule = (existing ?: RuleEntity()).apply {
            name = RULE_NAME
            this.domains = domains.joinToString("\n")
            outbound = -2L
            enabled = true
        }
        if (existing != null) {
            SagerDatabase.rulesDao.updateRule(rule)
        } else {
            rule.userOrder = SagerDatabase.rulesDao.nextOrder() ?: 1
            SagerDatabase.rulesDao.createRule(rule)
        }
        return domains.size
    }

}
