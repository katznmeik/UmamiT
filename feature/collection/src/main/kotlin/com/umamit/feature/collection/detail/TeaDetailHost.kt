package com.umamit.feature.collection.detail

/**
 * What the tea detail's host wires (SCR-09). The editors (SCR-10, SCR-12) are reached through [onEditTea],
 * [onEditRecipe] and [onAddRecipe].
 *
 * @property onBack back to where the detail was opened.
 * @property onBrew *Brühen* (recipe null: the default) or a recipe's tap: the setup (SCR-04) of this tea.
 * @property onArchived the tea was archived: the host closes the detail and shows „*Tanabata* archiviert" ·
 *   *Rückgängig* on the screen below (D-100).
 * @property onDeleted the tea was deleted: the host closes the detail.
 * @property alertKey the alert playing now, or null: the menu, the question and the sheet give way (rule 8).
 */
class TeaDetailHost(
    val onBack: () -> Unit,
    val onBrew: (recipeId: String?) -> Unit,
    val onArchived: (ArchivedTea) -> Unit,
    val onDeleted: () -> Unit,
    val alertKey: String? = null,
    val onEditTea: () -> Unit = {},
    val onEditRecipe: (recipeId: String) -> Unit = {},
    val onAddRecipe: () -> Unit = {},
)
