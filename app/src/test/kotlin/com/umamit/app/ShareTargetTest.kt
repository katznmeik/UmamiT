package com.umamit.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.catalog.TeaLinkLoader
import com.umamit.feature.catalog.TeaLinkShare
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The share target (v0.2.1, #98; CAT-17 AC1, 06 §5.7, D-094; spec #81 seam 5): in the merged
 * manifest it answers only `ACTION_SEND` with `text/plain`; it hands the shared text, and only
 * that, to MainActivity in the app's own task; MainActivity keeps a share until the catalog takes it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShareTargetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val shareTarget = ComponentName(context, ShareTeaLinkActivity::class.java)

    private fun targetsOf(intent: Intent): List<ComponentName> =
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }

    @Test
    fun theManifestOffersTheShareTargetForPlainTextSharesOnly() {
        val textShare = Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(context.packageName)
        assertThat(targetsOf(textShare)).containsExactly(shareTarget)

        val others = listOf(
            Intent(Intent.ACTION_SEND).setType("image/jpeg"),
            Intent(Intent.ACTION_SEND).setType("text/html"),
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("text/plain"),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tee-kontor-kiel.de/nebelhang-sencha/XY0170")),
            Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain"),
        )
        others.forEach { intent ->
            assertThat(targetsOf(intent.setPackage(context.packageName))).doesNotContain(shareTarget)
        }
        val info = context.packageManager.getActivityInfo(shareTarget, 0)
        assertThat(info.exported).isTrue()
        assertThat(info.permission).isNull()
    }

    @Test
    fun aSharedLinkGoesToTheAppsOwnTaskAndTheTargetFinishes() {
        val share = ShareTeaLinkActivity.share(context, "Nebelhang Sencha $LINK")

        val target = Robolectric.buildActivity(ShareTeaLinkActivity::class.java, share).create().get()

        val started = requireNotNull(shadowOf(target).nextStartedActivity)
        assertThat(started.component).isEqualTo(ComponentName(context, MainActivity::class.java))
        assertThat(started.action).isEqualTo(TeaLinkShares.ACTION_OPEN_TEA_LINK)
        assertThat(started.getStringExtra(TeaLinkShares.EXTRA_TEXT)).isEqualTo("Nebelhang Sencha $LINK")
        assertThat(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
        assertThat(started.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP).isNotEqualTo(0)
        assertThat(target.isFinishing).isTrue()
    }

    @Test
    fun anythingButAPlainTextShareStartsNothing() {
        val intents = listOf(
            // An explicit intent reaches the exported activity with any action or type.
            Intent(context, ShareTeaLinkActivity::class.java).setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse(LINK)),
            Intent(context, ShareTeaLinkActivity::class.java).setAction(Intent.ACTION_SEND).setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://elsewhere/secret.png")),
            Intent(context, ShareTeaLinkActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain"),
            Intent(context, ShareTeaLinkActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "   "),
        )
        intents.forEach { intent ->
            val target = Robolectric.buildActivity(ShareTeaLinkActivity::class.java, intent).create().get()

            assertThat(shadowOf(target).nextStartedActivity).isNull()
            assertThat(target.isFinishing).isTrue()
        }
    }

    @Test
    fun aSharedTextIsCutJustOverWhatTheAppReads() {
        val long = "x".repeat(TeaLinkLoader.MAX_TEXT_LENGTH * 3)

        val text = ShareTeaLinkActivity.sharedText(ShareTeaLinkActivity.share(context, long))

        // One character over the limit: the catalog refuses it, and no huge text travels on.
        assertThat(text).hasLength(TeaLinkLoader.MAX_TEXT_LENGTH + 1)
        assertThat(
            ShareTeaLinkActivity.sharedText(
                ShareTeaLinkActivity.share(context, LINK).setType("Text/Plain; charset=utf-8"),
            ),
        )
            .isEqualTo(LINK)
    }

    @Test
    fun mainActivityKeepsAShareUntilTheCatalogTakesIt() {
        val shares = TeaLinkShares(newToken = { 42L })

        assertThat(shares.offer(Intent(Intent.ACTION_MAIN))).isFalse()
        assertThat(shares.offer(MainActivity.openTeaLink(context, LINK))).isTrue()
        assertThat(shares.share.value).isEqualTo(TeaLinkShare(42L, LINK))

        // A recreation keeps it.
        val saved = Bundle().also(shares::save)
        val restored = TeaLinkShares(newToken = { 0L }).apply { restore(saved) }
        assertThat(restored.share.value).isEqualTo(TeaLinkShare(42L, LINK))

        shares.taken(41L)
        assertThat(shares.share.value).isNotNull()
        shares.taken(42L)
        assertThat(shares.share.value).isNull()
    }

    @Test
    fun aTaskReopenedFromTheRecentAppsDoesNotLoadItsShareAgain() {
        val shares = TeaLinkShares()

        val reopened = MainActivity.openTeaLink(context, LINK).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertThat(shares.offer(reopened)).isFalse()
        assertThat(shares.share.value).isNull()
    }

    private companion object {
        const val LINK = "https://www.tee-kontor-kiel.de/nebelhang-sencha/XY0170"
    }
}
