package com.splitcruiser.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where ads land in the feed, pinned so the two platforms cannot disagree.
 *
 * The same reason `PlaceRanking` and `perRiderShare` are tested here rather than twice: a rule
 * reimplemented per platform drifts, and "every third ride" drifting means one platform shows
 * twice the ads of the other with nobody noticing.
 */
class AdSlottingTest {

    @Test
    fun aShortFeedGetsNoAdsAtAll() {
        // A feed of two rides with an ad between them reads as an ad-supported app with nothing to
        // offer. That is the common case in a new city, which is exactly when it matters most.
        for (rideCount in 0 until AdSlotting.MIN_RIDES_FOR_ADS) {
            assertEquals(
                emptyList(),
                AdSlotting.adSlotIndices(rideCount),
                "a feed of $rideCount rides must carry no ads",
            )
        }
    }

    @Test
    fun theFirstAdComesAfterSeveralRidesNotBeforeAny() {
        // Index 3 means "after the fourth ride". An ad at index 0 would be the first thing in the
        // feed, above every ride.
        assertEquals(listOf(3), AdSlotting.adSlotIndices(AdSlotting.MIN_RIDES_FOR_ADS))
    }

    @Test
    fun adsRepeatOnTheInterval() {
        // 20 rides: after the 4th, then every 6 — 3, 9, 15.
        assertEquals(listOf(3, 9, 15), AdSlotting.adSlotIndices(20))
    }

    @Test
    fun aLongFeedStopsAtTheCap() {
        // Each slot is a live ad request. Unbounded, they would scale with scroll depth, which
        // costs the user bandwidth and is the shape AdMob's policies treat as excessive.
        val slots = AdSlotting.adSlotIndices(500)
        assertEquals(AdSlotting.MAX_SLOTS_PER_FEED, slots.size)
        assertEquals(listOf(3, 9, 15), slots)
    }

    @Test
    fun noSlotIsEverTheLastThingInTheFeed() {
        // `index < rideCount - 1` is what guarantees this. An ad card with no ride under it looks
        // like the feed ended in an advert.
        for (rideCount in 0..60) {
            val slots = AdSlotting.adSlotIndices(rideCount)
            assertTrue(
                slots.all { it < rideCount - 1 },
                "a slot at the end of a $rideCount-ride feed: $slots",
            )
        }
    }

    @Test
    fun slotsAreAscendingAndDistinct() {
        // Both platforms walk the rides in order and consult this; a duplicate or out-of-order
        // index would render two ads in one gap on one platform and not the other.
        for (rideCount in 0..60) {
            val slots = AdSlotting.adSlotIndices(rideCount)
            assertEquals(slots.sorted(), slots, "not ascending at $rideCount rides")
            assertEquals(slots.distinct(), slots, "duplicate slot at $rideCount rides")
        }
    }

    @Test
    fun everySlotIsARealRideIndex() {
        // The contract is "the index of the ride this ad follows", so it has to be in range.
        for (rideCount in 0..60) {
            assertTrue(
                AdSlotting.adSlotIndices(rideCount).all { it in 0 until rideCount },
                "out-of-range slot at $rideCount rides",
            )
        }
    }

    @Test
    fun oneMoreRideNeverRemovesAnEarlierSlot() {
        // Growing the feed by a poll must not make an ad jump position — the list is re-rendered
        // on every 20s refresh, and a slot that moves makes the feed twitch under the user.
        for (rideCount in AdSlotting.MIN_RIDES_FOR_ADS until 60) {
            val before = AdSlotting.adSlotIndices(rideCount)
            val after = AdSlotting.adSlotIndices(rideCount + 1)
            assertTrue(
                after.containsAll(before) || before.size == AdSlotting.MAX_SLOTS_PER_FEED,
                "slots moved from $before to $after when the feed grew to ${rideCount + 1}",
            )
        }
    }
}
