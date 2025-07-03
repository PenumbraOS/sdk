package com.penumbraos.bridge_system.provider

import android.os.DeadObjectException
import android.os.RemoteException
import android.util.Log

inline fun safeCallback(tag: String, operation: () -> Unit): Boolean {
    return try {
        operation()
        true
    } catch (e: DeadObjectException) {
        Log.w(tag, "Dead callback detected", e)
        false
    } catch (e: RemoteException) {
        Log.w(tag, "RemoteException in callback", e)
        false
    }
}