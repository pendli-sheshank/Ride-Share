import { describe, test, beforeAll, afterAll, beforeEach } from "vitest";
import {
  as, anon, seed, clear, teardown, assertFails, assertSucceeds,
  HOST, RIDER, STRANGER, offerWithOneSeatLeft,
} from "./helpers";

/**
 * Executable tests for `firestore.rules`.
 *
 * These rules had never been run by anything — no emulator setup, no CI job — and every repository
 * test uses a Ktor MockEngine that accepts whatever it is handed. That combination is why several
 * client writes the deployed rules reject shipped without anyone noticing.
 */

beforeAll(async () => { await clear(); });
beforeEach(async () => { await clear(); });
afterAll(async () => { await teardown(); });

describe("users", () => {
  test("a signed-out caller cannot read a user, even with a valid API key", async () => {
    await seed(`users/${HOST}`, { id: HOST, name: "Dana", phoneNumber: "+1 617 555 0100" });
    await assertFails((await anon()).doc(`users/${HOST}`).get());
  });

  test("any signed-in user can read a profile, so feeds can render a host", async () => {
    await seed(`users/${HOST}`, { id: HOST, name: "Dana" });
    await assertSucceeds((await as(RIDER)).doc(`users/${HOST}`).get());
  });

  test("a user may edit their own editable fields", async () => {
    await seed(`users/${RIDER}`, { id: RIDER, name: "Ray", ratingAvg: 4.5, ratingCount: 2 });
    await assertSucceeds((await as(RIDER)).doc(`users/${RIDER}`).update({ name: "Rayna" }));
  });

  test("a user cannot inflate their own rating", async () => {
    await seed(`users/${RIDER}`, { id: RIDER, name: "Ray", ratingAvg: 3.0, ratingCount: 2 });
    await assertFails((await as(RIDER)).doc(`users/${RIDER}`).update({ ratingAvg: 5.0 }));
    await assertFails((await as(RIDER)).doc(`users/${RIDER}`).update({ ratingCount: 999 }));
  });

  test("a user cannot award themselves the vouched badge", async () => {
    await seed(`users/${RIDER}`, { id: RIDER, verifiedTier: "guest" });
    await assertFails((await as(RIDER)).doc(`users/${RIDER}`).update({ verifiedTier: "vouched" }));
  });

  test("a user cannot reset someone else's no-show count", async () => {
    await seed(`users/${HOST}`, { id: HOST, noShowCount: 3 });
    await assertFails((await as(RIDER)).doc(`users/${HOST}`).update({ noShowCount: 0 }));
  });

  test("the private contact subdocument is owner-only", async () => {
    await seed(`users/${RIDER}/private/profile`, { homeAddress: "12 Elm St", lat: 42.3, lng: -71.1 });
    await assertSucceeds((await as(RIDER)).doc(`users/${RIDER}/private/profile`).get());
    await assertFails((await as(STRANGER)).doc(`users/${RIDER}/private/profile`).get());
  });

  test("a block list is readable only by its owner", async () => {
    await seed(`users/${RIDER}/blockedUsers/${STRANGER}`, { id: STRANGER });
    await assertSucceeds((await as(RIDER)).doc(`users/${RIDER}/blockedUsers/${STRANGER}`).get());
    await assertFails((await as(STRANGER)).doc(`users/${RIDER}/blockedUsers/${STRANGER}`).get());
  });
});

