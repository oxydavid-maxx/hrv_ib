package com.hrvib.app

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreenRendersCharts() {
        composeRule.onNodeWithTag("scatter_count").assertExists()
        composeRule.onNodeWithTag("timeseries_count").assertExists()
    }

    @Test
    fun canOpenDeviceScreen() {
        composeRule.onNodeWithText("Device").performClick()
        composeRule.onNodeWithTag("connection_status").assertExists()
    }
}
