package com.ezragubbay.quire.feature.auth.scan

import com.ezragubbay.quire.core.network.ServerUrl
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

sealed interface PairUriResult {
    data class Valid(
        val serverUrl: String,
        val code: String,
    ) : PairUriResult

    data class Invalid(
        val reason: Reason,
    ) : PairUriResult

    enum class Reason { NotPairUri, MissingServer, MissingCode, InsecureServer }
}

/** Parses `quire://pair?server=<url>&code=<8 chars>` (from a scanned QR or a deep link). Pure; no Android deps. */
object PairUriParser {
    private const val SCHEME = "quire"
    private const val HOST = "pair"
    private val codeRegex = Regex("^[A-Za-z0-9]{8}$")

    fun parse(
        raw: String,
        allowCleartextLocal: Boolean = false,
    ): PairUriResult {
        val params = queryParams(raw.trim()) ?: return PairUriResult.Invalid(PairUriResult.Reason.NotPairUri)
        return validate(params["server"], params["code"], allowCleartextLocal)
    }

    /** Validates already-extracted `server` / `code` values (e.g. from a navigation deep-link argument). */
    fun validate(
        serverParam: String?,
        codeParam: String?,
        allowCleartextLocal: Boolean = false,
    ): PairUriResult {
        val server = serverParam?.trim().orEmpty()
        val code = codeParam?.trim().orEmpty()
        val reason =
            when {
                server.isEmpty() -> PairUriResult.Reason.MissingServer
                code.isEmpty() || !codeRegex.matches(code) -> PairUriResult.Reason.MissingCode
                !ServerUrl.isAllowed(server, allowCleartextLocal) -> PairUriResult.Reason.InsecureServer
                else -> null
            }
        return if (reason == null) PairUriResult.Valid(server, code) else PairUriResult.Invalid(reason)
    }

    /** Query parameters when [raw] is a `quire://pair` URI, else null. */
    private fun queryParams(raw: String): Map<String, String>? =
        try {
            val uri = URI(raw)
            if (SCHEME.equals(uri.scheme, ignoreCase = true) && HOST.equals(uri.host, ignoreCase = true)) {
                uri.rawQuery
                    .orEmpty()
                    .split('&')
                    .filter { it.contains('=') }
                    .associate {
                        val (key, value) = it.split('=', limit = 2)
                        URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
                    }
            } else {
                null
            }
        } catch (_: URISyntaxException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
}
