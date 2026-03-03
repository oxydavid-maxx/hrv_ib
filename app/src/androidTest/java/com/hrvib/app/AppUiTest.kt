package com.hrvib.app

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import androidx.test.espresso.Espresso
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val grantRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.BLUETOOTH_SCAN,
        android.Manifest.permission.BLUETOOTH_CONNECT
    )

    private fun openDevice() {
        composeRule.onNodeWithText("Device").performClick()
    }

    private fun connectWithVector(vectorButton: String) {
        openDevice()
        composeRule.onNodeWithText(vectorButton).performClick()
        composeRule.onNodeWithText("Scan").performClick()
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodes(hasText("Fake Polar H10")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Fake Polar H10").performClick()
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodes(hasText("Connection: Connected")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun demoScanConnectAndHrUpdate() {
        connectWithVector("HR+RR")
        composeRule.onNodeWithTag("connection_status").assertIsDisplayed()
        Espresso.pressBack()
        composeRule.onNodeWithText("Setup").performClick()
        composeRule.onNodeWithText("Start Session").performClick()
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodes(hasText("HR: --")).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("live_hr").assertIsDisplayed()
    }

    @Test
    fun rrPresentUpdatesHrvEverySecond() {
        connectWithVector("HR+RR")
        Espresso.pressBack()
        composeRule.onNodeWithText("Setup").performClick()
        composeRule.onNodeWithText("Start Session").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("HRV(3s RMSSD): —")).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("live_hrv").assertExists()
    }

    @Test
    fun rrAbsentShowsDashWithoutCrash() {
        connectWithVector("HR only")
        Espresso.pressBack()
        composeRule.onNodeWithText("Setup").performClick()
        composeRule.onNodeWithText("Start Session").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("live_hrv").assertExists()
        composeRule.onNodeWithText("HRV(3s RMSSD): —").assertExists()
    }

    @Test
    fun disconnectReconnectStateTransitions() {
        connectWithVector("Disconnect")
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Connection: Disconnected")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Connection: Connected")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun rangeSelectorChangesChartDataCounts() {
        val weekCount = composeRule.onNodeWithTag("scatter_count").fetchSemanticsNode().config.toString()
        composeRule.onNodeWithText("Day").performClick()
        composeRule.waitForIdle()
        val dayCount = composeRule.onNodeWithTag("scatter_count").fetchSemanticsNode().config.toString()
        composeRule.onNodeWithText("Year").performClick()
        composeRule.waitForIdle()
        val yearCount = composeRule.onNodeWithTag("scatter_count").fetchSemanticsNode().config.toString()
        assertNotEquals(dayCount, yearCount)
        assertNotEquals(weekCount, yearCount)
    }

    @Test
    fun softDeleteRestoreAffectsCharts() {
        composeRule.onNodeWithText("Year").performClick()
        composeRule.waitForIdle()
        val before = composeRule.onNodeWithTag("scatter_count").fetchSemanticsNode().config.toString()
        composeRule.onAllNodes(hasText("br/min", substring = true)).onFirst().performClick()
        composeRule.onNodeWithTag("soft_delete_toggle").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Restore")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("soft_delete_toggle").performClick()
        Espresso.pressBack()
        composeRule.waitForIdle()
        val afterRestore = composeRule.onNodeWithTag("scatter_count").fetchSemanticsNode().config.toString()
        assertEquals(before, afterRestore)
    }

    @Test
    fun permissionDeniedUxMessageShown() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand(
            "pm revoke com.hrvib.app android.permission.BLUETOOTH_SCAN"
        ).close()
        instrumentation.uiAutomation.executeShellCommand(
            "pm revoke com.hrvib.app android.permission.BLUETOOTH_CONNECT"
        ).close()
        openDevice()
        composeRule.onNodeWithText("Bluetooth permission denied. Allow permission to scan/connect.").assertExists()
    }
}
