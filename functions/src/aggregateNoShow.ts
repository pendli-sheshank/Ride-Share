import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { AggregateField } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { databaseId, db } from "./database";
import { deriveVerifiedTier, numberField } from "./reputation";

/**
 * Recomputes a user's no-show count server-side from the immutable no-show reports filed against
 * them.
 *
 * Like `aggregateRating`, this replaces a forgeable client write. The app used to read a user's
 * `noShowCount`, add one, and PATCH it straight back — a value the reporter fully controlled. Now
 * the client files a `no_show_reports/{reporterId}_{targetId}` document (one per reporter, immutable
 * by rule) and this function, with Admin privileges, owns the counter on the user record. The count
 * is the number of distinct reports, so a reporter cannot inflate it by re-filing.
 *
 * See `aggregateRating` for why this counts with a server-side aggregation inside a transaction and
 * updates rather than merges — the same three properties apply.
 */
export const aggregateNoShow = onDocumentWritten(
  { document: "no_show_reports/{reportId}", database: databaseId, retry: true },
  async (event) => {
    const after = event.data?.after?.data();
    const before = event.data?.before?.data();
    const targetId = (after?.targetId ?? before?.targetId) as string | undefined;
    if (!targetId) return;

    const firestore = db();
    const userRef = firestore.collection("users").doc(targetId);
    const reportsQuery = firestore
      .collection("no_show_reports")
      .where("targetId", "==", targetId);

    try {
      await firestore.runTransaction(async (tx) => {
        const userSnap = await tx.get(userRef);
        if (!userSnap.exists) {
          logger.warn("Skipping no-show aggregate for a user document that does not exist", {
            targetId,
          });
          return;
        }

        const aggregate = await tx.get(reportsQuery.aggregate({ count: AggregateField.count() }));
        const noShowCount = aggregate.data().count;

        // A no-show drops the badge immediately, so recompute it from this side too.
        const verifiedTier = deriveVerifiedTier({
          ratingAvg: numberField(userSnap.data(), "ratingAvg"),
          ratingCount: numberField(userSnap.data(), "ratingCount"),
          noShowCount,
        });

        tx.update(userRef, { noShowCount, verifiedTier });
      });
    } catch (error) {
      logger.error("Failed to aggregate no-show reports", { targetId, error });
      throw error;
    }
  },
);
