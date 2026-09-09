import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions";
import { Query } from "firebase-admin/firestore";
import { db } from "./database";

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
 * **Reads are paged, not just writes.** The previous version batched the `batch.commit()` calls at
 * 400 but did one unbounded `.get()` first, so the whole expired backlog had to fit in function
 * memory and in the 60s default timeout. That backlog was guaranteed to be large the first time
 * this ran for real, because until the database binding was fixed this function was pointed at
 * `(default)` and never closed anything at all. Each pass now reads at most one batch, closes it,
 * and re-queries — the closed documents fall out of the `status in [...]` filter, so the query
 * makes progress without needing a cursor.
 */

const AUTO_CLOSEABLE_OFFER_STATUSES = ["active", "full"];
const AUTO_CLOSEABLE_REQUEST_STATUSES = ["active"];

/** Firestore caps a single batch at 500 writes. */
const BATCH_SIZE = 400;

/** A stop so a pathological backlog cannot spin until the timeout instead of finishing a pass. */
const MAX_BATCHES_PER_COLLECTION = 25;

const COLLECTIONS: { name: string; closeableStatuses: string[] }[] = [
  { name: "trip_offers", closeableStatuses: AUTO_CLOSEABLE_OFFER_STATUSES },
  { name: "ride_requests", closeableStatuses: AUTO_CLOSEABLE_REQUEST_STATUSES },
];

async function closeExpired(query: Query, collectionName: string): Promise<number> {
  const firestore = db();
  let closed = 0;

  for (let pass = 0; pass < MAX_BATCHES_PER_COLLECTION; pass++) {
    const expired = await query.get();
    if (expired.empty) return closed;

    const batch = firestore.batch();
    expired.docs.forEach((doc) => batch.update(doc.ref, { status: "closed" }));
    await batch.commit();
    closed += expired.size;

    // A short page means the backlog is drained; re-querying would only cost a round trip.
    if (expired.size < BATCH_SIZE) return closed;
  }

  logger.warn("Hit the per-run batch cap; the remainder waits for the next run", {
    collection: collectionName,
    closed,
  });
  return closed;
}

export const autoCloseExpiredRides = onSchedule(
  {
    schedule: "every 10 minutes",
    // The default 60s is not enough to drain a real backlog through paged batches.
    timeoutSeconds: 300,
    memory: "256MiB",
    retryCount: 1,
  },
  async () => {
    const firestore = db();
    const now = Date.now();

    for (const { name, closeableStatuses } of COLLECTIONS) {
      const query = firestore
        .collection(name)
        .where("status", "in", closeableStatuses)
        .where("departureTime", "<=", now)
        .limit(BATCH_SIZE);

      try {
        const closed = await closeExpired(query, name);
        if (closed > 0) logger.info("Closed expired documents", { collection: name, closed });
      } catch (error) {
        // Log and continue: a failure closing offers should not stop requests being closed, and a
        // thrown error here would otherwise vanish with no record of which collection failed.
        logger.error("Failed to close expired documents", { collection: name, error });
      }
    }
  },
);
