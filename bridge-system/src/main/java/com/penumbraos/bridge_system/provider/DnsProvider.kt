package com.penumbraos.bridge_system.provider

import android.util.Log
import com.penumbraos.bridge.IDnsProvider
import okhttp3.Dns
import java.net.InetAddress

class DnsProvider(private val resolver: Dns) : IDnsProvider.Stub() {
    override fun lookup(hostname: String): String? {
        val results = resolver.lookup(hostname)
        return if (results.isEmpty()) {
            null
        } else {
            (results[0] as InetAddress?)?.hostAddress
        }
    }
}