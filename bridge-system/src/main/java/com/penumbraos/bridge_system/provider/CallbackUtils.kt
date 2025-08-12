package com.penumbraos.bridge_system.provider

import android.os.DeadObjectException
import android.os.RemoteException
import android.util.Log

inline fun safeCallback(
    tag: String,
    operation: () -> Unit,
    onDeadObject: () -> Unit = {}
): Boolean {
    return try {
        operation()
        true
    } catch (e: DeadObjectException) {
        Log.w(tag, "Dead callback detected", e)
        onDeadObject()
        false
    } catch (e: RemoteException) {
        Log.w(tag, "RemoteException in callback", e)
        false
    } catch (e: Exception) {
        Log.e(tag, "Exception in callback", e)
        false
    }
}