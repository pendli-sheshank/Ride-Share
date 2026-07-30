import { onSchedule } from "firebase-functions/v2/scheduler";
import { getFirestore } from "firebase-admin/firestore";

/**
 * Requires the Blaze (pay-as-you-go) plan — scheduled functions run on Cloud Scheduler, which Spark
 * projects cannot use. See `.claude/skills/release-pipeline/SKILL.md` before deploying this.
 *
 * The `status in [...] + departureTime` query needs a Firestore composite index. The
 * `status ASC, departureTime ASC` index already declared for both collections in
 * `firestore.indexes.json` covers an `in` filter the same way it covers equality, but if Firestore
 * disagrees the first time this runs against real data, it throws with a link that creates the
 * exact index needed — create it before relying on this in production.
 *
 * Batched in chunks of 400 (Firestore caps a single batch at 500 writes) so a busy day's worth of
 * expired rides can't make `batch.commit()` throw partway through.
 */

const AUTO_CLOSEABLE_OFFER_STATUSES = ["active", "full"];
const AUTO_CLOSEABLE_REQUEST_STATUSES = ["active"];
const BATCH_SIZE = 400;

const COLLECTIONS: { name: string; closeableStatuses: string[] }[] = [
  { name: "trip_offers", closeableStatuses: AUTO_CLOSEABLE_OFFER_STATUSES },
  { name: "ride_requests", closeableStatuses: AUTO_CLOSEABLE_REQUEST_STATUSES },
];

export const autoCloseExpiredRides = onSchedule("every 10 minutes", async () => {
  const db = getFirestore();
  const now = Date.now();

  for (const { name, closeableStatuses } of COLLECTIONS) {
    const expired = await db
      .collection(name)
      .where("status", "in", closeableStatuses)
      .where("departureTime", "<=", now)
      .get();

    if (expired.empty) continue;

    for (let i = 0; i < expired.docs.length; i += BATCH_SIZE) {
      const batch = db.batch();
      expired.docs.slice(i, i + BATCH_SIZE).forEach((doc) => {
        batch.update(doc.ref, { status: "closed" });
      });
      await batch.commit();
    }
  }
});
