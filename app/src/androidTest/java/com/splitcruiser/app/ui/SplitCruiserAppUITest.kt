package com.splitcruiser.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitcruiser.app.data.RideRequest
import com.splitcruiser.app.data.TripOffer
import com.splitcruiser.app.ui.theme.SplitCruiserTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for the components a screen is assembled from.
 *
 * What was here before asserted against local variables and never rendered anything: `testLoginFlow`
 * checked that a hardcoded `"+1-555-0100".isNotEmpty()`, `testNavigationTabsExist` checked that
 * `4 >= 4`, and `setup()` called `setContent {}` with an empty body. Every test passed without
 * touching a single composable, while the docs cited the suite as evidence the login screen was
 * tested.
 *
 * These render real composables and assert on what appears. They cover the shared pieces rather
 * than whole screens, because every screen takes a `MainViewModel`, which constructs a
 * `SplitCruiserRepository` and starts polling Firebase — that needs a fake, which does not exist
 * yet, and a test that silently talks to the network is worse than no test.
 */
@RunWith(AndroidJUnit4::class)
class SplitCruiserAppUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeTestRule.setContent { SplitCruiserTheme { content() } }
    }

    @Test
    fun routeIndicator_showsBothEndsOfTheJourney() {
        setContent {
            RouteIndicator(origin = "Snell Library", destination = "Logan Airport")
        }

        composeTestRule.onNodeWithText("Snell Library").assertIsDisplayed()
        composeTestRule.onNodeWithText("Logan Airport").assertIsDisplayed()
    }

    @Test
    fun statusBadge_upperCasesTheStatus() {
        setContent { StatusBadge(status = "active") }

        composeTestRule.onNodeWithText("ACTIVE").assertIsDisplayed()
    }

    @Test
    fun statusColor_isSharedAcrossEveryCard() {
        // The regression this guards: each card used to re-implement its own
        // `when (status) { ... }`, so a status handled by one fell through to the `else` of
        // another. One function now means they cannot disagree.
        assert(statusColor("active") == statusColor("ACTIVE"))
        assert(statusColor("cancelled") != statusColor("active"))
        assert(statusColor("something-new") == statusColor("unknown"))
    }

    @Test
    fun emptyState_rendersItsCallToActionAndInvokesIt() {
        var clicked = false
        setContent {
            SplitCruiserEmptyState(
                title = "No Hosted Rides",
                description = "You haven't posted any trip offers yet.",
                actionLabel = "Post a ride offer",
                onActionClick = { clicked = true }
            )
        }

        composeTestRule.onNodeWithText("No Hosted Rides").assertIsDisplayed()
        composeTestRule.onNodeWithText("Post a ride offer").performClick()
        assert(clicked) { "The empty state's action did not fire." }
    }

    @Test
    fun hostedRideCard_showsRouteStatusAndSeatCount() {
        val offer = TripOffer(
            id = "offer_1",
            hostName = "Alex",
            origin = "Ruggles Station",
            destination = "South Station",
            departureTime = 1_800_000_000_000,
            totalSeats = 4,
            seatsLeft = 2,
            status = "active",
        )

        setContent {
            HostedRideScheduleCard(offer = offer, onCardClick = {}, onStatusChange = {})
        }

        composeTestRule.onNodeWithText("Ruggles Station").assertIsDisplayed()
        composeTestRule.onNodeWithText("South Station").assertIsDisplayed()
        composeTestRule.onNodeWithText("ACTIVE").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 / 4").assertIsDisplayed()
    }

    @Test
    fun myRideRequestCard_offersCancelOnlyWhileActive() {
        var cancelled = false
        val request = RideRequest(
            id = "req_1",
            origin = "Mission Hill",
            destination = "Harvard Square",
            departureTime = 1_800_000_000_000,
            seatsNeeded = 1,
            status = "active",
        )

        setContent {
            MyRideRequestCard(request = request, onCancelClick = { cancelled = true })
        }

        composeTestRule.onNodeWithText("Cancel request").performClick()
        assert(cancelled) { "Cancelling an active ride request did nothing." }
    }

    @Test
    fun cancelledRideRequest_hasNoCancelButton() {
        val request = RideRequest(
            id = "req_2",
            origin = "Mission Hill",
            destination = "Harvard Square",
            status = "cancelled",
        )

        setContent { MyRideRequestCard(request = request, onCancelClick = {}) }

        composeTestRule.onNodeWithText("Cancel request").assertDoesNotExist()
    }

    @Test
    fun formSection_groupsItsFieldsUnderAHeading() {
        setContent {
            FormSection(title = "Route") {
                androidx.compose.material3.Text("Pickup")
            }
        }

        composeTestRule.onNodeWithText("ROUTE").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pickup").assertIsDisplayed()
    }

    @Test
    fun proposePickupDialog_returnsWhatWasTyped() {
        var proposed: Pair<String, String>? = null
        setContent {
            ProposePickupDialog(
                onDismiss = {},
                onPropose = { location, time -> proposed = location to time }
            )
        }

        composeTestRule.onNodeWithTag("propose_location_input").performTextInput("Main entrance")
        composeTestRule.onNodeWithTag("propose_time_input").performTextInput("8:15 am")
        composeTestRule.onNodeWithText("Send Proposal").performClick()

        assert(proposed == "Main entrance" to "8:15 am") { "Got $proposed" }
    }
}
