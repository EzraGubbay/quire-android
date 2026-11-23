package com.ezragubbay.quire.feature.notes

import com.ezragubbay.quire.testing.Smoke
import org.junit.Assert.assertTrue
import org.junit.Test
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula

/**
 * JLaTeXMath typesets many everyday commands (`\dfrac`, `\operatorname`, `\textbf`, the `cases`/`pmatrix`/`aligned`
 * environments) through macros it resolves by class and method name at runtime. Under R8 those were renamed and
 * removed, so in the release build every such formula failed and showed as raw source. Part of the minified smoke run.
 */
class LatexEngineTest {
    @Test
    @Smoke("chat-math")
    fun reflectiveMacros_typesetInThisBuild() {
        FORMULAS.forEach { latex ->
            val icon = TeXFormula(latex).createTeXIcon(TeXConstants.STYLE_DISPLAY, TEXT_SIZE)
            assertTrue("empty render for $latex", icon.iconWidth > 0 && icon.iconHeight > 0)
        }
    }

    private companion object {
        const val TEXT_SIZE = 40f
        val FORMULAS =
            listOf(
                "\\int_{-\\infty}^{\\infty} f(\\tau)\\, g(t - \\tau)\\, d\\tau",
                "\\dfrac{1}{2} + \\tfrac{a}{b}",
                "\\operatorname{rank}(A) = \\textbf{n}",
                "f(x) = \\begin{cases} 1 & x > 0 \\\\ 0 & \\text{otherwise} \\end{cases}",
                "\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}",
                "\\begin{aligned} a &= b \\\\ c &= d \\end{aligned}",
            )
    }
}
