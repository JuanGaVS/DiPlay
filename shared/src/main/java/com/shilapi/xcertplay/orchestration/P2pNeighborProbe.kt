package com.shilapi.xcertplay.orchestration

import java.io.File

/**
 * Counts the phones the head unit has seen on the Wi-Fi Direct interface, from /proc/net/arp.
 *
 * The group-client count Android reports is not reliable on Android 9, so a first-TCP timeout
 * also logs this. It reads only counts and flags: no address leaves the device.
 */
object P2pNeighborProbe {
    data class Summary(val entries: Int, val complete: Int, val incomplete: Int) {
        fun describe(): String = "arpEntries=$entries arpComplete=$complete arpIncomplete=$incomplete"
    }

    private const val FLAG_COMPLETE = 0x2

    fun parse(arpTable: String): Summary {
        var entries = 0
        var complete = 0
        arpTable.lineSequence().drop(1).forEach { line ->
            val columns = line.trim().split(Regex("\\s+"))
            // IP address, HW type, Flags, HW address, Mask, Device
            if (columns.size < 6 || !columns[5].startsWith("p2p")) return@forEach
            entries += 1
            val flags = columns[2].removePrefix("0x").toIntOrNull(16) ?: 0
            if (flags and FLAG_COMPLETE != 0) complete += 1
        }
        return Summary(entries, complete, entries - complete)
    }

    fun read(path: String = "/proc/net/arp"): String = try {
        parse(File(path).readText()).describe()
    } catch (error: Exception) {
        "arp=unavailable failureClass=${error.javaClass.simpleName}"
    }
}
