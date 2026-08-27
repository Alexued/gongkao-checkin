package com.gongkao.checkin.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import com.gongkao.checkin.data.Repo
import java.net.Inet4Address
import java.net.NetworkInterface

object LanInfo {

    /** 取当前网络的 IPv4 地址；优先活动网络，退化到遍历网卡。 */
    fun ip(ctx: Context): String? {
        fromConnectivity(ctx)?.let { return it }
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .asSequence()
                .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
                .flatMap { it.inetAddresses.toList().asSequence() }
                .filterIsInstance<Inet4Address>()
                .map { it.hostAddress }
                .filterNotNull()
                .firstOrNull { it != "127.0.0.1" }
        }.getOrNull()
    }

    private fun fromConnectivity(ctx: Context): String? = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        val net = cm.activeNetwork ?: return null
        val props = cm.getLinkProperties(net) ?: return null
        props.linkAddresses
            .mapNotNull { la: LinkAddress -> la.address as? Inet4Address }
            .map { it.hostAddress }
            .firstOrNull { it != null && it != "127.0.0.1" }
    }.getOrNull()

    fun port(): Int = Repo.read { it.settings.syncPort }
}
