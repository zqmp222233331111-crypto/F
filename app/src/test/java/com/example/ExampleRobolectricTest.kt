package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.control.PhoneControlUiState
import com.example.live.CallType
import com.example.live.LiveCallStatus
import com.example.live.LiveCallUiState
import com.example.persona.AyeshaOutfit
import com.example.ui.LaunchCoverOverlay
import com.example.ui.screens.AudioCallScreen
import com.example.ui.screens.ShortsAnalyzerScreen
import com.example.ui.screens.VideoCallScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.AyeshaTheme
import com.example.video.ShortsAnalyzerUiState
import com.example.video.TrendRadarUiState
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
    fun `app_name is Bengali Ayesha and outfit drawables exist`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("আয়শা", appName)
        assertNotEquals(0, R.drawable.ayesha_launch_cover)
        assertNotEquals(0, R.drawable.ayesha_outfit_white_floral)
        assertNotEquals(0, R.drawable.ayesha_outfit_yellow)
        assertNotEquals(0, R.drawable.ayesha_outfit_black_heart)
        assertNotEquals(0, R.drawable.ayesha_outfit_red_rose)
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

    @Test
    fun `audio call screen shows living character and overlaid orb`() {
        composeTestRule.setContent {
            AyeshaTheme {
                AudioCallScreen(
                    callState = LiveCallUiState(
                        isInCall = true,
                        callType = CallType.AUDIO,
                        status = LiveCallStatus.LISTENING,
                        currentOutfit = AyeshaOutfit.WHITE_FLORAL
                    ),
                    phoneControlState = PhoneControlUiState(),
                    onToggleMute = {},
                    onCycleAudioRoute = {},
                    onStopTask = {},
                    onResolveConfirmation = {},
                    onEndCall = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("audio_call_character").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audio_call_orb").assertIsDisplayed()
    }

    @Test
    fun `video call screen has left orb and expands corner character on tap`() {
        composeTestRule.setContent {
            AyeshaTheme {
                VideoCallScreen(
                    callState = LiveCallUiState(
                        isInCall = true,
                        callType = CallType.VIDEO,
                        status = LiveCallStatus.SPEAKING,
                        isCameraOn = false,
                        currentOutfit = AyeshaOutfit.YELLOW
                    ),
                    phoneControlState = PhoneControlUiState(),
                    onSubmitCameraFrame = { it.close() },
                    onToggleCamera = {},
                    onSwitchCamera = {},
                    onToggleMute = {},
                    onStopTask = {},
                    onResolveConfirmation = {},
                    onEndCall = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("video_left_orb").assertIsDisplayed()
        composeTestRule.onNodeWithTag("video_pip_swap_card").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("video_expanded_character").assertIsDisplayed()
    }

    @Test
    fun `shorts creator coach chat has top call buttons and sends chat message`() {
        var audioCallClicked = false
        var videoCallClicked = false
        var sentMessage = ""
        composeTestRule.setContent {
            AyeshaTheme {
                ShortsAnalyzerScreen(
                    uiState = ShortsAnalyzerUiState(),
                    trendState = TrendRadarUiState(),
                    creatorAudits = emptyList(),
                    isKeyVerified = true,
                    isPhoneControlReady = true,
                    onSelectSubTab = {},
                    onVideoPicked = {},
                    onClearPendingVideo = {},
                    onSelectLanguage = {},
                    onSendChatMessage = { sentMessage = it },
                    onSelectPlatform = {},
                    onSelectTitleOption = {},
                    onTitleEdited = {},
                    onDescriptionEdited = {},
                    onTrendNicheChange = {},
                    onTrendRegionChange = {},
                    onTrendPlatformChange = {},
                    onFetchTrends = {},
                    onDeleteCreatorAudit = {},
                    onDeleteAllCreatorAudits = {},
                    onStartVoiceAssistCall = { audioCallClicked = true },
                    onStartVideoAssistCall = { videoCallClicked = true },
                    onOpenPhoneControlSettings = {},
                    onOpenConnectionSettings = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("shorts_header_audio_call").performClick()
        composeTestRule.onNodeWithTag("shorts_header_video_call").performClick()
        assertTrue(audioCallClicked)
        assertTrue(videoCallClicked)

        composeTestRule.onNodeWithTag("shorts_chat_input").performTextInput("নতুন হুক লিখে দাও")
        composeTestRule.onNodeWithTag("shorts_send_chat_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("নতুন হুক লিখে দাও", sentMessage)
    }
}
