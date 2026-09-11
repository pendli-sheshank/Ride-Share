import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { logger } from "firebase-functions";
import { databaseId, db } from "./database";
import { buildPushMessage, classifySendError, type PushRegistration } from "./pushPayload";

/**
 * Delivers a `notifications/{id}` document to the recipient's device.
 *
 * Until this existed the app could not reach anyone who did not already have it open.
 * `sendNotificationAlert` wrote the document, the in-app bell rendered it, and that was the end of
 * the chain — so a rider learned their request had been accepted only by opening the app and
 * looking. On a product whose whole job is agreeing a meeting time with a stranger, that is most of
 * the way to not working.
 *
 * **Bound to [databaseId], like every trigger here.** `onDocumentCreated("path", …)` without the
 * `database` option resolves to `(default)`, which is not this project's database — that mistake
 * shipped all three of the original functions inert and froze every user's reputation at zero.
 *
 * `retry: true` with a deliberately narrow idea of what is worth retrying: see
 * `classifySendError`. A permanent rejection re-sent forever is worse than a dropped notification.
 */
export const fanOutNotifications = onDocumentCreated(
  { document: "notifications/{notificationId}", database: databaseId, retry: true },
  async (event) => {
    const alert = event.data?.data();
    const userId = alert?.userId as string | undefined;
    if (!alert || !userId) return;

    const firestore = db();

    // The token lives on the *private* subdocument, not on `users/{uid}`. That document is readable
    // by every signed-in user (`firestore.rules` says so, and says why), so a live push token there
    // would be a stable per-device identifier for every user handed to every other user. Same
    // reason the home address sits under `private/`.
    const pushRef = firestore.collection("users").doc(userId).collection("private").doc("push");

    let registration: Partial<PushRegistration> | undefined;
    try {
      registration = (await pushRef.get()).data() as Partial<PushRegistration> | undefined;
    } catch (error) {
      logger.error("Could not read the push registration", { userId, error });
      throw error; // Transient: let the retry handle it.
    }

    const message = buildPushMessage(alert, registration);
    if (!message) {
      // No device registered, or nothing worth saying. Not an error — most accounts will sit in
      // this state until they grant the notification permission.
      logger.debug("No push sent", { userId, notificationId: event.params.notificationId });
      return;
    }

    try {
      await getMessaging().send(message);
    } catch (error) {
      const outcome = classifySendError(error);
      if (outcome === "drop-token") {
        // The app was uninstalled or the token rotated. Left in place, this same failure repeats on
        // every notification for the life of the account.
        logger.info("Dropping a dead push registration", { userId });
        await pushRef.delete().catch((deleteError) => {
          logger.warn("Could not delete the dead push registration", { userId, deleteError });
        });
        return;
      }
      if (outcome === "retry") throw error;
      logger.warn("Giving up on a push", { userId, error });
    }
  },
);
