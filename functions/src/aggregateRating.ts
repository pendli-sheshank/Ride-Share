import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";

/**
 * Recomputes a user's reputation aggregate server-side whenever a rating about them changes.
 *
 * This is the real fix for the client-side aggregation hole. Previously the app read every rating
 * for a user on the *rater's* device, averaged them, and PATCHed `ratingAvg`/`ratingCount` onto the
 * rated user's public document — a write any authenticated client could forge to any value. The
 * Firestore rules now forbid a client from writing those fields at all; the only writer is this
 * function, running with Admin privileges that bypass the rules.
 *
 * Triggered on create, update and delete of any `ratings/{ratingId}` document. The `ratings` create
 * rule already bounds `rating` to 1..5 and forbids self-rating, so the values summed here are sane.
 *
 * Follow-up worth considering: cross-check each rating against a completed `trip_matches` document
 * shared by the two users before counting it, so a rating with no underlying shared trip is ignored.
 * That needs a match lookup and its own index and is deliberately left out of this first version.
 */
export const aggregateRating = onDocumentWritten("ratings/{ratingId}", async (event) => {
  const after = event.data?.after?.data();
  const before = event.data?.before?.data();
  const toUserId = (after?.toUserId ?? before?.toUserId) as string | undefined;
  if (!toUserId) return;

  const db = getFirestore();
  const snapshot = await db
    .collection("ratings")
    .where("toUserId", "==", toUserId)
    .get();

  let sum = 0;
  let count = 0;
  snapshot.forEach((doc) => {
    const value = doc.data().rating;
    if (typeof value === "number" && value >= 1 && value <= 5) {
      sum += value;
      count += 1;
    }
  });

  const ratingAvg = count > 0 ? sum / count : 0;
  await db
    .collection("users")
    .doc(toUserId)
    .set({ ratingAvg, ratingCount: count }, { merge: true });
});
