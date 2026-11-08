package com.umamit.core.testing

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.umamit.core.ui.PhotoPicker

/**
 * The photo picker port in tests (06 §5.2), where the system picker can't run: each launch answers at once with
 * [next] (null: the user backed out), without starting an activity. Pass it to the route's `photoPicker`.
 */
class FakePhotoPicker(var next: Uri? = null) : PhotoPicker() {
    /** How often the picker was opened. */
    var launches = 0
        private set

    override fun createIntent(context: Context, input: Unit): Intent = Intent()

    override fun getSynchronousResult(context: Context, input: Unit): SynchronousResult<Uri?> {
        launches++
        return SynchronousResult(next)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = next
}
