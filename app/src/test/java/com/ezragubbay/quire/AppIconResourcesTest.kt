package com.ezragubbay.quire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Asserts the app-identity wiring (launcher icon, themed icon, splash) straight from the XML sources.
 * Plain JVM parsing keeps this independent of a Robolectric harness; AGP/lint verifies the references resolve.
 */
class AppIconResourcesTest {
    private val res = File("src/main/res")

    private fun parse(file: File): Document {
        assertTrue("missing ${file.path}", file.isFile)
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    }

    private fun application(): Element =
        parse(File("src/main/AndroidManifest.xml")).getElementsByTagName("application").item(0) as Element

    private fun androidAttr(
        el: Element,
        name: String,
    ): String = el.getAttribute("android:$name")

    @Test
    fun manifestReferencesAdaptiveIconAndSplashTheme() {
        val app = application()
        assertEquals("@mipmap/ic_launcher", androidAttr(app, "icon"))
        assertEquals("@mipmap/ic_launcher_round", androidAttr(app, "roundIcon"))
        assertEquals("@style/Theme.Quire.Starting", androidAttr(app, "theme"))
    }

    @Test
    fun adaptiveIconsDeclareForegroundBackgroundAndMonochrome() {
        listOf("ic_launcher.xml", "ic_launcher_round.xml").forEach { name ->
            val root = parse(File(res, "mipmap-anydpi/$name")).documentElement
            assertEquals(name, "adaptive-icon", root.tagName)
            assertEquals("@color/ic_launcher_background", androidAttr(childElement(root, "background"), "drawable"))
            assertEquals("@drawable/ic_launcher_foreground", androidAttr(childElement(root, "foreground"), "drawable"))
            assertEquals("@drawable/ic_launcher_foreground", androidAttr(childElement(root, "monochrome"), "drawable"))
        }
    }

    @Test
    fun foregroundReusesThePlaceholderLogo() {
        val text = File(res, "drawable/ic_launcher_foreground.xml").readText()
        assertTrue(text.contains("@drawable/ic_logo_placeholder"))
        assertTrue(File(res, "drawable/ic_logo_placeholder.xml").isFile)
    }

    @Test
    fun splashThemeDefinedInLightAndNight() {
        listOf("values", "values-night").forEach { dir ->
            val style = splashStyle(File(res, "$dir/themes.xml"))
            assertEquals(dir, "Theme.SplashScreen", style.getAttribute("parent"))
            val items = itemsByName(style)
            assertEquals(dir, "@color/splash_background", items["windowSplashScreenBackground"])
            assertEquals(dir, "@drawable/ic_logo_placeholder", items["windowSplashScreenAnimatedIcon"])
            // The white mark needs the brand disc behind it to be visible on the parchment splash.
            assertEquals(dir, "@color/ic_launcher_background", items["windowSplashScreenIconBackgroundColor"])
            assertEquals(dir, "@style/Theme.Quire", items["postSplashScreenTheme"])
        }
    }

    /** Platform views (Markwon TextViews) take links and selection handles from the window theme, not Compose. */
    @Test
    fun appThemeTintsLinksAndSelectionWithBrandPrimary() {
        mapOf("values" to "#6E1E2B", "values-night" to "#E28A93").forEach { (dir, primary) ->
            val styles = parse(File(res, "$dir/themes.xml")).getElementsByTagName("style")
            val app =
                (0 until styles.length)
                    .map { styles.item(it) as Element }
                    .single { it.getAttribute("name") == "Theme.Quire" }
            val items = itemsByName(app)
            assertEquals(dir, "@color/brand_primary", items["android:colorAccent"])
            assertEquals(dir, "@color/brand_primary", items["android:textColorLink"])
            val colors = parse(File(res, "$dir/colors.xml")).getElementsByTagName("color")
            val brand =
                (0 until colors.length)
                    .map { colors.item(it) as Element }
                    .single { it.getAttribute("name") == "brand_primary" }
            assertEquals(dir, primary, brand.textContent.trim())
        }
    }

    @Test
    fun licenseShippedWithBundledFonts() {
        assertTrue(File("src/main/assets/fonts/OFL.txt").readText().contains("SIL OPEN FONT LICENSE Version 1.1"))
    }

    private fun childElement(
        parent: Element,
        tag: String,
    ): Element {
        val nodes = parent.getElementsByTagName(tag)
        assertTrue("missing <$tag>", nodes.length > 0)
        return nodes.item(0) as Element
    }

    private fun splashStyle(file: File): Element {
        val styles = parse(file).getElementsByTagName("style")
        return (0 until styles.length)
            .map { styles.item(it) as Element }
            .single { it.getAttribute("name") == "Theme.Quire.Starting" }
    }

    private fun itemsByName(style: Element): Map<String, String> {
        val items = style.getElementsByTagName("item")
        return (0 until items.length)
            .map { items.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent.trim() }
    }
}
