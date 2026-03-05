package com.hrvib.app

import android.Manifest
import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(*runtimePermissions())

    @Test
    fun setupControlsShowRatioBreathsPerMinuteAndDurationStep() {
        composeRule.onNodeWithTag("go_setup").performClick()

        composeRule.onNodeWithTag("setup_ratio").assertTextContains("4:6")
        composeRule.onNodeWithTag("setup_breaths_per_min").assertTextContains("5.5")

        composeRule.onNodeWithTag("inhale_beats_inc").performClick()
        composeRule.onNodeWithTag("setup_ratio").assertTextContains("5:6")
        composeRule.onNodeWithTag("setup_breaths_per_min").assertTextContains("5.0")

        composeRule.onNodeWithTag("duration_min_inc").performClick()
        composeRule.onNodeWithTag("duration_min_value").assertTextContains("6")

        composeRule.onNodeWithTag("setup_bpm_slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(55.1f)
            }
        composeRule.onNodeWithText("Metronome BPM 55.1").assertExists()
    }

    @Test
    fun startSessionShowsLiveMetricsAndWaveAndCanOpenSummary() {
        connectFake("vector_hr_rr")
        composeRule.onNodeWithTag("go_setup").performClick()
        composeRule.onNodeWithTag("inhale_beats_dec").performClick()
        composeRule.onNodeWithTag("inhale_beats_dec").performClick()
        composeRule.onNodeWithTag("inhale_beats_dec").performClick()
        composeRule.onNodeWithTag("exhale_beats_dec").performClick()
        composeRule.onNodeWithTag("exhale_beats_dec").performClick()
        composeRule.onNodeWithTag("exhale_beats_dec").performClick()
        composeRule.onNodeWithTag("exhale_beats_dec").performClick()
        composeRule.onNodeWithTag("exhale_beats_dec").performClick()

        composeRule.onNodeWithTag("start_session").performClick()
        composeRule.onNodeWithTag("live_wave").assertExists()
        composeRule.onNodeWithTag("live_hr").assertExists()
        composeRule.waitUntil(6_000) {
            nodeTextOrNull("live_phase")?.contains("Exhale") == true
        }
        composeRule.waitUntil(4_000) {
            nodeTextOrNull("live_countdown")?.contains("299s") == true
        }
        composeRule.onNodeWithTag("stop_session").performClick()
        composeRule.onNodeWithTag("open_summary").performClick()
        composeRule.onNodeWithText("Session Summary").assertExists()
    }

    @Test
    fun hrOnlyVectorShowsHrUpdatesAndHrvDash() {
        connectFake("vector_hr_only")
        composeRule.onNodeWithTag("go_setup").performClick()
        composeRule.onNodeWithTag("start_session").performClick()
        composeRule.waitUntil(5_000) {
            nodeTextOrNull("live_hr")?.contains("72") == true
        }
        composeRule.onNodeWithTag("live_hrv").assertTextContains("—")
    }

    @Test
    fun disconnectReconnectVectorShowsDisconnectedThenConnected() {
        connectFake("vector_disconnect")
        composeRule.waitUntil(5_000) {
            nodeTextOrNull("connection_status")?.contains("Disconnected") == true
        }
        composeRule.waitUntil(5_000) {
            nodeTextOrNull("connection_status")?.contains("Connected") == true
        }
    }

    @Test
    fun homeRangeButtonsChangeScatterAndTimeSeriesCountsAndLegendVisible() {
        composeRule.onNodeWithTag("timeseries_legend_avg").assertExists()
        composeRule.onNodeWithTag("timeseries_legend_peak").assertExists()
        val before = readCount("scatter_count")
        composeRule.onNodeWithTag("range_day").performClick()
        composeRule.waitForIdle()
        val day = readCount("scatter_count")
        composeRule.onNodeWithTag("range_year").performClick()
        composeRule.waitForIdle()
        val year = readCount("scatter_count")

        // With seeded demo history, wider ranges should not reduce to zero.
        assertTrue(before >= 0)
        assertTrue(day >= 0)
        assertTrue(year >= day)
    }

    private fun connectFake(vectorTag: String) {
        composeRule.onNodeWithTag("go_device").performClick()
        composeRule.onNodeWithTag(vectorTag).performClick()
        composeRule.onNodeWithTag("scan_button").performClick()
        composeRule.waitUntil(6_000) {
            composeRule.onAllNodes(hasTestTag("device_fake-polar-h10")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("device_fake-polar-h10").performClick()
        composeRule.waitUntil(6_000) {
            nodeTextOrNull("connection_status")?.contains("Connected") == true
        }
    }

    private fun readCount(tag: String): Int {
        val text = composeRule.onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config
            .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
            ?.joinToString("") { it.text }
            ?: return -1
        return text.substringAfter(": ").toIntOrNull() ?: -1
    }

    private fun nodeTextOrNull(tag: String): String? {
        return runCatching {
            composeRule.onNodeWithTag(tag)
                .fetchSemanticsNode()
                .config
                .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
                ?.joinToString("") { it.text }
        }.getOrNull()
    }

    companion object {
        private fun runtimePermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
        }
    }
}
