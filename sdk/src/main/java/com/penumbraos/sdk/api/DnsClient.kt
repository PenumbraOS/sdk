package com.penumbraos.sdk.api

import com.penumbraos.bridge.IDnsProvider

class DnsClient(private val dnsProvider: IDnsProvider) {
    fun lookup(hostname: String): String {
        return dnsProvider.lookup(hostname)
    }
}