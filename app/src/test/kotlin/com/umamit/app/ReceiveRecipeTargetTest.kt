package com.umamit.app

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.backup.recipe.RecipeFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch

/**
 * Receiving a recipe file (PRF-08 (a) and (b), 06 §5.7, D-130 item 18): in the merged manifest the activity answers
 * only `VIEW` and `SEND` with the recipe file's type and `application/json`, exported without a permission; it hands
 * the URI with its read grant to MainActivity in the app's own task and reads nothing itself (D-133 Q4); MainActivity
 * copies at most 64 KB + 1 byte of a `content://` URI off the main thread; a `file://` URI or one of the app's own
 * providers reads as empty (the reader refuses it); MainActivity keeps the file until SCR-32 closes. Robolectric's
 * grant model is thin: the grant itself is a 09 §4 check on the phone. Synthetic bytes (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReceiveRecipeTargetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val target = ComponentName(context, ReceiveRecipeActivity::class.java)

    @Before
    fun resetProvider() {
        BytesProvider.opened = 0
        BytesProvider.typeAsked = 0
        BytesProvider.type = RecipeFile.MIME_TYPE
    }

    private fun targetsOf(intent: Intent): List<ComponentName> =
        context.packageManager.queryIntentActivities(intent.setPackage(context.packageName), PackageManager.MATCH_ALL)
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }

    /** A provider serving [bytes] at every URI of [AUTHORITY]. */
    private fun serve(bytes: ByteArray) {
        val file = File(context.cacheDir, "received.bin").apply { writeBytes(bytes) }
        Robolectric.buildContentProvider(BytesProvider::class.java).create(AUTHORITY).get().file = file
    }

    private fun handedOver(intent: Intent): Intent? {
        val activity = Robolectric.buildActivity(ReceiveRecipeActivity::class.java, intent).create().get()
        assertThat(activity.isFinishing).isTrue()
        return shadowOf(activity).nextStartedActivity
    }

    @Test
    fun theManifestOffersViewAndSendForBothTypesOnly() {
        val uri = Uri.parse("content://$AUTHORITY/Tee.umamit-rezept.json")
        listOf(RecipeFile.MIME_TYPE, "application/json").forEach { type ->
            assertThat(targetsOf(Intent(Intent.ACTION_VIEW).setDataAndType(uri, type))).containsExactly(target)
            assertThat(targetsOf(Intent(Intent.ACTION_SEND).setType(type))).containsExactly(target)
        }
        val others = listOf(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/octet-stream"),
            Intent(Intent.ACTION_SEND).setType("application/octet-stream"),
            Intent(Intent.ACTION_SEND).setType("text/plain"),
            Intent(Intent.ACTION_SEND_MULTIPLE).setType(RecipeFile.MIME_TYPE),
            Intent(Intent.ACTION_EDIT).setDataAndType(uri, RecipeFile.MIME_TYPE),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tee-kontor-kiel.de/nebelhang-sencha/XY0170")),
        )
        others.forEach { assertThat(targetsOf(it)).doesNotContain(target) }
        val info = context.packageManager.getActivityInfo(target, 0)
        assertThat(info.exported).isTrue()
        assertThat(info.permission).isNull()
    }

    /** [MainActivity]'s files with the copy run at once on the calling thread. */
    private fun files(newToken: () -> Long = { 42L }) =
        ReceivedRecipeFiles(newToken = newToken, io = Dispatchers.Unconfined)

    /** What MainActivity gets from [started]: the bytes its off-main-thread copy lands as pending, or null. */
    private fun copied(started: Intent): ByteArray? = files().run {
        assertThat(offer(started, RecipeFileSource.of(context))).isTrue()
        pending.value?.bytes
    }

    @Test
    fun aViewedOrSentFileGoesToTheAppsOwnTaskWithItsUriAndGrant() {
        val bytes = """{"format":"umamit-recipe"}""".toByteArray()
        serve(bytes)
        val uri = Uri.parse("content://$AUTHORITY/Tee.umamit-rezept.json")

        listOf(
            ReceiveRecipeActivity.view(context, uri),
            ReceiveRecipeActivity.view(context, uri, "application/json"),
            ReceiveRecipeActivity.send(context, uri),
            ReceiveRecipeActivity.send(context, uri, "application/json"),
        ).forEach { intent ->
            val started = requireNotNull(handedOver(intent))
            assertThat(started.component).isEqualTo(ComponentName(context, MainActivity::class.java))
            assertThat(started.action).isEqualTo(ReceivedRecipeFiles.ACTION_OPEN_RECIPE)
            // The grant is passed on with the URI; the receiving step reads nothing itself (D-133 Q4).
            assertThat(started.data).isEqualTo(uri)
            assertThat(started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
            assertThat(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
            assertThat(started.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP).isNotEqualTo(0)
            assertThat(started.hasExtra("com.umamit.app.extra.RECIPE_BYTES")).isFalse()
            assertThat(copied(started)).isEqualTo(bytes)
        }
        assertThat(BytesProvider.opened).isEqualTo(4)
    }

    @Test
    fun theReceivingStepMakesNoCallIntoTheProviderAndAViewWithoutATypeIsCheckedOnTheCopy() {
        serve("""{"format":"umamit-recipe"}""".toByteArray())
        val uri = Uri.parse("content://$AUTHORITY/untyped")

        val started = requireNotNull(handedOver(ReceiveRecipeActivity.view(context, uri, type = null)))

        // No getType on the main thread: the provider is asked only by MainActivity's copy.
        assertThat(BytesProvider.typeAsked).isEqualTo(0)
        assertThat(BytesProvider.opened).isEqualTo(0)
        assertThat(started.getStringExtra(ReceivedRecipeFiles.EXTRA_TYPE)).isNull()
        assertThat(copied(started)).isNotEmpty()
        assertThat(BytesProvider.typeAsked).isEqualTo(1)
        // A provider type that is no recipe type: nothing arrives.
        BytesProvider.type = "image/png"
        assertThat(copied(started)).isNull()
    }

    @Test
    fun aFileOver64KbIsCutJustOverTheLimit() {
        serve(ByteArray(RecipeFile.MAX_BYTES * 3) { 'x'.code.toByte() })

        val started = handedOver(ReceiveRecipeActivity.send(context, Uri.parse("content://$AUTHORITY/big")))

        // One byte over: the reader refuses it, and no huge file is kept.
        assertThat(copied(started!!)).hasLength(RecipeFile.MAX_BYTES + 1)
    }

    @Test
    fun aFileUriIsHandedOnEmptySoTheReaderRefusesIt() {
        val file = File(context.filesDir, "secret.umamit-rezept.json").apply {
            writeText("""{"format":"umamit-recipe"}""")
        }

        val started = handedOver(ReceiveRecipeActivity.view(context, Uri.fromFile(file)))

        assertThat(copied(started!!)).isEmpty()
    }

    @Test
    fun aUriOfTheAppsOwnProviderReadsAsNothing() {
        // MainActivity is exported: another app could point it at the app's own files.
        val own = Uri.parse("content://${context.packageName}.files/recipes/Tee.umamit-rezept.json")
        val forged = MainActivity.openRecipe(context, ReceivedFile(own, RecipeFile.MIME_TYPE))

        assertThat(copied(forged)).isEmpty()
    }

    @Test
    fun anythingButAViewOrSendOfARecipeTypeStartsNothing() {
        val uri = Uri.parse("content://$AUTHORITY/x")
        val intents = listOf(
            // An explicit intent reaches the exported activity with any action or type.
            Intent(context, ReceiveRecipeActivity::class.java).setAction(Intent.ACTION_VIEW)
                .setDataAndType(uri, "image/png"),
            Intent(context, ReceiveRecipeActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, uri),
            Intent(context, ReceiveRecipeActivity::class.java).setAction(Intent.ACTION_SEND)
                .setType(RecipeFile.MIME_TYPE),
            Intent(context, ReceiveRecipeActivity::class.java).setAction(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_STREAM, uri),
            Intent(context, ReceiveRecipeActivity::class.java).setAction(Intent.ACTION_EDIT)
                .setDataAndType(uri, RecipeFile.MIME_TYPE),
        )
        intents.forEach { assertThat(handedOver(it)).isNull() }
    }

    @Test
    fun mainActivityKeepsAFileUntilScr32ClosesIt() {
        var next = 41L
        val files = files(newToken = { ++next })
        val bytes = byteArrayOf(1, 2, 3)
        val source = RecipeFileSource { _, _ -> bytes }
        val opened = MainActivity.openRecipe(context, ReceivedFile(Uri.parse("content://$AUTHORITY/r"), null))

        assertThat(files.offer(Intent(Intent.ACTION_MAIN), source)).isFalse()
        assertThat(files.offer(opened, source)).isTrue()
        val pending = requireNotNull(files.pending.value)
        assertThat(pending.token).isEqualTo(42L)

        // A recreation after a process death keeps it.
        val restored = ReceivedRecipeFiles().apply { restore(Bundle().also(files::save)) }
        assertThat(restored.pending.value?.bytes).isEqualTo(bytes)

        files.shown(42L)
        assertThat(files.pending.value).isNull()
        assertThat(files.bytes(42L)).isEqualTo(bytes)
        val reopened = ReceivedRecipeFiles().apply { restore(Bundle().also(files::save)) }
        assertThat(reopened.bytes(42L)).isEqualTo(bytes)

        files.closed(42L)
        assertThat(files.bytes(42L)).isNull()

        // From the recent apps, the task's first intent comes again: handled then.
        val fromHistory = Intent(opened).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertThat(files.offer(fromHistory, source)).isFalse()
    }

    @Test
    fun aRecreationKeepsTheFilesTheInstanceHoldsOverTheSavedOnes() {
        val files = files()
        files.receive(byteArrayOf(1))
        val saved = Bundle().also(files::save)
        // A copy that lands after the save, before the recreated activity restores (the view model survived).
        files.receive(byteArrayOf(2))

        files.restore(saved)

        assertThat(files.pending.value?.bytes).isEqualTo(byteArrayOf(2))
    }

    @Test
    fun aPickedFileIsCappedAsAReceivedOne() {
        val files = ReceivedRecipeFiles(newToken = { 7L })

        files.receive(ByteArray(RecipeFile.MAX_BYTES * 2))

        assertThat(files.pending.value?.bytes).hasLength(RecipeFile.MAX_BYTES + 1)
    }

    @Test
    fun aPickedFileStillArrivesWhenTheScreenThatPickedItGoes() = runBlocking {
        // The copy runs in the files' own scope (MainActivity's view model), not in the picking composition's.
        val files = ReceivedRecipeFiles(newToken = { 9L })
        val composition = CoroutineScope(Job())
        val release = CountDownLatch(1)
        val copy = CompletableDeferred<Job>()
        composition.launch {
            copy.complete(
                files.receiveCopy {
                    release.await()
                    byteArrayOf(4, 5)
                },
            )
        }
        val job = copy.await()

        composition.cancel() // a rotation ends the composition while the copy runs
        release.countDown()
        job.join()

        assertThat(files.pending.value?.bytes).isEqualTo(byteArrayOf(4, 5))
    }

    /** Serves [file] for every URI, as another app's provider would. */
    class BytesProvider : ContentProvider() {
        var file: File? = null

        override fun onCreate(): Boolean = true

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
            opened++
            return file?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }
        }

        override fun getType(uri: Uri): String {
            typeAsked++
            return type
        }

        companion object {
            /** How often the provider was opened and asked for its type, and the type it answers; reset per test. */
            var opened = 0
            var typeAsked = 0
            var type = RecipeFile.MIME_TYPE
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    private companion object {
        const val AUTHORITY = "com.example.sender.files"
    }
}
