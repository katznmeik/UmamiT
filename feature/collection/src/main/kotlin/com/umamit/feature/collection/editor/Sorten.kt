package com.umamit.feature.collection.editor

import android.content.Context
import androidx.annotation.StringRes
import com.umamit.core.domain.search.SearchQuery
import com.umamit.core.domain.style.BuiltInStyles
import com.umamit.core.model.TeaCategory
import com.umamit.feature.collection.R
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** The style key of *Andere / unbekannt* (08 §3); a tea of it is stored without a style (05 §3.1: null = other). */
const val OTHER_STYLE_KEY = "other"

/**
 * The German *Sorte* name of a built-in style (08 §3, glossary *Sorte*), e.g. „Gyokuro" for `gyokuro`; null for a
 * key that is not a built-in style. [TeaStyle][com.umamit.core.domain.style.TeaStyle] holds no German names.
 */
@StringRes
fun sorteNameRes(styleKey: String): Int? = SORTE_NAMES[styleKey]

private val SORTE_NAMES: Map<String, Int> = mapOf(
    "gyokuro" to R.string.sorte_gyokuro,
    "kabusecha" to R.string.sorte_kabusecha,
    "sencha" to R.string.sorte_sencha,
    "fukamushi_sencha" to R.string.sorte_fukamushi_sencha,
    "shincha" to R.string.sorte_shincha,
    "matcha" to R.string.sorte_matcha,
    "karigane" to R.string.sorte_karigane,
    "bancha" to R.string.sorte_bancha,
    "hojicha" to R.string.sorte_hojicha,
    "hojicha_powder" to R.string.sorte_hojicha_powder,
    "genmaicha" to R.string.sorte_genmaicha,
    "kamairicha" to R.string.sorte_kamairicha,
    "tamaryokucha" to R.string.sorte_tamaryokucha,
    "longjing" to R.string.sorte_longjing,
    "anji_bai_cha" to R.string.sorte_anji_bai_cha,
    "bi_luo_chun" to R.string.sorte_bi_luo_chun,
    "korean_green" to R.string.sorte_korean_green,
    "flavored_green" to R.string.sorte_flavored_green,
    "green_generic" to R.string.sorte_green_generic,
    "herbal" to R.string.sorte_herbal,
    "white_generic" to R.string.sorte_white_generic,
    "oolong_generic" to R.string.sorte_oolong_generic,
    "black_generic" to R.string.sorte_black_generic,
    "dark_generic" to R.string.sorte_dark_generic,
    OTHER_STYLE_KEY to R.string.sorte_other,
)

/** The Sorte names the tea editor stores as a tea's *Sorte* (`tea_type`) when its style is chosen. */
fun interface SorteNames {
    /** The German Sorte name of [styleKey], or null for a key that is not a built-in style. */
    fun name(styleKey: String): String?
}

/** [SorteNames] from the app's strings. */
class ResourceSorteNames @Inject constructor(@ApplicationContext private val context: Context) : SorteNames {
    override fun name(styleKey: String): String? = sorteNameRes(styleKey)?.let(context::getString)
}

/** Binds [SorteNames] to the strings. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SorteNamesModule {
    @Binds
    abstract fun sorteNames(impl: ResourceSorteNames): SorteNames
}

/** A Kategorie's styles in *Sorte wählen*; [category] null: *Andere / unbekannt* at the end, without a heading. */
data class SorteGroup(val category: TeaCategory?, val styleKeys: List<String>)

/** *Sorte wählen*'s list (SCR-11, TEA-01, D-100) as a pure function. */
object SortePicker {
    /** The Kategorien in 03 SCR-11's order. */
    private val ORDER = listOf(
        TeaCategory.GREEN,
        TeaCategory.WHITE,
        TeaCategory.OOLONG,
        TeaCategory.BLACK,
        TeaCategory.DARK,
        TeaCategory.HERBAL,
    )

    /**
     * Every built-in style by Kategorie in 08 §3's order, with `other` alone at the end; with a [query], only the
     * styles whose Sorte name or Kategorie matches it (GEN-10: word starts, ignoring case and accents), and no
     * empty group.
     */
    fun groups(query: String, sorteName: (String) -> String, categoryName: (TeaCategory) -> String): List<SorteGroup> {
        val search = SearchQuery(query)
        val styles = BuiltInStyles.all
        val groups = ORDER.map { category ->
            SorteGroup(category, styles.filter { it.category == category && it.key != OTHER_STYLE_KEY }.map { it.key })
        } + SorteGroup(null, listOf(OTHER_STYLE_KEY))
        return groups.map { group ->
            val categoryWords = group.category?.let { SearchQuery.words(categoryName(it)) }.orEmpty()
            group.copy(
                styleKeys = group.styleKeys.filter { key ->
                    search.isEmpty || search.matches(SearchQuery.words(sorteName(key)) + categoryWords)
                },
            )
        }.filter { it.styleKeys.isNotEmpty() }
    }
}

/** Test tags of SCR-11. */
object SortePickerTags {
    const val SEARCH = "SortePickerSearch"

    /** A style's row: `SortePickerStyle_<key>`. */
    fun style(key: String) = "SortePickerStyle_$key"
}
