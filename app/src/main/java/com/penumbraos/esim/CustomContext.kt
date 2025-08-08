package com.penumbraos.esim

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.UserHandle
import android.util.Log
import android.view.Display
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

class CustomContext(baseContext: Context, val realResources: Resources, val internalClassLoader: ClassLoader, val isApplication: Boolean = false, var appContext: Context? = null) : ContextWrapper(baseContext) {
    init {
        if (!isApplication && appContext == null) {
            appContext = CustomContext(baseContext, realResources, internalClassLoader, true)
        }
    }

    override fun getResources(): Resources? {
        Log.w("CustomContext", "getResources")
        return realResources
    }

    override fun getOpPackageName(): String {
        Log.w("CustomContext", "getOpPackageName ${super.opPackageName}")
        return super.getOpPackageName()
    }

    override fun getApplicationContext(): Context? {
        return if (isApplication) {
            this
        } else {
            appContext
        }
    }

    override fun getClassLoader(): ClassLoader? {
        return internalClassLoader
    }

    override fun getSharedPreferences(
        name: String?,
        mode: Int
    ): SharedPreferences? {
        return CustomSharedPreferences()
    }

//    @SuppressLint("PrivateApi")
//    override fun getSystemService(name: String): Any? {
//        return baseContext.getSystemService(name)
////        return applicationContext?.getSystemService(name)
////        Log.w("CustomContext", "getSystemService $name")
////
////        if (name == "phone") {
////            Log.w("CustomContext", "getSystemService phone")
////
//////            val telephonyStubClass = Class.forName("com.android.internal.telephony.ITelephony\$Stub")
//////            val asInterfaceMethod = telephonyStubClass.getMethod("asInterface", IBinder::class.java)
//////
//////            val serviceManager = Class.forName("android.os.ServiceManager")
//////            val getService =
//////                serviceManager.getMethod("getService", String::class.java)
//////
//////            val serviceBinder = getService.invoke(null, name)
//////            val iTelephony = asInterfaceMethod.invoke(null, serviceBinder)
//////
//////            val iTelephonyClass = Class.forName("com.android.internal.telephony.ITelephony")
//////            val telephonyManagerClass = Class.forName("android.telephony.TelephonyManager")
//////            val setupITelephonyForTestMethod = telephonyManagerClass.getMethod("setupITelephonyForTest", iTelephonyClass)
//////            setupITelephonyForTestMethod.isAccessible = true
//////
//////            return setupITelephonyForTestMethod.invoke(null, iTelephony)
////            val telephonyManagerClass = Class.forName("android.telephony.TelephonyManager")
////            val getDefaultMethod = telephonyManagerClass.getMethod("getDefault")
////            getDefaultMethod.isAccessible = true
////
////            return getDefaultMethod.invoke(null)
////        } else if (name == "connectivity") {
////            return CustomConnectivityManager.init(internalClassLoader, this)
////        }
////
////        return null
//
////        val serviceManager = Class.forName("android.os.ServiceManager")
////        val getService =
////            serviceManager.getMethod("getService", String::class.java)
////
////        return getService.invoke(null, name)
////        val systemServiceRegistryClass = Class.forName("android.app.SystemServiceRegistry")
////        val getSystemService =
////            systemServiceRegistryClass.getDeclaredMethod("getSystemService", String::class.java)
////        getSystemService.isAccessible = true
////
////        return getSystemService.invoke(null, name)
//    }

    override fun getSystemServiceName(serviceClass: Class<*>): String? {
        TODO("Not yet implemented in getSystemServiceName")
    }

    override fun checkPermission(
        permission: String,
        pid: Int,
        uid: Int
    ): Int {
        Log.w(TAG, "checkPermission $permission")
        return PackageManager.PERMISSION_GRANTED
    }

    override fun checkCallingPermission(permission: String): Int {
        Log.w(TAG, "checkCallingPermission $permission")
        return PackageManager.PERMISSION_GRANTED
    }

