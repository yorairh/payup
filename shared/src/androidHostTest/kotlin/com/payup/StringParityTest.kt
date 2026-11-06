package com.payup

import com.payup.domain.AppLanguage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.Element

/**
 * Every language must have exactly the same strings as English, with the same placeholders, and the plural
 * forms its language needs. Runs on the JVM because it reads the resource files from the source tree.
 */
class StringParityTest {

    /** Resource folder name per language tag. Hebrew is `he` (not the old Android `iw`), because Compose resources need it. */
    private val folders = mapOf(
        "en" to "values", "he" to "values-he", "es" to "values-es", "ru" to "values-ru", "ar" to "values-ar",
        "fr" to "values-fr", "de" to "values-de", "pt" to "values-pt", "zh-CN" to "values-zh-rCN",
    )

    /** The plural categories a language needs. "other" is always required; Compose falls back to it. */
    private val requiredPlurals = mapOf(
        "ru" to setOf("one", "few", "many", "other"),
        "ar" to setOf("zero", "one", "two", "few", "many", "other"),
        "he" to setOf("one", "two", "other"),
        "zh-CN" to setOf("other"), // Chinese has no plural forms
    ).withDefault { setOf("one", "other") }

    private class Entry(val plural: Boolean, val items: Map<String, String>)

    private fun root(): File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "shared/src/commonMain/composeResources") }
        .firstOrNull { it.isDirectory }
        ?: File("src/commonMain/composeResources").absoluteFile

    private fun load(folder: String): Map<String, Entry> {
        val file = File(root(), "$folder/strings.xml")
        assertTrue(file.isFile, "Missing ${file.path}")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = LinkedHashMap<String, Entry>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            val name = e.getAttribute("name")
            when (e.tagName) {
                "string" -> out[name] = Entry(false, mapOf("" to e.textContent))
                "plurals" -> {
                    val items = LinkedHashMap<String, String>()
                    val kids = e.getElementsByTagName("item")
                    for (j in 0 until kids.length) {
                        val item = kids.item(j) as Element
                        items[item.getAttribute("quantity")] = item.textContent
                    }
                    out[name] = Entry(true, items)
                }
                else -> error("Unexpected <${e.tagName}> in $folder")
            }
        }
        return out
    }

    private val placeholder = Regex("""%\d+\$[sd]""")
    private fun placeholders(text: String) = placeholder.findAll(text).map { it.value }.toSet()

    @Test
    fun everyLanguageHasTheSameKeysAsEnglish() {
        val english = load("values")
        folders.forEach { (tag, folder) ->
            val other = load(folder)
            assertEquals(english.keys, other.keys, "$tag: keys differ from English (missing=${english.keys - other.keys}, extra=${other.keys - english.keys})")
            english.forEach { (key, en) ->
                assertEquals(en.plural, other.getValue(key).plural, "$tag/$key: plural vs string")
            }
        }
    }

    @Test
    fun placeholdersMatchEnglish() {
        val english = load("values")
        folders.forEach { (tag, folder) ->
            val other = load(folder)
            english.forEach { (key, en) ->
                if (en.plural) return@forEach
                assertEquals(placeholders(en.items.getValue("")), placeholders(other.getValue(key).items.getValue("")), "$tag/$key: placeholders differ")
            }
        }
    }

    @Test
    fun pluralsHaveTheFormsTheirLanguageNeeds() {
        folders.forEach { (tag, folder) ->
            load(folder).forEach { (key, entry) ->
                if (!entry.plural) return@forEach
                val missing = requiredPlurals.getValue(tag) - entry.items.keys
                assertTrue(missing.isEmpty(), "$tag/$key: missing plural forms $missing")
                entry.items.forEach { (quantity, text) ->
                    assertTrue(placeholders(text).size <= 1, "$tag/$key/$quantity: more than one placeholder")
                }
            }
        }
    }

    @Test
    fun noAndroidOnlySyntaxLeftBehind() {
        val bs = '\\'
        folders.forEach { (tag, folder) ->
            load(folder).forEach { (key, entry) ->
                entry.items.forEach { (quantity, text) ->
                    val where = "$tag/$key/$quantity"
                    assertTrue(!text.contains("$bs'") && !text.contains("$bs\""), "$where: escaped quote")
                    assertTrue(!text.contains("%%"), "$where: doubled percent")
                    assertTrue(!Regex("""%(?!\d+\$[sd])[sd]""").containsMatchIn(text), "$where: non-positional placeholder")
                    assertTrue(text.isNotBlank(), "$where: empty")
                }
            }
        }
    }

    /**
     * Android can report Hebrew as the old code "iw", and Compose resources match the folder name exactly, while the
     * web reports "he". So the same Hebrew strings are kept under both names.
     */
    @Test
    fun hebrewIsIdenticalUnderBothCodes() {
        val he = File(root(), "values-he/strings.xml").readText()
        val iw = File(root(), "values-iw/strings.xml").readText()
        assertEquals(he, iw, "values-iw must be an exact copy of values-he")
    }

    @Test
    fun languageListMatchesFoldersAndAndroidLocaleConfig() {
        assertEquals(folders.keys, AppLanguage.OPTIONS.map { it.tag }.toSet(), "AppLanguage.OPTIONS vs resource folders")
        val config = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "androidApp/src/main/res/xml/locales_config.xml") }
            .firstOrNull { it.isFile }
        assertTrue(config != null, "locales_config.xml not found")
        val tags = Regex("android:name=\"([^\"]+)\"").findAll(config.readText()).map { it.groupValues[1] }.toSet()
        assertEquals(folders.keys, tags, "locales_config.xml vs resource folders")
    }
}
