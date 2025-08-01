package com.penumbraos.bridge_system.util

import android.util.Log
import okhttp3.Dns
import org.xbill.DNS.AAAARecord
import org.xbill.DNS.ARecord
import org.xbill.DNS.Lookup
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import java.net.InetAddress

/**
 * pinitd breaks access to DNS, so we provide a custom resolution service automatically
 */
class OkHttpDnsResolver : Dns {

    // TODO: It would be really nice if we could use the DHCP DNS server
    private val resolver = SimpleResolver("1.1.1.1")

    override fun lookup(hostname: String): List<InetAddress> {
        val resolvedIpString = try {
            val lookup = Lookup(hostname, Type.A)
            lookup.setResolver(resolver)

            val records = lookup.run()

            if (lookup.result == Lookup.SUCCESSFUL && records != null && records.isNotEmpty()) {
                val aRecord = records[0] as ARecord
                val ip = aRecord.address.hostAddress
                Log.i("CustomDnsResolver", "Resolved hostname $hostname to A name $ip")
                ip
            } else {
                // Try IPv6 as fallback
                val fallbackLookup = Lookup(hostname, Type.AAAA)
                fallbackLookup.setResolver(resolver)

                val ipv6Records = fallbackLookup.run()

                if (fallbackLookup.result == Lookup.SUCCESSFUL && ipv6Records != null && ipv6Records.isNotEmpty()) {
                    val aaaaRecord = ipv6Records[0] as AAAARecord
                    val ip = "[${aaaaRecord.address.hostAddress}]"
                    Log.i("CustomDnsResolver", "Resolved hostname $hostname to AAAA name $ip")
                    ip
                } else {
                    Log.e("CustomDnsResolver", "Failed to resolve hostname $hostname")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("CustomDnsResolver", "Error resolving hostname: $hostname", e)
            null
        }

        if (resolvedIpString == null) {
            return emptyList()
        }

        return listOf(InetAddress.getByName(resolvedIpString))
    }
}