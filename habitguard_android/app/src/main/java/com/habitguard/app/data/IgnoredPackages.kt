package com.habitguard.app.data

object IgnoredPackages {
    private val exactPackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.dialer",
        "com.samsung.android.incallui",
        "com.samsung.android.dialer",
        "com.sec.android.app.launcher",
        "com.sec.android.app.desktoplauncher",
        "com.samsung.android.honeyboard",
        "com.google.android.inputmethod.latin",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.samsung.android.app.smartcapture",
    )

    private val packagePrefixes = listOf(
        "com.android.launcher",
        "com.android.inputmethod",
        "com.android.providers.",
        "com.samsung.android.providers.",
        "com.samsung.android.app.aodservice",
        "com.samsung.android.app.cocktailbarservice",
        "com.samsung.android.biometrics",
        "com.samsung.android.mdx",
    )

    fun isUsageNoise(packageName: String, ownPackageName: String = ""): Boolean {
        val normalized = packageName.lowercase()
        return normalized == ownPackageName.lowercase() ||
            normalized in exactPackages ||
            packagePrefixes.any { normalized.startsWith(it) }
    }
}
