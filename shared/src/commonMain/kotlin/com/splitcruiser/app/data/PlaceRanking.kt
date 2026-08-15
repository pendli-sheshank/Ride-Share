package com.splitcruiser.app.data

/**
 * A place suggestion with its distance from wherever the user is.
 *
 * A flat data class rather than a wrapper around [PhotonPlaceResult]: nesting exports to Swift as
 * `ranked.place.name`, and every call site would have to know which of the two levels a field lives
 * on.
 */
data class RankedPlace(
    val name: String,
    val formattedAddress: String,
    val city: String?,
    val state: String?,
    val country: String?,
    val lat: Double,
    val lon: Double,
    val type: String,
    /** Straight-line miles from the anchor, or -1.0 when there was nothing to measure against. */
    val distanceMiles: Double,
    /** "0.4 mi away". Empty when [distanceMiles] is unknown, so the UI can simply omit the row. */
    val distanceText: String,
    /** Carried from [PhotonPlaceResult.hasHouseNumber]: a precise street address vs a named POI. */
    val hasHouseNumber: Boolean = false,
)

/**
 * Orders address suggestions nearest-first.
 *
 * Photon's `lat`/`lon` parameters only *nudge* its ranking, which is dominated by OSM importance —
 * so a search for a residential street loses to any city, state or country with a similar name, and
 * with the old limit of 8 the address the user actually wanted never reached the screen at all. The
 * fix is two-part: ask Photon for a wide candidate set, then order it here. This half is pure math,
 * so it is unit-testable off-device and both platforms cannot disagree about the order.
 */
object PlaceRanking {

    /**
     * [results] sorted by distance from ([fromLat], [fromLon]), truncated to [limit].
     *
     * Results whose geometry Photon omitted decode as `0.0, 0.0` — a point in the Gulf of Guinea.
     * Sorting those on their computed distance would scatter them through the list according to how
     * far the user happens to live from the prime meridian, so they are pushed to the end instead
     * and carry no distance text.
     */
    fun rankByDistance(
        results: List<PhotonPlaceResult>,
        fromLat: Double,
        fromLon: Double,
        limit: Int,
        preferAddresses: Boolean = false,
    ): List<RankedPlace> {
        if (!isUsableCoordinate(fromLat, fromLon)) return unranked(results, limit, preferAddresses)

        return results
            .map { place ->
                val known = isUsableCoordinate(place.lat, place.lon)
                val miles = if (known) {
                    GeoUtils.distanceInMiles(fromLat, fromLon, place.lat, place.lon)
                } else {
                    UNKNOWN_DISTANCE
                }
                place.ranked(miles)
            }
            // Precise street addresses first when the user is typing one ([preferAddresses]), then
            // unknown distances last, then nearest first. A stable sort, so Photon's own relevance
            // order still breaks ties between two places the same distance away.
            .sortedWith(
                compareBy(
                    { preferAddresses && !it.hasHouseNumber },
                    { it.distanceMiles < 0.0 },
                    { it.distanceMiles },
                ),
            )
            .take(limit)
    }

    /**
     * The same shape with no anchor — what the UI shows before a location fix arrives.
     *
     * With [preferAddresses] the precise street-address results (those carrying a house number) are
     * lifted to the front while everything else keeps Photon's own importance order. Without it, the
     * order is left entirely to Photon. Either way the sort is stable, so relative order is
     * preserved within each group. This is what lets a residential address the user is typing appear
     * even when there is no location fix to rank against — the case that hid houses entirely before.
     */
    fun unranked(
        results: List<PhotonPlaceResult>,
        limit: Int,
        preferAddresses: Boolean = false,
    ): List<RankedPlace> {
        val ordered = if (preferAddresses) {
            results.sortedBy { !it.hasHouseNumber }
        } else {
            results
        }
        return ordered.take(limit).map { it.ranked(UNKNOWN_DISTANCE) }
    }

    private fun PhotonPlaceResult.ranked(miles: Double) = RankedPlace(
        name = name,
        formattedAddress = formattedAddress,
        city = city,
        state = state,
        country = country,
        lat = lat,
        lon = lon,
        type = type,
        distanceMiles = miles,
        distanceText = if (miles < 0.0) "" else "${GeoUtils.formatMiles(miles)} away",
        hasHouseNumber = hasHouseNumber,
    )

    /**
     * `0.0, 0.0` is Photon's "no geometry" and the model's default for an unset coordinate, not a
     * place anyone is navigating to.
     */
    private fun isUsableCoordinate(lat: Double, lon: Double): Boolean = lat != 0.0 || lon != 0.0

    private const val UNKNOWN_DISTANCE = -1.0
}
