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
 * @property onOpenSession a brew row of the stock history: that session's page (SCR-16, SCR-29); null where the
 *   detail has none to open (its brew rows then open nothing). LETZTE SITZUNGEN's rows use it too (TEA-13).
 * @property onShowAllSessions LETZTE SITZUNGEN's *Alle anzeigen*: the Journal tab filtered to this tea (TEA-13,
 *   JRN-09); null where the detail has none (above the setup), where the button is hidden.
 * @property onOpenCatalog ⋮ *Im Katalog ansehen* (D-114 Q10): the tea's catalog page (SCR-21) by its catalog key,
 *   above the detail; back returns.
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
    val onOpenSession: ((sessionId: String) -> Unit)? = null,
    val onShowAllSessions: (() -> Unit)? = null,
    val onOpenCatalog: (catalogKey: String) -> Unit = {},
)
