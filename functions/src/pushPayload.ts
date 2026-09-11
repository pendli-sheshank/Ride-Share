/**
 * Turning a `notifications/{id}` document into an FCM message, and deciding what a send failure
 * means.
 *
 * These are the parts of the fan-out worth testing: the SDK call itself is a network round trip
 * that a unit test can only assert was made, which proves nothing about whether the right thing
 * was sent. Kept pure and separate for the same reason `reputation.ts` is.
 *
 * Context for why any of this exists: `sendNotificationAlert` in `:shared` has always written a
 * `notifications` document, and nothing has ever read one except a client that was already open.
 * `fcmToken` was declared on the user model and referenced nowhere in any source file on either
 * platform. So the app could not reach anyone who did not happen to have it running — not to say a
 * request was accepted, not that a message arrived, not that a host had cancelled.
 */

/** The `notifications/{id}` shape `firestore.rules` bounds. */
export interface NotificationAlert {
  id: string;
  userId: string;
  title: string;
  message: string;
  type: string;
  timestamp: number;
  isRead: boolean;
}

/** What `users/{uid}/private/push` holds. */
export interface PushRegistration {
  token: string;
  platform: string;
  updatedAt: number;
}

/**
 * FCM rejects a `data` payload whose values are not strings, with an error that names neither the
 * field nor the type. Everything that travels in `data` is stringified here rather than at each
 * call site.
 */
export interface PushMessage {
  token: string;
  notification: { title: string; body: string };
  data: Record<string, string>;
  android: { priority: "high" | "normal"; notification: { channelId: string } };
  apns: { payload: { aps: { sound: string; badge?: number } } };
}

/**
 * The Android notification channel the client must create with the same id, or nothing is shown on
 * API 26+ — the system drops the notification silently rather than reporting a bad channel.
 */
export const RIDE_CHANNEL_ID = "split_cruiser_rides";

/**
 * Chat messages arrive while people are coordinating a pickup that is about to happen, so they go
 * at high priority; everything else can wait for the next maintenance window and spare the battery.
 */
function priorityFor(type: string): "high" | "normal" {
  return type === "message" || type === "ride_accepted" ? "high" : "normal";
}

/**
 * Builds the message, or returns `null` when there is nothing worth sending.
 *
 * Returning `null` rather than throwing: a malformed or empty alert is a reason to do nothing, not
 * a reason to fail the trigger and have it retried forever against the same bad document.
 */
export function buildPushMessage(
  alert: Partial<NotificationAlert>,
  registration: Partial<PushRegistration> | undefined,
): PushMessage | null {
  const token = registration?.token;
  if (typeof token !== "string" || token.trim() === "") return null;

  const title = typeof alert.title === "string" ? alert.title.trim() : "";
  const body = typeof alert.message === "string" ? alert.message.trim() : "";
  if (title === "" && body === "") return null;

  const type = typeof alert.type === "string" && alert.type !== "" ? alert.type : "general";

  return {
    token,
    notification: { title: title || "Split Cruiser", body },
    // Lets the app open the right screen when the notification is tapped. Every value is a string
    // because FCM requires it.
    data: {
      type,
      notificationId: typeof alert.id === "string" ? alert.id : "",
    },
    android: { priority: priorityFor(type), notification: { channelId: RIDE_CHANNEL_ID } },
    apns: { payload: { aps: { sound: "default" } } },
  };
}

/**
 * What to do about a failed send.
 *
 * - `drop-token` — the registration is dead (the app was uninstalled, or the token was rotated).
 *   Delete it, or the same failure repeats on every notification for the life of the account.
 * - `retry` — transient. Let the trigger's own retry handle it.
 * - `give-up` — a permanent error about *this message*, not the token. Retrying re-sends the same
 *   rejected payload forever.
 */
export type SendOutcome = "drop-token" | "retry" | "give-up";

/** FCM's codes for "this token will never work again". */
const DEAD_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
  "messaging/invalid-argument",
]);

/** Transient server-side conditions. */
const RETRYABLE_CODES = new Set([
  "messaging/server-unavailable",
  "messaging/internal-error",
  "messaging/quota-exceeded",
  "messaging/unavailable",
]);

export function classifySendError(error: unknown): SendOutcome {
  const code = (error as { code?: unknown } | null)?.code;
  if (typeof code !== "string") return "give-up";
  if (DEAD_TOKEN_CODES.has(code)) return "drop-token";
  if (RETRYABLE_CODES.has(code)) return "retry";
  return "give-up";
}
