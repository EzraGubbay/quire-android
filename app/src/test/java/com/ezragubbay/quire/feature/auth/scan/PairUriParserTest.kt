package com.ezragubbay.quire.feature.auth.scan

import org.junit.Assert.assertEquals
import org.junit.Test

class PairUriParserTest {
    private fun invalid(reason: PairUriResult.Reason) = PairUriResult.Invalid(reason)

    @Test
    fun valid_uri_yieldsServerAndCode() {
        val result = PairUriParser.parse("quire://pair?server=https%3A%2F%2Fquire.example.com&code=AbC12345")
        assertEquals(PairUriResult.Valid("https://quire.example.com", "AbC12345"), result)
    }

    @Test
    fun unencoded_server_isAccepted() {
        val result = PairUriParser.parse("quire://pair?server=https://quire.example.com&code=ABCD2345")
        assertEquals(PairUriResult.Valid("https://quire.example.com", "ABCD2345"), result)
    }

    @Test
    fun extra_parameters_areIgnored() {
        val result = PairUriParser.parse("quire://pair?x=1&server=https%3A%2F%2Fq.example.com&code=ABCD2345&y=2")
        assertEquals(PairUriResult.Valid("https://q.example.com", "ABCD2345"), result)
    }

    @Test
    fun missing_server() {
        assertEquals(invalid(PairUriResult.Reason.MissingServer), PairUriParser.parse("quire://pair?code=ABCD2345"))
    }

    @Test
    fun missing_code() {
        val result = PairUriParser.parse("quire://pair?server=https%3A%2F%2Fq.example.com")
        assertEquals(invalid(PairUriResult.Reason.MissingCode), result)
    }

    @Test
    fun malformed_code() {
        val result = PairUriParser.parse("quire://pair?server=https%3A%2F%2Fq.example.com&code=short")
        assertEquals(invalid(PairUriResult.Reason.MissingCode), result)
    }

    @Test
    fun non_https_server_isRejected() {
        val result = PairUriParser.parse("quire://pair?server=http%3A%2F%2Fq.example.com&code=ABCD2345")
        assertEquals(invalid(PairUriResult.Reason.InsecureServer), result)
    }

    @Test
    fun cleartext_localhost_allowedOnlyWhenFlagged() {
        val raw = "quire://pair?server=http%3A%2F%2F10.0.2.2%3A8080&code=ABCD2345"
        assertEquals(invalid(PairUriResult.Reason.InsecureServer), PairUriParser.parse(raw))
        assertEquals(
            PairUriResult.Valid("http://10.0.2.2:8080", "ABCD2345"),
            PairUriParser.parse(raw, allowCleartextLocal = true),
        )
    }

    @Test
    fun other_scheme_orHost_isNotPairUri() {
        val notPair = invalid(PairUriResult.Reason.NotPairUri)
        assertEquals(notPair, PairUriParser.parse("https://pair?server=https%3A%2F%2Fq.example.com&code=ABCD2345"))
        assertEquals(notPair, PairUriParser.parse("quire://other?server=https%3A%2F%2Fq.example.com&code=ABCD2345"))
    }

    @Test
    fun malformed_input_isNotPairUri() {
        val notPair = invalid(PairUriResult.Reason.NotPairUri)
        assertEquals(notPair, PairUriParser.parse(""))
        assertEquals(notPair, PairUriParser.parse("hello world"))
        assertEquals(notPair, PairUriParser.parse("quire://pair?server=%ZZ&code=%"))
    }
}
