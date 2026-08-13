import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";

/**
 * Recomputes a user's no-show count server-side from the immutable no-show reports filed against
 * them.
 *
 * Like `aggregateRating`, this replaces a forgeable client write. The app used to read a user's
 * `noShowCount`, add one, and PATCH it straight back — a value the reporter fully controlled. Now
 * the client files a `no_show_reports/{reporterId}_{targetId}` document (one per reporter, immutable
 * by rule) and this function, with Admin privileges, owns the counter on the user record. The count
 * is the number of distinct reports, so a reporter cannot inflate it by re-filing.
 */
export const aggregateNoShow = onDocumentWritten(
  "no_show_reports/{reportId}",
  async (event) => {
    const after = event.data?.after?.data();
    const before = event.data?.before?.data();
    const targetId = (after?.targetId ?? before?.targetId) as string | undefined;
    if (!targetId) return;

    const db = getFirestore();
    const snapshot = await db
      .collection("no_show_reports")
      .where("targetId", "==", targetId)
      .get();

    await db
      .collection("users")
      .doc(targetId)
      .set({ noShowCount: snapshot.size }, { merge: true });
  },
);
