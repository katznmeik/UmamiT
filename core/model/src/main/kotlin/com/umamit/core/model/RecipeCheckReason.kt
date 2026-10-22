package com.umamit.core.model

/** Why a tea's recipe should be checked (05 §5, CAT-05, TEA-17). */
enum class RecipeCheckReason {
    FROM_TEXT,
    FROM_STYLE_DEFAULT,
    TABLE_TEXT_CONFLICT,
    IMPLAUSIBLE_VALUE,
    UNMAPPED_STYLE,
}
