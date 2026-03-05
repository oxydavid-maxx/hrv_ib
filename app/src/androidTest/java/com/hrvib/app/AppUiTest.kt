package com.hrvib.app

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
    fun homeScreenRendersCoreWidgets() {
        composeRule.onNodeWithTag("range_day").fetchSemanticsNode()
        composeRule.onNodeWithTag("range_week").fetchSemanticsNode()
        composeRule.onNodeWithTag("range_month").fetchSemanticsNode()
        composeRule.onNodeWithTag("range_year").fetchSemanticsNode()
        composeRule.onNodeWithTag("scatter_count").fetchSemanticsNode()
        composeRule.onNodeWithTag("timeseries_count").fetchSemanticsNode()
        composeRule.onNodeWithTag("timeseries_legend_avg").fetchSemanticsNode()
        composeRule.onNodeWithTag("timeseries_legend_peak").fetchSemanticsNode()
    }

    @Test
    fun debugBuildForcesFakeBleInCiPath() {
        assertTrue(BuildConfig.USE_FAKE_BLE)
    }

    @Test
    fun smokePasses() {
        assertTrue(true)
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
