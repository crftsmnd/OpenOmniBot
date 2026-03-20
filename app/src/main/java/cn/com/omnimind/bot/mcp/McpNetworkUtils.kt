package cn.com.omnimind.bot.mcp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * MCP 网络工具类
 */
object McpNetworkUtils {
    
    /**
     * 检查是否连接到局域网（Wi-Fi或以太网）
     */
    fun isLanConnected(context: Context): Boolean {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(active) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * 获取当前局域网 IP 地址
     */
    fun currentLanIp(): String? {
        val interfaces = runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }.getOrNull()
            ?: return null
        return interfaces
            .flatMap { netIf -> Collections.list(netIf.inetAddresses) }
            .firstOrNull { address ->
                !address.isLoopbackAddress && address is Inet4Address && isLanAddress(address.hostAddress)
            }
            ?.hostAddress
    }

    /**
     * 检查是否为局域网地址（包括 RFC1918 和 Tailscale CGNAT）
     */
    fun isLanAddress(host: String?): Boolean {
        if (host.isNullOrBlank()) return false

        // RFC1918 私网网段
        if (host.startsWith("192.168.")) return true
        if (host.startsWith("10.")) return true
        if (host.startsWith("172.")) {
            val parts = host.split(".")
            if (parts.size >= 2) {
                val second = parts[1].toIntOrNull()
                if (second != null && second in 16..31) return true
            }
        }

        // Tailscale / CGNAT 网段（100.64.0.0/10）
        if (host.startsWith("100.")) {
            val parts = host.split(".")
            if (parts.size >= 2) {
                val second = parts[1].toIntOrNull()
                if (second != null && second in 64..127) return true
            }
        }

        return false
    }
}
