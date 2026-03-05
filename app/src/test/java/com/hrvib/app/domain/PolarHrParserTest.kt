package com.hrvib.app.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PolarHrParserTest {
    @Test
    fun parsesHrAndRrFrom2a37() {
        val payload = byteArrayOf(
            0x10, // flags: RR present, 8-bit HR
            0x48, // HR 72
            0x80.toByte(), 0x03, // RR 896
            0x60, 0x03 // RR 864
        )
        val parsed = PolarHrParser.parse(payload, 1000L)
        assertThat(parsed.hrBpm).isEqualTo(72)
        assertThat(parsed.rrIntervalsMs[0]).isWithin(0.1).of(875.0)
        assertThat(parsed.rrIntervalsMs[1]).isWithin(0.1).of(843.75)
    }

    @Test
    fun parsesHrOnlyWhenNoRr() {
        val payload = byteArrayOf(0x00, 0x46)
        val parsed = PolarHrParser.parse(payload, 1000L)
        assertThat(parsed.hrBpm).isEqualTo(70)
        assertThat(parsed.rrIntervalsMs).isEmpty()
    }

    @Test
    fun parses16BitHrCorrectly() {
        val payload = byteArrayOf(
            0x01, // flags: 16-bit HR, no RR
            0x2C, 0x01 // HR 300
        )
        val parsed = PolarHrParser.parse(payload, 1234L)
        assertThat(parsed.hrBpm).isEqualTo(300)
        assertThat(parsed.rrIntervalsMs).isEmpty()
    }
}
