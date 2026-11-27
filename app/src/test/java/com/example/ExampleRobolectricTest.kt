package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.ui.LaunchCoverOverlay
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.AyeshaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `app_name is Bengali Ayesha`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("আয়শা", appName)
        assertNotEquals(0, R.drawable.ayesha_launch_cover)
    }

    @Test
    fun `welcome screen requires 18 plus checkbox before start`() {
        var started = false
        composeTestRule.setContent {
            AyeshaTheme {
                WelcomeScreen(onStart = { confirmed -> started = confirmed })
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("welcome_start_button").performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("age_18_checkbox").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("welcome_start_button").performScrollTo().assertIsEnabled()
        composeTestRule.onNodeWithTag("welcome_start_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(started)
    }

    @Test
    fun `launch cover overlay displays photo and dismisses on tap`() {
        var dismissed = false
        composeTestRule.setContent {
            AyeshaTheme {
                LaunchCoverOverlay(onDismiss = { dismissed = true })
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("launch_cover_overlay").performClick()
        composeTestRule.waitForIdle()
        assertTrue(dismissed)
    }
}
