package com.umamit.buildlogic

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The merged-manifest permission rule (06 §5.7, M1-02): the app may request
 * only the permissions listed there.
 *
 * - **Release** manifests are checked strictly against [ALLOWED].
 * - **Debug** manifests may additionally contain [DEBUG_TOOLING], the
 *   permissions that debug-only libraries add (LeakCanary). They never reach a
 *   release build, which the release check proves.
 * - Both may contain the app's own [DYNAMIC_RECEIVER_PERMISSION_SUFFIX]
 *   permission that `androidx.core` declares (signature level) to protect
 *   runtime-registered, non-exported receivers. It is not a system permission
 *   and grants nothing outside the app; it is accepted only when the manifest
 *   itself declares it for this application ID with `protectionLevel="signature"`.
 */
object ManifestPermissions {

    /** 06 §5.7 — the complete list. */
    val ALLOWED: Set<String> = setOf(
        "android.permission.INTERNET",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.POST_PROMOTED_NOTIFICATIONS",
        "android.permission.USE_FULL_SCREEN_INTENT",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
        "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
        "android.permission.WAKE_LOCK",
        "android.permission.VIBRATE",
        "android.permission.USE_EXACT_ALARM",
        "android.permission.RECEIVE_BOOT_COMPLETED",
        "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
    )

    /**
     * Debug builds only: LeakCanary (`debugImplementation`) requests storage
     * access to keep heap dumps. Add an entry here only for a debug-only tool,
     * with the reason, and mention it in 06 §5.7.
     */
    val DEBUG_TOOLING: Set<String> = setOf(
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
    )

    const val DYNAMIC_RECEIVER_PERMISSION_SUFFIX = ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private val USES_PERMISSION_TAGS = listOf("uses-permission", "uses-permission-sdk-23")

    /** The permissions a variant may request: debug build types also get [DEBUG_TOOLING]. */
    fun allowedFor(debugBuildType: Boolean): Set<String> = if (debugBuildType) ALLOWED + DEBUG_TOOLING else ALLOWED

    /**
     * Returns the requested permissions of [manifestXml] that are not in [allowed]
     * (sorted, without duplicates); empty when the manifest is fine.
     */
    fun violations(manifestXml: String, applicationId: String, allowed: Set<String>): List<String> {
        val document = DocumentBuilderFactory.newInstance()
            .apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            .newDocumentBuilder()
            .parse(InputSource(StringReader(manifestXml)))
        val root = document.documentElement

        val ownSignaturePermission = "$applicationId$DYNAMIC_RECEIVER_PERMISSION_SUFFIX"
        val declaresOwnSignaturePermission = root.elements("permission").any {
            it.androidAttribute("name") == ownSignaturePermission &&
                it.androidAttribute("protectionLevel") == "signature"
        }

        return USES_PERMISSION_TAGS
            .flatMap { root.elements(it) }
            .map { it.androidAttribute("name") }
            .filterNot { it in allowed || (declaresOwnSignaturePermission && it == ownSignaturePermission) }
            .distinct()
            .sorted()
    }

    private fun Element.elements(tagName: String): List<Element> {
        val nodes = getElementsByTagName(tagName)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.androidAttribute(name: String): String = getAttributeNS(ANDROID_NS, name)
}
