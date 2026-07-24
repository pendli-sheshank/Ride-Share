package com.example.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.theme.SawaariTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class AuthenticationScreensTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testLoginScreen_DisplaysElements() {
        composeTestRule.setContent {
            SawaariTheme {
                // Login screen composable test would go here
                // This is a placeholder for the UI testing framework setup
            }
        }

        // Verify email input exists
        composeTestRule.onNodeWithTag("email_input")
            .assertExists()

        // Verify password input exists
        composeTestRule.onNodeWithTag("password_input")
            .assertExists()

        // Verify login button exists
        composeTestRule.onNodeWithTag("login_button")
            .assertExists()
    }

    @Test
    fun testLoginScreen_EmailValidation_ShowsError() {
        composeTestRule.setContent {
            SawaariTheme {
                // Login screen composable test would go here
            }
        }

        // Enter invalid email
        composeTestRule.onNodeWithTag("email_input")
            .performTextInput("invalidemail")

        // Click login button
        composeTestRule.onNodeWithTag("login_button")
            .performClick()

        // Verify error message appears
        composeTestRule.onNodeWithText("valid email")
            .assertExists()
    }

    @Test
    fun testSignupScreen_PasswordMismatch_ShowsError() {
        composeTestRule.setContent {
            SawaariTheme {
                // Signup screen composable test would go here
            }
        }

        // Enter mismatched passwords
        composeTestRule.onNodeWithTag("password_input")
            .performTextInput("password123")

        composeTestRule.onNodeWithTag("confirm_password_input")
            .performTextInput("password456")

        // Click signup button
        composeTestRule.onNodeWithTag("signup_button")
            .performClick()

        // Verify error message appears
        composeTestRule.onNodeWithText("do not match")
            .assertExists()
    }

    @Test
    fun testSignupScreen_ShortPassword_ShowsError() {
        composeTestRule.setContent {
            SawaariTheme {
                // Signup screen composable test would go here
            }
        }

        // Enter short password
        composeTestRule.onNodeWithTag("password_input")
            .performTextInput("pass")

        composeTestRule.onNodeWithTag("confirm_password_input")
            .performTextInput("pass")

        // Click signup button
        composeTestRule.onNodeWithTag("signup_button")
            .performClick()

        // Verify error message appears
        composeTestRule.onNodeWithText("at least 6 characters")
            .assertExists()
    }

    @Test
    fun testRedeemInviteScreen_ValidCode_Proceeds() {
        composeTestRule.setContent {
            SawaariTheme {
                // Redeem screen composable test would go here
            }
        }

        // Enter valid invite code
        composeTestRule.onNodeWithTag("invite_code_input")
            .performTextInput("INVITE2024")

        // Click redeem button
        composeTestRule.onNodeWithTag("redeem_button")
            .performClick()

        // Verify navigation happens (button should be disabled during processing)
        composeTestRule.onNodeWithTag("redeem_button")
            .assertIsNotEnabled()
    }

    @Test
    fun testProfileSetupScreen_UploadPicture_ShowsProgressIndicator() {
        composeTestRule.setContent {
            SawaariTheme {
                // Profile setup screen composable test would go here
            }
        }

        // Click profile picture card
        composeTestRule.onNodeWithTag("profile_picture_card")
            .performClick()

        // Verify progress indicator appears
        composeTestRule.onNode(
            hasProgressIndicator()
        ).assertExists()
    }

    @Test
    fun testProfileSetupScreen_FilledForm_EnablesCompleteButton() {
        composeTestRule.setContent {
            SawaariTheme {
                // Profile setup screen composable test would go here
            }
        }

        // Fill in name
        composeTestRule.onNodeWithTag("name_input")
            .performTextInput("John")

        // Fill in last initial
        composeTestRule.onNodeWithTag("initial_input")
            .performTextInput("D")

        // Fill in home area
        composeTestRule.onNodeWithTag("home_area_input")
            .performTextInput("Cambridge, MA")

        // Verify complete button is enabled
        composeTestRule.onNodeWithTag("complete_button")
            .assertIsEnabled()
    }
}

private fun hasProgressIndicator() =
    hasTestTag("progress_indicator")
