package com.hrvib.app.domain

object PolarHrParser {
    /**
     * Parse Heart Rate Measurement characteristic (0x2A37).
     * RR values are in 1/1024 seconds and converted to milliseconds.
     */
    fun parse(payload: ByteArray, timestampMs: Long): HrRrSample {
        if (payload.isEmpty()) return HrRrSample(timestampMs, null, emptyList())
        val flags = payload[0].toInt() and 0xFF
        val hr16Bit = flags and 0x01 != 0
        val rrPresent = flags and 0x10 != 0
        var offset = 1
        val hr = if (hr16Bit) {
            if (payload.size < 3) null else {
                val value = (payload[offset].toInt() and 0xFF) or
                    ((payload[offset + 1].toInt() and 0xFF) shl 8)
                offset += 2
                value
            }
        } else {
            if (payload.size < 2) null else {
                val value = payload[offset].toInt() and 0xFF
                offset += 1
                value
            }
        }
        if (flags and 0x08 != 0) offset += 2 // energy expended
        val rr = mutableListOf<Double>()
        if (rrPresent) {
            while (offset + 1 < payload.size) {
                val raw = (payload[offset].toInt() and 0xFF) or
                    ((payload[offset + 1].toInt() and 0xFF) shl 8)
                rr += raw * 1000.0 / 1024.0
                offset += 2
            }
        }
        return HrRrSample(
            timestampMs = timestampMs,
            hrBpm = hr,
            rrIntervalsMs = rr
        )
    }
}
