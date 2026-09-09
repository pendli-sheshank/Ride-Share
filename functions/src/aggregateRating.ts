import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { AggregateField } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { databaseId, db } from "./database";
import { deriveVerifiedTier, numberField } from "./reputation";

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
 * rule already bounds `rating` to 1..5 and forbids self-rating, so the values summed here are sane
 * — which is what lets this use a server-side aggregation query instead of reading the documents.
 *
 * Three properties worth keeping if you change this:
 *
 * 1. **Bounded reads.** `count()`/`average()` are computed by the server; no rating document is
 *    transferred. The previous version did an unbounded `.get()` of every rating for the user, so a
 *    popular host's recount loaded the whole set into function memory.
 * 2. **No lost updates.** The recount and the write share one transaction, so two ratings landing
 *    together serialise instead of both snapshotting pre-write and the later commit writing the
 *    *older* count permanently. Nothing re-triggers to repair that, so it has to be right here.
 * 3. **No ghost users.** The write is `update`-on-existing, not `set(..., {merge:true})`, which
 *    would mint a user document for a uid that no longer exists — readable by the whole app.
 *
 * Follow-up worth considering: cross-check each rating against a completed `trip_matches` document
 * shared by the two users before counting it, so a rating with no underlying shared trip is ignored.
 * That needs a match lookup and its own index and is deliberately left out of this first version.
 */
export const aggregateRating = onDocumentWritten(
  { document: "ratings/{ratingId}", database: databaseId, retry: true },
  async (event) => {
    const after = event.data?.after?.data();
    const before = event.data?.before?.data();
    const toUserId = (after?.toUserId ?? before?.toUserId) as string | undefined;
    if (!toUserId) return;

    const firestore = db();
    const userRef = firestore.collection("users").doc(toUserId);
    const ratingsQuery = firestore.collection("ratings").where("toUserId", "==", toUserId);

    try {
      await firestore.runTransaction(async (tx) => {
        // Read the user first: a transaction must issue all reads before any write, and this is
        // also the existence check that stops a rating against a deleted account minting one.
        const userSnap = await tx.get(userRef);
        if (!userSnap.exists) {
          logger.warn("Skipping rating aggregate for a user document that does not exist", {
            toUserId,
          });
          return;
        }

        const aggregate = await tx.get(
          ratingsQuery.aggregate({
            count: AggregateField.count(),
            average: AggregateField.average("rating"),
          }),
        );

        const ratingCount = aggregate.data().count;
        // `average` is null when nothing matched, and ignores documents whose `rating` is absent or
        // non-numeric. Collapsing that to 0.0 matches what the clients render for an unrated user.
        const ratingAvg = aggregate.data().average ?? 0;

        // The badge is derived here, not by the client, and not left at whatever the client wrote
        // when it created the document. `noShowCount` is whatever aggregateNoShow last wrote.
        const verifiedTier = deriveVerifiedTier({
          ratingAvg,
          ratingCount,
          noShowCount: numberField(userSnap.data(), "noShowCount"),
        });

        tx.update(userRef, { ratingAvg, ratingCount, verifiedTier });
      });
    } catch (error) {
      // A v2 trigger with `retry: true` re-delivers on a thrown error, which is what we want for a
      // transient Firestore failure — but log first, because a silently dropped error here leaves
      // the aggregate permanently desynced with nothing to repair it.
      logger.error("Failed to aggregate ratings", { toUserId, error });
      throw error;
    }
  },
);
