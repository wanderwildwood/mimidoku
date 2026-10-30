package com.wanderwildwood.mimidoku.server

/**
 * What a typed server address means.
 *
 * A server at home is reached by a private address and is plain http; a server on the open
 * internet should be https. So an address typed without a scheme gets http only when its host is
 * on the home network, and https otherwise; a scheme the reader typed is theirs to choose, but
 * plain http to a host outside the house is warned about.
 */
object ServerAddress {

    /** The address with a scheme: as typed, or http for a home host and https for any other. */
    fun withScheme(typed: String): String {
        val t = typed.trim()
        if (t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true)) return t
        return (if (isHomeHost(hostOf(t))) "http://" else "https://") + t
    }

    /** True for plain http to a host that is not on the home network. */
    fun plainHttpOutsideHome(typed: String): Boolean {
        val full = withScheme(typed)
        return full.startsWith("http://", ignoreCase = true) && !isHomeHost(hostOf(full))
    }

    internal fun hostOf(address: String): String {
        var rest = address.trim().substringAfter("://")
        rest = rest.substringBefore('/').substringBefore('?').substringAfterLast('@')
        if (rest.startsWith("[")) return rest.substringAfter('[').substringBefore(']').lowercase()
        return rest.substringBefore(':').lowercase()
    }

    /**
     * Loopback, private ranges (10/8, 172.16/12, 192.168/16), link-local, the carrier-grade and
     * Tailscale range 100.64/10, `.local` and `.lan` names, names with no dot at all (a box on
     * the home network called "nas"), and the IPv6 equivalents.
     */
    internal fun isHomeHost(host: String): Boolean {
        if (host.isEmpty()) return false
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home")) return true
        if (host.contains(':')) {
            return host == "::1" || host.startsWith("fe80:") || host.startsWith("fc") || host.startsWith("fd")
        }
        val parts = host.split('.')
        if (parts.size == 4 && parts.all { p -> p.isNotEmpty() && p.all { it.isDigit() } && p.toInt() in 0..255 }) {
            val (a, b) = parts[0].toInt() to parts[1].toInt()
            return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) ||
                (a == 169 && b == 254) || (a == 100 && b in 64..127)
        }
        return !host.contains('.')
    }
}
