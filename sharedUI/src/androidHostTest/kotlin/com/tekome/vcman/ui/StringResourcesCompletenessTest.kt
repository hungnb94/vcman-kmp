package com.tekome.vcman.ui

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertTrue

class StringResourcesCompletenessTest {
    private val resourcesDir: File = locateResourcesDir()

    private fun locateResourcesDir(): File {
        val relative = "src/commonMain/composeResources"
        val candidates =
            listOfNotNull(
                System.getProperty("vcman.composeResourcesDir")?.let(::File),
                File(relative),
                File("sharedUI/$relative"),
            )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("compose resources not found; tried ${candidates.map { it.absolutePath }}")
    }

    private fun readStrings(dir: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(dir, "strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent }
    }

    private val placeholder = Regex("%\\d+\\$[a-zA-Z]")

    private fun placeholders(text: String): List<String> = placeholder.findAll(text).map { it.value }.sorted().toList()

    @Test
    fun everyLocaleMatchesDefaultStrings() {
        val default = readStrings(File(resourcesDir, "values"))
        val localeDirs = resourcesDir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }.orEmpty().sortedBy { it.name }
        val problems = mutableListOf<String>()

        assertTrue(localeDirs.isNotEmpty(), "no values-* directory found in ${resourcesDir.absolutePath}")

        default.forEach { (key, text) ->
            if (text.isBlank()) problems += "values/$key is blank"
        }
        localeDirs.forEach { dir ->
            val strings = readStrings(dir)
            (default.keys - strings.keys).forEach { problems += "${dir.name}: missing key $it" }
            (strings.keys - default.keys).forEach { problems += "${dir.name}: unknown key $it" }
            strings.forEach { (key, text) ->
                if (text.isBlank()) problems += "${dir.name}/$key is blank"
                val expected = default[key]?.let(::placeholders)
                if (expected != null && placeholders(text) != expected) {
                    problems += "${dir.name}/$key placeholders ${placeholders(text)} != $expected"
                }
            }
            // Android-style qualifier: `pt-rBR` is language `pt` + region `BR`, i.e. tag `pt-BR`.
            val language = dir.name.removePrefix("values-").replace("-r", "-")
            val tag = strings["content_language_tag"]
            if (tag != null && !tag.startsWith(language, ignoreCase = true)) {
                problems += "${dir.name}: content_language_tag '$tag' does not start with '$language'"
            }
        }

        assertTrue(problems.isEmpty(), "string resource problems:\n" + problems.joinToString("\n"))
    }
}
