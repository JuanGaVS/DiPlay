package com.shilapi.xcertplay.orchestration

import org.junit.Assert.assertEquals
import org.junit.Test

class P2pNeighborProbeTest {
    private val header = "IP address       HW type     Flags       HW address            Mask     Device"

    @Test
    fun countsOnlyWifiDirectEntries() {
        val table = listOf(
            header,
            "192.168.49.2     0x1         0x2         aa:bb:cc:dd:ee:01     *        p2p0",
            "192.168.49.3     0x1         0x0         00:00:00:00:00:00     *        p2p0",
            "192.168.1.5      0x1         0x2         aa:bb:cc:dd:ee:02     *        wlan0",
        ).joinToString("\n")
        assertEquals(P2pNeighborProbe.Summary(2, 1, 1), P2pNeighborProbe.parse(table))
    }

    @Test
    fun emptyTableHasNoEntries() {
        assertEquals(P2pNeighborProbe.Summary(0, 0, 0), P2pNeighborProbe.parse(header))
        assertEquals(P2pNeighborProbe.Summary(0, 0, 0), P2pNeighborProbe.parse(""))
    }

    @Test
    fun malformedLinesAreIgnored() {
        val table = "$header\nnot an arp line\n192.168.49.2 0x1 zz aa:bb * p2p-p2p0-1"
        assertEquals(P2pNeighborProbe.Summary(1, 0, 1), P2pNeighborProbe.parse(table))
    }

    @Test
    fun describeNeverContainsAddresses() {
        val text = P2pNeighborProbe.Summary(1, 1, 0).describe()
        assertEquals("arpEntries=1 arpComplete=1 arpIncomplete=0", text)
    }
}
