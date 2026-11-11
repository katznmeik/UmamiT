package com.umamit.feature.journal.session

/**
 * What the session editor (SCR-17) gets from the app.
 *
 * @property onClose ✕ and back (after „Änderungen verwerfen?" when something changed).
 * @property onSaved *Speichern* wrote the edit: the app returns to the session page, which shows the new values.
 * @property alertKey the alert playing now, or null: the pickers, the menu and the question give way to another
 *   (rule 8).
 */
class SessionEditorHost(val onClose: () -> Unit, val onSaved: () -> Unit = onClose, val alertKey: String? = null)
