import { describe, test, beforeAll, afterAll, beforeEach } from "vitest";
import {
  as, seed, clear, teardown, assertFails, assertSucceeds,
  HOST, RIDER, STRANGER, offerWithOneSeatLeft,
} from "./helpers";

/**
 * "Women only" as an enforced restriction rather than a display filter.
 *
 * Before this, the flag was gated solely on `isWomenOnlyFilterEnabled` — a self-service boolean any
 * account could set from its own settings screen — with no gender recorded anywhere and no rule
 * behind it. A women-only ride was visible and joinable by anyone who found the toggle, while both
 * platforms presented it as a safety control.
 *
 * Gender lives on the caller's private profile document, which `users` itself is not, so enforcing
 * it does not mean publishing it.
 */

const WOMAN = "woman-uid";
const MAN = "man-uid";

async function seedProfiles() {
  await seed(`users/${WOMAN}/private/profile`, { gender: "woman", homeAddress: "12 Elm St" });
  await seed(`users/${MAN}/private/profile`, { gender: "man", homeAddress: "4 Oak Ave" });
  // A user who went through onboarding before gender existed, or declined to say.
  await seed(`users/${STRANGER}/private/profile`, { homeAddress: "9 Pine Rd" });
}

beforeAll(async () => { await clear(); });
beforeEach(async () => { await clear(); await seedProfiles(); });
afterAll(async () => { await teardown(); });

describe("joining a women-only ride", () => {
  const womenOnlyOffer = () => offerWithOneSeatLeft({ womenOnly: true, seatsLeft: 2, totalSeats: 4 });

  const join = (uid: string) => ({
    passengers: ["earlier-rider", uid],
    passengerNames: ["Sam", "Someone"],
    seatsLeft: 1,
    status: "active",
  });

  test("an eligible rider may join", async () => {
    await seed("trip_offers/o1", womenOnlyOffer());
    await assertSucceeds((await as(WOMAN)).doc("trip_offers/o1").update(join(WOMAN)));
  });

  test("an ineligible rider is denied, however their settings are configured", async () => {
    await seed("trip_offers/o1", womenOnlyOffer());
    await assertFails((await as(MAN)).doc("trip_offers/o1").update(join(MAN)));
  });

  test("a rider who never declared a gender is denied", async () => {
    await seed("trip_offers/o1", womenOnlyOffer());
    await assertFails((await as(STRANGER)).doc("trip_offers/o1").update(join(STRANGER)));
  });

  test("a rider with no private profile at all is denied", async () => {
    await seed("trip_offers/o1", womenOnlyOffer());
    await assertFails((await as("ghost-uid")).doc("trip_offers/o1").update(join("ghost-uid")));
  });

  test("an ordinary ride is unaffected — anyone may still join", async () => {
    await seed("trip_offers/o1", offerWithOneSeatLeft({ seatsLeft: 2, totalSeats: 4 }));
    await assertSucceeds((await as(MAN)).doc("trip_offers/o1").update(join(MAN)));
  });

  test("the host still controls their own women-only ride", async () => {
    await seed("trip_offers/o1", womenOnlyOffer());
    await assertSucceeds((await as(HOST)).doc("trip_offers/o1").update({ status: "closed" }));
  });
});

describe("accepting a women-only ride request", () => {
  const match = (uid: string) => ({
    id: "m1", hostId: uid, riderId: RIDER, offerId: "o1", requestId: "r1", status: "pending",
  });

  beforeEach(async () => {
    await seed("ride_requests/r1", {
      id: "r1", riderId: RIDER, womenOnly: true, status: "active",
      origin: "A", destination: "B", seatsNeeded: 1, departureTime: Date.now() + 3_600_000,
    });
  });

  test("an eligible host may take a women-only request", async () => {
    await assertSucceeds((await as(WOMAN)).doc("trip_matches/m1").set(match(WOMAN)));
  });

  test("an ineligible host is denied", async () => {
    await assertFails((await as(MAN)).doc("trip_matches/m1").set(match(MAN)));
  });

  test("an ordinary request may be taken by anyone", async () => {
    await seed("ride_requests/r2", {
      id: "r2", riderId: RIDER, womenOnly: false, status: "active",
      origin: "A", destination: "B", seatsNeeded: 1, departureTime: Date.now() + 3_600_000,
    });
    await assertSucceeds((await as(MAN)).doc("trip_matches/m2").set({
      id: "m2", hostId: MAN, riderId: RIDER, offerId: "o1", requestId: "r2", status: "pending",
    }));
  });
});

describe("gender stays private", () => {
  test("nobody else can read the private profile that holds it", async () => {
    await assertFails((await as(MAN)).doc(`users/${WOMAN}/private/profile`).get());
    await assertSucceeds((await as(WOMAN)).doc(`users/${WOMAN}/private/profile`).get());
  });
});