    override fun checkCallingOrSelfPermission(permission: String): Int {
        Log.w(TAG, "checkCallingOrSelfPermission $permission")
        return PackageManager.PERMISSION_GRANTED
    }

    override fun checkSelfPermission(permission: String): Int {
        Log.w(TAG, "checkSelfPermission $permission")
        return PackageManager.PERMISSION_GRANTED
    }

    override fun enforcePermission(
        permission: String,
        pid: Int,
        uid: Int,
        message: String?
    ) {
        TODO("Not yet implemented in enforcePermission")
    }

    override fun enforceCallingPermission(permission: String, message: String?) {
        TODO("Not yet implemented in enforceCallingPermission")
    }

    override fun enforceCallingOrSelfPermission(
        permission: String,
        message: String?
    ) {
        TODO("Not yet implemented in enforceCallingOrSelfPermission")
    }

    override fun grantUriPermission(
        toPackage: String?,
        uri: Uri?,
        modeFlags: Int
    ) {
        TODO("Not yet implemented in grantUriPermission")
    }

    override fun revokeUriPermission(uri: Uri?, modeFlags: Int) {
        TODO("Not yet implemented in revokeUriPermission")
    }

    override fun revokeUriPermission(
        toPackage: String?,
        uri: Uri?,
        modeFlags: Int
    ) {
        TODO("Not yet implemented in revokeUriPermission")
    }

    override fun checkUriPermission(
        uri: Uri?,
        pid: Int,
        uid: Int,
        modeFlags: Int
    ): Int {
        TODO("Not yet implemented in checkUriPermission")
    }

    override fun checkCallingUriPermission(
        uri: Uri?,
        modeFlags: Int
    ): Int {
        TODO("Not yet implemented in checkCallingUriPermission")
    }

    override fun checkCallingOrSelfUriPermission(
        uri: Uri?,
        modeFlags: Int
    ): Int {
        TODO("Not yet implemented in checkCallingOrSelfUriPermission")
    }

    override fun checkUriPermission(
        uri: Uri?,
        readPermission: String?,
        writePermission: String?,
        pid: Int,
        uid: Int,
        modeFlags: Int
    ): Int {
        TODO("Not yet implemented in checkUriPermission")
    }

    override fun enforceUriPermission(
        uri: Uri?,
        pid: Int,
        uid: Int,
        modeFlags: Int,
        message: String?
    ) {
        TODO("Not yet implemented in enforceUriPermission")
    }

    override fun enforceCallingUriPermission(
        uri: Uri?,
        modeFlags: Int,
        message: String?
    ) {
        TODO("Not yet implemented in enforceCallingUriPermission")
    }

    override fun enforceCallingOrSelfUriPermission(
        uri: Uri?,
        modeFlags: Int,
        message: String?
    ) {
        TODO("Not yet implemented in enforceCallingOrSelfUriPermission")
    }

    override fun enforceUriPermission(
        uri: Uri?,
        readPermission: String?,
        writePermission: String?,
        pid: Int,
        uid: Int,
        modeFlags: Int,
        message: String?
    ) {
        TODO("Not yet implemented in enforceUriPermission")
    }

    override fun createPackageContext(
        packageName: String?,
        flags: Int
    ): Context? {
        TODO("Not yet implemented in createPackageContext")
    }

    override fun createContextForSplit(splitName: String?): Context? {
        TODO("Not yet implemented in createContextForSplit")
    }

    override fun createConfigurationContext(overrideConfiguration: Configuration): Context? {
        TODO("Not yet implemented in createConfigurationContext")
    }

    override fun createDisplayContext(display: Display): Context? {
        TODO("Not yet implemented in createDisplayContext")
    }

    override fun createDeviceProtectedStorageContext(): Context? {
        TODO("Not yet implemented in createDeviceProtectedStorageContext")
    }

    override fun isDeviceProtectedStorage(): Boolean {
        TODO("Not yet implemented in isDeviceProtectedStorage")
    }
}