package com.penumbraos.bridge_system

import android.content.Context
import android.content.pm.PackageManager
import dalvik.system.DexClassLoader

fun getApkClassLoader(context: Context, packageName: String): DexClassLoader {
    val packageManager = context.packageManager
    val apkPath: String
    try {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        apkPath = packageInfo.applicationInfo!!.sourceDir
    } catch (e: PackageManager.NameNotFoundException) {
        throw Error("Could not look up $packageName for class extraction", e)
    }

    return DexClassLoader(apkPath, null, null, null)
}