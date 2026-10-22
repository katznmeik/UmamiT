package com.umamit.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Test

/** The merged-manifest permission rule of 06 §5.7 (M1-02). */
class ManifestPermissionsTest {

    private val appId = "com.umamit.app"

    private fun manifest(vararg body: String) = listOf(
        """<?xml version="1.0" encoding="utf-8"?>""",
        """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$appId">""",
        *body,
        """<application android:label="UmamiT" />""",
        "</manifest>",
    ).joinToString("\n")

    private fun uses(name: String, tag: String = "uses-permission") = """<$tag android:name="$name" />"""

    private fun ownSignaturePermission(id: String = appId, level: String = "signature") =
        """<permission android:name="$id.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" android:protectionLevel="$level" />"""

    private fun releaseViolations(xml: String) =
        ManifestPermissions.violations(xml, appId, ManifestPermissions.allowedFor(debugBuildType = false))

    private fun debugViolations(xml: String, id: String = "$appId.debug") =
        ManifestPermissions.violations(xml, id, ManifestPermissions.allowedFor(debugBuildType = true))

    @Test
    fun `every permission of 06 section 5_7 is allowed in release`() {
        val xml = manifest(*ManifestPermissions.ALLOWED.map { uses(it) }.toTypedArray())
        assertEquals(emptyList<String>(), releaseViolations(xml))
    }

    @Test
    fun `a permission outside 06 section 5_7 fails`() {
        val xml = manifest(uses("android.permission.INTERNET"), uses("android.permission.READ_CONTACTS"))
        assertEquals(listOf("android.permission.READ_CONTACTS"), releaseViolations(xml))
        assertEquals(listOf("android.permission.READ_CONTACTS"), debugViolations(xml))
    }

    @Test
    fun `uses-permission-sdk-23 is checked too`() {
        val xml = manifest(uses("android.permission.CAMERA", tag = "uses-permission-sdk-23"))
        assertEquals(listOf("android.permission.CAMERA"), releaseViolations(xml))
    }

    @Test
    fun `debug tooling permissions pass in debug only`() {
        val xml = manifest(
            uses("android.permission.READ_EXTERNAL_STORAGE"),
            uses("android.permission.WRITE_EXTERNAL_STORAGE"),
        )
        assertEquals(emptyList<String>(), debugViolations(xml))
        assertEquals(
            listOf("android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"),
            releaseViolations(xml),
        )
    }

    @Test
    fun `the app's own androidx receiver permission passes when declared with signature level`() {
        val release = manifest(
            ownSignaturePermission(),
            uses("$appId.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"),
        )
        assertEquals(emptyList<String>(), releaseViolations(release))

        val debug = manifest(
            ownSignaturePermission(id = "$appId.debug"),
            uses("$appId.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"),
        )
        assertEquals(emptyList<String>(), debugViolations(debug))
    }

    @Test
    fun `a receiver permission that is undeclared, not signature level or of another app fails`() {
        val name = "$appId.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        assertEquals(listOf(name), releaseViolations(manifest(uses(name))))
        assertEquals(
            listOf(name),
            releaseViolations(manifest(ownSignaturePermission(level = "normal"), uses(name))),
        )
        val foreign = "org.example.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        assertEquals(
            listOf(foreign),
            releaseViolations(manifest(ownSignaturePermission(id = "org.example"), uses(foreign))),
        )
    }

    @Test
    fun `duplicates are reported once and sorted`() {
        val xml = manifest(
            uses("android.permission.READ_SMS"),
            uses("android.permission.ACCESS_FINE_LOCATION"),
            uses("android.permission.READ_SMS"),
        )
        assertEquals(
            listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.READ_SMS"),
            releaseViolations(xml),
        )
    }
}
