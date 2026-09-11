import { describe, test, expect } from "vitest";
import {
  buildPushMessage,
  classifySendError,
  RIDE_CHANNEL_ID,
  type NotificationAlert,
} from "./pushPayload";

const alert: NotificationAlert = {
  id: "notif_1",
  userId: "rider-uid",
  title: "Ride Request Accepted! 🚗",
  message: "Your ride request from Back Bay to Providence was accepted by Dana.",
  type: "ride_accepted",
  timestamp: 1_700_000_000_000,
  isRead: false,
};

const registration = { token: "tok-abc", platform: "android", updatedAt: 1_700_000_000_000 };

describe("building the message", () => {
  test("carries the alert's own words, not a generic 'you have a notification'", () => {
    const message = buildPushMessage(alert, registration)!;
    expect(message.notification.title).toBe(alert.title);
    expect(message.notification.body).toBe(alert.message);
    expect(message.token).toBe("tok-abc");
  });

  test("every data value is a string", () => {
    // FCM rejects a non-string data value with an error naming neither the field nor the type.
    const message = buildPushMessage(alert, registration)!;
    for (const value of Object.values(message.data)) {
      expect(typeof value).toBe("string");
    }
  });

  test("names the channel the client has to create, or API 26+ drops it silently", () => {
    expect(buildPushMessage(alert, registration)!.android.notification.channelId)
      .toBe(RIDE_CHANNEL_ID);
  });

  test("a chat message goes out at high priority", () => {
    const message = buildPushMessage({ ...alert, type: "message" }, registration)!;
    expect(message.android.priority).toBe("high");
  });

  test("a routine alert does not, to spare the battery", () => {
    const message = buildPushMessage({ ...alert, type: "match" }, registration)!;
    expect(message.android.priority).toBe("normal");
  });

  test("an empty title still sends, falling back to the app name", () => {
    const message = buildPushMessage({ ...alert, title: "" }, registration)!;
    expect(message.notification.title).toBe("Split Cruiser");
    expect(message.notification.body).toBe(alert.message);
  });

  test("a missing type does not produce the literal string 'undefined' in the payload", () => {
    const message = buildPushMessage({ ...alert, type: undefined }, registration)!;
    expect(message.data.type).toBe("general");
  });
});

describe("nothing worth sending", () => {
  // Returning null rather than throwing: a bad document is a reason to do nothing, not a reason to
  // fail the trigger and have it retried forever against the same document.
  test("no registration at all", () => {
    expect(buildPushMessage(alert, undefined)).toBeNull();
  });

  test("a registration whose token is empty or whitespace", () => {
    expect(buildPushMessage(alert, { token: "" })).toBeNull();
    expect(buildPushMessage(alert, { token: "   " })).toBeNull();
  });

  test("an alert with neither title nor message", () => {
    expect(buildPushMessage({ ...alert, title: "  ", message: "" }, registration)).toBeNull();
  });
});

describe("classifying a send failure", () => {
  test("an unregistered token is dropped, not retried", () => {
    // The app was uninstalled or the token rotated. Without this the same failure repeats on every
    // notification for the life of the account.
    expect(classifySendError({ code: "messaging/registration-token-not-registered" }))
      .toBe("drop-token");
  });

  test("an invalid token is dropped too", () => {
    expect(classifySendError({ code: "messaging/invalid-registration-token" })).toBe("drop-token");
  });

  test("a server-side blip is retried", () => {
    expect(classifySendError({ code: "messaging/server-unavailable" })).toBe("retry");
    expect(classifySendError({ code: "messaging/internal-error" })).toBe("retry");
  });

  test("anything else gives up rather than re-sending a rejected payload forever", () => {
    expect(classifySendError({ code: "messaging/payload-size-limit-exceeded" })).toBe("give-up");
  });

  test("an error with no code at all gives up", () => {
    // A thrown string, a network stack error, anything that is not an FCM error object.
    expect(classifySendError(new Error("boom"))).toBe("give-up");
    expect(classifySendError(null)).toBe("give-up");
    expect(classifySendError("nope")).toBe("give-up");
  });
});
