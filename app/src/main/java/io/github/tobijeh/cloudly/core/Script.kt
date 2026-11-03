package io.github.tobijeh.cloudly.core

enum class Script { LATIN, HEBREW, CHINESE }

object Scripts {
    /**
     * Picks the font script for [text] (Fraunces / Frank Ruhl Libre / Noto Serif TC). Any Chinese character
     * selects Noto Serif TC and any Hebrew character selects Frank Ruhl Libre, since both also cover Latin,
     * while Fraunces has no Hebrew or Chinese glyphs.
     */
    fun detect(text: String): Script {
        var hebrew = false
        var han = false
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isHan(cp)) han = true else if (isHebrew(cp)) hebrew = true
            i += Character.charCount(cp)
        }
        return when {
            han -> Script.CHINESE
            hebrew -> Script.HEBREW
            else -> Script.LATIN
        }
    }

    // Hebrew block (letters, vowel points, cantillation) and Hebrew presentation forms.
    fun isHebrew(cp: Int) = cp in 0x0590..0x05FF || cp in 0xFB1D..0xFB4F

    fun isHan(cp: Int) = cp in 0x4E00..0x9FFF || cp in 0x3400..0x4DBF || cp in 0x20000..0x2EBEF ||
        cp in 0xF900..0xFAFF || cp in 0x3000..0x303F || cp in 0x3100..0x312F
}
