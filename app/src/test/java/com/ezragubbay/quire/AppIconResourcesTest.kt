package com.ezragubbay.quire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.hypot

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
    fun foregroundUsesQuireLogo() {
        val text = File(res, "drawable/ic_launcher_foreground.xml").readText()
        assertTrue(text.contains("@drawable/ic_quire_logo"))
        assertTrue(File(res, "drawable/ic_quire_logo.xml").isFile)
    }

    @Test
    fun placeholderLogoIsGone() {
        assertFalse(File(res, "drawable/ic_logo_placeholder.xml").exists())
        val stale = "ic_logo_" + "placeholder"
        listOf(File("src/main/res"), File("src/main/java")).forEach { root ->
            root
                .walkTopDown()
                .filter { it.isFile }
                .forEach { assertFalse("${it.path} mentions the removed logo", it.readText().contains(stale)) }
        }
    }

    @Test
    fun logoMatchesWebBrand() {
        val vector = parse(File(res, "drawable/ic_quire_logo.xml")).documentElement
        assertEquals("108dp", androidAttr(vector, "width"))
        assertEquals("108dp", androidAttr(vector, "height"))
        assertEquals("108", androidAttr(vector, "viewportWidth"))
        assertEquals("108", androidAttr(vector, "viewportHeight"))
        val paths = vector.getElementsByTagName("path")
        assertTrue(paths.length > 0)
        (0 until paths.length).forEach {
            assertEquals("#F6F1E7", androidAttr(paths.item(it) as Element, "fillColor").uppercase())
        }
        val colors = parse(File(res, "values/colors.xml")).getElementsByTagName("color")
        val background =
            (0 until colors.length)
                .map { colors.item(it) as Element }
                .single { it.getAttribute("name") == "ic_launcher_background" }
        assertEquals("#6E1E2B", background.textContent.trim().uppercase())
    }

    /** The adaptive mask crops to a 66dp circle centred on the 108dp canvas; nothing of the Q may leave it. */
    @Test
    fun logoStaysInsideAdaptiveSafeZone() {
        val points = pathPoints(File(res, "drawable/ic_quire_logo.xml"))
        assertTrue(points.isNotEmpty())
        points.forEach { (x, y) ->
            val distance = hypot(x - 54.0, y - 54.0)
            assertTrue("($x,$y) is ${"%.2f".format(distance)}dp from centre; safe radius is 33", distance <= 33.0)
        }
        val height = points.maxOf { it.second } - points.minOf { it.second }
        assertTrue("glyph is only ${height}dp tall", height >= 30.0)
    }

    @Test
    fun notificationIconIsMonochrome24dp() {
        val file = File(res, "drawable/ic_stat_quire.xml")
        val vector = parse(file).documentElement
        assertEquals("24dp", androidAttr(vector, "width"))
        assertEquals("24dp", androidAttr(vector, "height"))
        assertEquals("24", androidAttr(vector, "viewportWidth"))
        assertEquals("24", androidAttr(vector, "viewportHeight"))
        assertFalse(file.readText().contains("ic_logo_placeholder"))
        val points = pathPoints(file)
        assertTrue(points.isNotEmpty())
        points.forEach { (x, y) -> assertTrue("($x,$y) outside the 24dp viewport", x in 0.0..24.0 && y in 0.0..24.0) }
    }

    @Test
    fun splashThemeDefinedInLightAndNight() {
        listOf("values", "values-night").forEach { dir ->
            val style = splashStyle(File(res, "$dir/themes.xml"))
            assertEquals(dir, "Theme.SplashScreen", style.getAttribute("parent"))
            val items = itemsByName(style)
            assertEquals(dir, "@color/splash_background", items["windowSplashScreenBackground"])
            assertEquals(dir, "@drawable/ic_quire_logo", items["windowSplashScreenAnimatedIcon"])
            // The cream mark needs the brand disc behind it to be visible on the parchment splash.
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

    /** Every coordinate of every `<path>` (control points bound a Bezier), absolute M/L/H/V/Q/Z only. */
    private fun pathPoints(file: File): List<Pair<Double, Double>> {
        val paths = parse(file).getElementsByTagName("path")
        return (0 until paths.length).flatMap { index ->
            pathDataPoints(androidAttr(paths.item(index) as Element, "pathData"))
        }
    }

    private fun pathDataPoints(data: String): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        var x = 0.0
        var y = 0.0
        Regex("([MLHVQZ])([^MLHVQZ]*)").findAll(data).forEach { token ->
            val nums = Regex("-?\\d*\\.?\\d+").findAll(token.groupValues[2]).map { it.value.toDouble() }.toList()
            when (token.groupValues[1]) {
                "H" -> nums.forEach { x = it }.also { points += x to y }
                "V" -> nums.forEach { y = it }.also { points += x to y }
                "M", "L", "Q" ->
                    nums.chunked(2).forEach { (px, py) ->
                        x = px
                        y = py
                        points += x to y
                    }
            }
        }
        return points
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
