package com.umamit.core.catalog.parser

/**
 * The patterns of a brewing instruction in the shop's prose (08 §2.3, §2.5): temperatures
 * („60 °C", „65-75°C"), times („90 Sekunden", „20 - 30 Minuten", „4 Stunden") and amounts per
 * volume („4 g auf 100 ml", „10g/L", „2 TL pro Liter"). Shared by the parser, which picks the
 * paragraphs, and the recipe derivation, which reads their values.
 */
internal object BrewProse {
    private const val NUMBER = """\d+(?:[.,]\d+)?"""
    private const val RANGE = """($NUMBER)(?:\s*(?:-|–|bis)\s*($NUMBER))?"""
    private const val NO_LETTER_AFTER = """(?![\p{L}])"""
    private const val NO_LETTER_BEFORE = """(?<![\p{L}])"""

    /** „60 °C", „65-75°C", „~80°" (the C missing, live sample 2): groups 1 and 2 the range. */
    val TEMPERATURE = Regex("""$RANGE\s*°\s*C?$NO_LETTER_AFTER""", RegexOption.IGNORE_CASE)

    /** A grid's alternative in parentheses („20(30) Sek", live sample 2): the first value counts. */
    private const val ALTERNATIVE = """(?:\s*\(\s*$NUMBER\s*\))?"""

    /**
     * „90 Sekunden", „~1 Min", „20 - 30 Minuten", „4 Std.", „20(30) Sek": groups 1 and 2 the
     * range, 3 the unit.
     */
    val TIME = Regex(
        """$RANGE$ALTERNATIVE\s*""" +
            """(sekunden|sekunde|sek\.?|sec|minuten|minute|min\.?|stunden|stunde|std\.?)$NO_LETTER_AFTER""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * „4 g auf 100 ml", „10g/L", „2 TL pro Liter": groups 1 and 2 the amount's range, 3 its unit
     * (g or teaspoons), 4 the volume (absent for „Liter"), 5 the volume's unit.
     */
    val AMOUNT_PER_VOLUME = Regex(
        """$RANGE\s*(g|gramm|tl|teelöffel)$NO_LETTER_AFTER[^.\d]{0,30}?""" +
            """(?:($NUMBER)\s*)?$NO_LETTER_BEFORE(ml|l|liter)$NO_LETTER_AFTER""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Tea bags for a volume („1Stk./500ml", „Einen Beutel auf 0,3–0,5 Liter", „pro Beutel 1 Liter";
     * live sample 2): group 1 the count (absent for „ein", „pro", „je": one), 2 and 3 the
     * volume's range (absent for „Liter"), 4 its unit.
     */
    val BAGS_PER_VOLUME = Regex(
        """(?:($NUMBER)\s*|$NO_LETTER_BEFORE(?:einen|ein|pro|je)\s+)(?:stk\.?|stück|beutel)$NO_LETTER_AFTER""" +
            """[^.\d]{0,30}?(?:$RANGE\s*)?$NO_LETTER_BEFORE(ml|l|liter)$NO_LETTER_AFTER""",
        RegexOption.IGNORE_CASE,
    )

    /** A video pointer („In diesem Video zeigen wir …"): never a brewing instruction, whatever it names. */
    private val VIDEO = Regex("""video""", RegexOption.IGNORE_CASE)

    /** Words of a brewing instruction; alone („Der Aufguss hat …") they are none. */
    private val KEYWORD =
        Regex("""zubereit|aufguss|aufgüss|aufgie|aufbrüh|ziehzeit|ziehen|dosierung""", RegexOption.IGNORE_CASE)

    /** A Kaltaufguss (cold brew) is described. */
    val COLD = Regex(
        """kaltaufguss|kalt\s+aufg|kaltem\s+wasser|kaltes\s+wasser|cold\s*-?\s*brew|kühlschrank""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Whether [paragraph] is a brewing instruction: two of temperature, time and amount per
     * volume (in grams, teaspoons or bags), or one of them with a brewing word. Tasting notes
     * about the liquor („Aufguss"), video pointers (whatever values they name, live sample 2),
     * package sizes and storage hints are not (live sample 1); when unsure, a paragraph is left
     * out, as a wrong value from the text is worse than the style default.
     */
    fun isInstruction(paragraph: String): Boolean {
        if (VIDEO.containsMatchIn(paragraph)) return false
        val amount = AMOUNT_PER_VOLUME.containsMatchIn(paragraph) || BAGS_PER_VOLUME.containsMatchIn(paragraph)
        val values = listOf(TEMPERATURE, TIME).count { it.containsMatchIn(paragraph) } + if (amount) 1 else 0
        return values >= 2 || (values == 1 && KEYWORD.containsMatchIn(paragraph))
    }
}