describe("trip_offers", () => {
  test("a host can post an offer under their own id, but not under someone else's", async () => {
    await assertSucceeds((await as(HOST)).doc("trip_offers/o1").set(offerWithOneSeatLeft()));
    await assertFails(
      (await as(STRANGER)).doc("trip_offers/o2").set(offerWithOneSeatLeft({ id: "o2" })),
    );
  });

  test("a rider may add themselves to the manifest", async () => {
    await seed("trip_offers/o1", offerWithOneSeatLeft());
    await assertSucceeds((await as(RIDER)).doc("trip_offers/o1").update({
      passengers: ["earlier-rider", RIDER],
      passengerNames: ["Sam", "Ray"],
      seatsLeft: 0,
      status: "full",
    }));
  });

  test("a rider may remove themselves from the manifest", async () => {
    await seed("trip_offers/o1", offerWithOneSeatLeft({
      passengers: ["earlier-rider", RIDER], passengerNames: ["Sam", "Ray"], seatsLeft: 0, status: "full",
    }));
    await assertSucceeds((await as(RIDER)).doc("trip_offers/o1").update({
      passengers: ["earlier-rider"], passengerNames: ["Sam"], seatsLeft: 1, status: "active",
    }));
  });

  test("a joiner must actually consume a seat", async () => {
    await seed("trip_offers/o1", offerWithOneSeatLeft());
    // Adding yourself while leaving seatsLeft untouched means a full ride never reads as full.
    await assertFails((await as(RIDER)).doc("trip_offers/o1").update({
      passengers: ["earlier-rider", RIDER], passengerNames: ["Sam", "Ray"],
      seatsLeft: 1, status: "active",
    }));
  });

  test("a joiner cannot add passengers who never joined", async () => {
    await seed("trip_offers/o1", offerWithOneSeatLeft({ seatsLeft: 3, totalSeats: 4 }));
    // `hasAll` alone permitted this: add yourself, and any number of ids alongside.
    await assertFails((await as(RIDER)).doc("trip_offers/o1").update({
      passengers: ["earlier-rider", RIDER, "ghost-1", "ghost-2"],
      passengerNames: ["Sam", "Ray", "Ghost", "Ghost"],
      seatsLeft: 2, status: "active",
    }));
  });

  test("a rider cannot free up seats on someone else's ride", async () => {
    await seed("trip_offers/o1", offerWithOneSeatLeft({
      passengers: ["earlier-rider", RIDER], passengerNames: ["Sam", "Ray"], seatsLeft: 0, status: "full",
    }));
    // Leaving must return exactly what it returns; it must not reset the ride to empty.
    await assertFails((await as(RIDER)).doc("trip_offers/o1").update({
      passengers: [], passengerNames: [], seatsLeft: 3, status: "active",
    }));
  });

  test("the two parallel manifest arrays must stay the same length", async () => {
    await seed("trip_offers/o1", offerWithOneSeatLeft());
    // The UI zips passengers with passengerNames, so a mismatch pairs a name with the wrong rider.
    await assertFails((await as(RIDER)).doc("trip_offers/o1").update({
      passengers: ["earlier-rider", RIDER], passengerNames: ["Sam"], seatsLeft: 0, status: "full",
    }));
  });

  test("a non-host cannot evict another passenger", async () => {
    await seed("trip_offers/o1", offerWithOneSeatLeft());
    await assertFails((await as(STRANGER)).doc("trip_offers/o1").update({
      passengers: [STRANGER], passengerNames: ["Nope"], seatsLeft: 0, status: "full",
    }));
  });

  test("a non-host cannot cancel someone else's ride", async () => {
    await seed("trip_offers/o1", offerWithOneSeatLeft());
    await assertFails((await as(STRANGER)).doc("trip_offers/o1").update({ status: "cancelled" }));
  });

  test("a non-host cannot rewrite the price", async () => {
    await seed("trip_offers/o1", offerWithOneSeatLeft());
    await assertFails((await as(RIDER)).doc("trip_offers/o1").update({ costPerRider: 0 }));
  });

  test("only the host may delete the offer", async () => {
    await seed("trip_offers/o1", offerWithOneSeatLeft());
    await assertFails((await as(RIDER)).doc("trip_offers/o1").delete());
    await assertSucceeds((await as(HOST)).doc("trip_offers/o1").delete());
  });
});

describe("trip_matches", () => {
  const match = { id: "m1", hostId: HOST, riderId: RIDER, offerId: "o1", requestId: "r1", status: "pending" };

  test("both parties can read the match; nobody else can", async () => {
    await seed("trip_matches/m1", match);
    await assertSucceeds((await as(HOST)).doc("trip_matches/m1").get());
    await assertSucceeds((await as(RIDER)).doc("trip_matches/m1").get());
    await assertFails((await as(STRANGER)).doc("trip_matches/m1").get());
  });

  test("a stranger cannot accept a match they are not part of", async () => {
    await seed("trip_matches/m1", match);
    await assertFails((await as(STRANGER)).doc("trip_matches/m1").update({ status: "accepted" }));
  });
});

describe("ratings", () => {
  const rating = (over: Record<string, unknown> = {}) => ({
    id: "rt1", fromUserId: RIDER, toUserId: HOST, rating: 5, comment: "Great driver", timestamp: Date.now(), ...over,
  });

  test("a valid rating is accepted", async () => {
    await assertSucceeds((await as(RIDER)).doc("ratings/rt1").set(rating()));
  });

  test("a rating cannot be filed on someone else's behalf", async () => {
    await assertFails((await as(STRANGER)).doc("ratings/rt1").set(rating()));
  });

  test("self-rating is rejected", async () => {
    await assertFails((await as(RIDER)).doc("ratings/rt1").set(rating({ toUserId: RIDER })));
  });

  test("out-of-range scores are rejected, including the 1e9 that used to poison the aggregate", async () => {
    await assertFails((await as(RIDER)).doc("ratings/rt1").set(rating({ rating: 1e9 })));
    await assertFails((await as(RIDER)).doc("ratings/rt1").set(rating({ rating: 0 })));
    await assertFails((await as(RIDER)).doc("ratings/rt1").set(rating({ rating: 6 })));
  });

  test("an overlong comment is rejected", async () => {
    await assertFails((await as(RIDER)).doc("ratings/rt1").set(rating({ comment: "x".repeat(501) })));
  });
});

describe("catch-all", () => {
  test("an undeclared collection is denied outright", async () => {
    await assertFails((await as(RIDER)).doc("invites/anything").set({ code: "FREE" }));
    await assertFails((await as(RIDER)).doc("communities/anything").get());
  });
});
