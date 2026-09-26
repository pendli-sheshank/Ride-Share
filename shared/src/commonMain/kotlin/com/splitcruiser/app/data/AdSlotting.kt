package com.splitcruiser.app.data

/**
 * Where an ad card sits in the browse feed.
 *
 * One object read by both platforms, for the same reason `PlaceRanking` and `perRiderShare` are
 * shared: this is a *decision*, not a rendering, and two independent copies of "every third ride"
 * drift the moment either side is touched. `DESIGN_SYSTEM.md` states the rule — anything that
 * computes a user-visible position or number goes here and both platforms call it.
 *
 * Deliberately free of any ad-SDK type. Nothing here knows AdMob exists, which is what makes it
 * testable on Linux and identical on both platforms.
 */
object AdSlotting {

    /**
     * How many rides a rider sees before the first ad.
     *
     * Not zero. An ad in the first slot is the first thing in the feed, before any ride, which
     * reads as an advertising app that happens to list rides.
     */
    const val FIRST_SLOT_AFTER = 4

    /** Rides between one ad and the next. */
    const val SLOT_INTERVAL = 6

    /**
     * Below this many rides, no ads at all.
     *
     * A feed of two rides with an ad between them looks like an ad-supported app with nothing to
     * offer — worse for the product than showing no ad and earning nothing. This is also the
     * common case in a new city, where the feed is short precisely when first impressions matter.
     */
    const val MIN_RIDES_FOR_ADS = 6

    /**
     * Ceiling per feed render, regardless of length.
     *
     * Each slot is a live ad request; an unbounded feed would otherwise scale requests with scroll
     * depth, which costs the user bandwidth and battery and is the shape AdMob's policies treat as
     * excessive.
     */
    const val MAX_SLOTS_PER_FEED = 3

    /**
     * The ride indices *after* which an ad card is drawn, ascending.
     *
     * Returned as positions in the ride list rather than positions in the rendered list, so
     * neither platform has to reason about how the other one offsets its own items. A caller
     * walking the rides in order draws the ride, then an ad if that index is in this set.
     *
     * Empty for a short feed — see [MIN_RIDES_FOR_ADS]. Never returns an index at or beyond
     * [rideCount], so an ad can never be the last thing in the feed with nothing under it.
     */
    fun adSlotIndices(rideCount: Int): List<Int> {
        if (rideCount < MIN_RIDES_FOR_ADS) return emptyList()

        val slots = mutableListOf<Int>()
        var index = FIRST_SLOT_AFTER - 1
        while (index < rideCount - 1 && slots.size < MAX_SLOTS_PER_FEED) {
            slots += index
            index += SLOT_INTERVAL
        }
        return slots
    }
}
