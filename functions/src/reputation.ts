/**
 * Derives a user's `verifiedTier` from their reputation aggregates.
 *
 * `verifiedTier` renders as a trust badge on both platforms — a checkmark next to the host's name
 * on the ride detail screen and on the profile — but until now **nothing ever wrote it**. The rules
 * reserve it for the Admin SDK (`firestore.rules:44`) and no function set it, so it stayed at the
 * `"vouched"` the client hardcoded when it created the user document. Every account displayed as
 * vouched from the moment it was created, which made the badge worse than useless: it looked like a
 * verification signal while carrying no information at all.
 *
 * The thresholds below are deliberately conservative and easy to change; what matters is that the
 * value is now a function of behaviour the user cannot set themselves.
 */

/** Ratings needed before an average means anything. */
const MIN_RATINGS_FOR_VOUCHED = 3;

/** Average at or above this, on at least [MIN_RATINGS_FOR_VOUCHED] ratings. */
const MIN_AVERAGE_FOR_VOUCHED = 4.0;

export type VerifiedTier = "vouched" | "guest";

export function deriveVerifiedTier(input: {
  ratingAvg: number;
  ratingCount: number;
  noShowCount: number;
}): VerifiedTier {
  if (input.noShowCount > 0) return "guest";
  if (input.ratingCount < MIN_RATINGS_FOR_VOUCHED) return "guest";
  if (input.ratingAvg < MIN_AVERAGE_FOR_VOUCHED) return "guest";
  return "vouched";
}

/** Reads a numeric field off a user document, tolerating a missing or wrongly-typed value. */
export function numberField(data: FirebaseFirestore.DocumentData | undefined, key: string): number {
  const value = data?.[key];
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
}
