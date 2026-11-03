package com.gathin.weatherram;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.StringReader;

public class RamTest {
    private static final String MEMINFO = String.join("\n",
            "MemTotal:       1843200 kB",
            "MemFree:         200000 kB",
            "MemAvailable:    921600 kB",
            "Buffers:          50000 kB",
            "Cached:          400000 kB",
            "");

    @Test
    public void percentIsUsedOverTotal() throws Exception {
        long[] mb = Ram.readMb(new StringReader(MEMINFO));
        assertEquals(900L, mb[0]);
        assertEquals(1800L, mb[1]);
        assertEquals(50, Ram.usedPercent(mb[0], mb[1]));
    }

    @Test
    public void oldKernelFallsBackToFreeBuffersCached() throws Exception {
        String old = MEMINFO.replace("MemAvailable:    921600 kB\n", "");
        long[] mb = Ram.readMb(new StringReader(old));
        assertEquals(625L, mb[0]); // (200000 + 50000 + 400000) / 1024
        assertEquals(66, Ram.usedPercent(mb[0], mb[1]));
    }

    @Test
    public void unknownTotalsGiveNoPercent() throws Exception {
        assertEquals(-1, Ram.usedPercent(-1, 1800));
        assertEquals(-1, Ram.usedPercent(900, -1));
        assertEquals(-1, Ram.usedPercent(900, 0));
    }

    @Test
    public void totalLabelUsesGigabytesAboveOne() {
        assertEquals("1.8 GB", Ram.totalLabel(1800));
        assertEquals("512 MB", Ram.totalLabel(512));
        assertEquals("", Ram.totalLabel(-1));
    }
}
