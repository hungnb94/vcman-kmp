package com.tekome.vcman.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrlChecksTest {
    @Test
    fun localHostsOverHttpDoNotWarn() {
        listOf(
            "http://10.0.2.2:1234/v1",
            "http://127.0.0.1:8080",
            "http://[::1]:8080/v1",
            "http://localhost:11434",
            "HTTP://LOCALHOST",
        ).forEach { assertFalse(isInsecureRemote(it), it) }
    }

    @Test
    fun remoteHostsOverHttpWarn() {
        listOf(
            "http://localhost.attacker.com/v1",
            "http://evil.com/?q=localhost",
            "http://user@192.168.1.5",
            "http://localhost@evil.com",
            "http://EVIL.com",
            "http://192.168.1.10:11434/v1",
        ).forEach { assertTrue(isInsecureRemote(it), it) }
    }

    @Test
    fun httpsAndInvalidUrlsDoNotWarn() {
        listOf("https://evil.com", "https://api.openai.com/v1", "not a url", "", "http://").forEach {
            assertFalse(isInsecureRemote(it), it)
        }
    }

    @Test
    fun httpHostOf_extractsLowercaseHost() {
        assertEquals("example.com", httpHostOf("https://Example.com:8443/path?x=1#f"))
        assertEquals("[::1]", httpHostOf("http://[::1]:80"))
        assertEquals("host", httpHostOf("https://user:pw@host/p"))
        assertNull(httpHostOf("mailto:a@b.c"))
    }

    @Test
    fun httpPathOf_returnsTrimmedPath() {
        mapOf(
            "https://api.openai.com" to "",
            "https://api.openai.com/" to "",
            "https://api.openai.com/v1" to "v1",
            "https://h/a/b/?q=1" to "a/b",
            "https://h?x=/y" to "",
            "not a url" to "",
        ).forEach { (url, expected) -> assertEquals(expected, httpPathOf(url), url) }
    }
}
