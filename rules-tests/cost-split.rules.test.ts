import { describe, test, beforeAll, afterAll, beforeEach } from "vitest";
import {
  as, seed, clear, teardown, assertFails, assertSucceeds,
  HOST, RIDER, offerWithOneSeatLeft,
} from "./helpers";

/**
 * The rule half of the cost split.
 *
 * `TripOffer.totalCost` is what the trip costs; `costPerRider` is that divided `totalSeats + 1`
 * ways by `SplitCruiserRepository.perRiderShare`, once, when the ride is posted. Two things have to
 * hold for that to mean anything:
 *
 *   1. Neither figure can be nonsense — bounds, enforced server-side, because a modified client
 *      can talk past the ones in `validateTripOfferOrThrow`.
 *   2. A rider taking a seat cannot rewrite the price on the way in. This is the constraint that
 *      *decided* the design: a share that tracked the manifest would have to be written on the
 *      seat-booking commit, by the one caller who must not be trusted with it.
 *
 * The rules deliberately do not check `costPerRider == totalCost / (totalSeats + 1)`. Kotlin's
 * `Double` division and this engine's need not agree in the last bits, so that equality would
 * reject writes the app makes correctly. The `hasOnly` test below is what actually protects the
 * derived figure.
 */

beforeAll(async () => { await clear(); });
beforeEach(async () => { await clear(); });
afterAll(async () => { await teardown(); });

/** A well-formed posted ride: $60 over three seats plus the driver is $15 each. */
function pricedOffer(overrides: Record<string, unknown> = {}) {
  return {
    ...offerWithOneSeatLeft(),
    totalSeats: 3,
    totalCost: 60.0,
    costPerRider: 15.0,
    ...overrides,
  };
}

describe("posting a ride: the money fields are bounded", () => {
  test("a host may post a ride carrying a trip cost and the share derived from it", async () => {
    await assertSucceeds(
      (await as(HOST)).doc("trip_offers/offer-new").set(pricedOffer({ id: "offer-new" })),
    );
  });

  test("a trip cost at the seat-scaled ceiling is allowed", async () => {
    // Three seats plus the driver at MAX_CONTRIBUTION each: exactly $2000, exactly $500 a share.
    await assertSucceeds(
      (await as(HOST)).doc("trip_offers/offer-new").set(
        pricedOffer({ id: "offer-new", totalSeats: 3, totalCost: 2000.0, costPerRider: 500.0 }),
      ),
    );
  });

  test("a trip cost above that ceiling is denied", async () => {
    await assertFails(
      (await as(HOST)).doc("trip_offers/offer-new").set(
        pricedOffer({ id: "offer-new", totalSeats: 3, totalCost: 2000.01, costPerRider: 500.0 }),
      ),
    );
  });

  test("the ceiling scales with the seat count — the same cost on a bigger ride is fine", async () => {
    // The cap is a bound on the *share*, so eight seats may carry four times the three-seat limit.
    await assertSucceeds(
      (await as(HOST)).doc("trip_offers/offer-new").set(
        pricedOffer({ id: "offer-new", totalSeats: 8, totalCost: 2000.01, costPerRider: 222.23 }),
      ),
    );
  });

  test("a negative trip cost is denied", async () => {
    await assertFails(
      (await as(HOST)).doc("trip_offers/offer-new").set(
        pricedOffer({ id: "offer-new", totalCost: -1.0, costPerRider: 0.0 }),
      ),
    );
  });

  test("a per-rider share above the cap is denied even when the trip cost is in range", async () => {
    // The two bounds are independent: a small trip cost paired with a hand-written share is
    // exactly what a modified client would send, since it never has to do the division.
    await assertFails(
      (await as(HOST)).doc("trip_offers/offer-new").set(
        pricedOffer({ id: "offer-new", totalCost: 60.0, costPerRider: 900.0 }),
      ),
    );
  });

  test("an offer posted before `totalCost` existed still validates — the field defaults to 0", async () => {
    const { totalCost, ...legacy } = pricedOffer({ id: "offer-new" });
    await assertSucceeds((await as(HOST)).doc("trip_offers/offer-new").set(legacy));
  });
});

describe("taking a seat: a rider cannot move the price", () => {
  beforeEach(async () => {
    await seed("trip_offers/offer-1", pricedOffer());
    await seed(`users/${RIDER}/private/profile`, { gender: "female" });
  });

  /** The manifest change a legitimate join makes, with nothing else attached. */
  const join = {
    passengers: ["earlier-rider", RIDER],
    passengerNames: ["Sam", "Rae"],
    seatsLeft: 0,
    status: "full",
  };

  test("the bare seat claim is allowed", async () => {
    await assertSucceeds((await as(RIDER)).doc("trip_offers/offer-1").update(join));
  });

  test("the same claim is denied the moment it also lowers `costPerRider`", async () => {
    await assertFails(
      (await as(RIDER)).doc("trip_offers/offer-1").update({ ...join, costPerRider: 1.0 }),
    );
  });

  test("…and denied when it raises it, which is what a live re-split would have to do when a rider leaves", async () => {
    await assertFails(
      (await as(RIDER)).doc("trip_offers/offer-1").update({ ...join, costPerRider: 20.0 }),
    );
  });

  test("…and denied when it rewrites `totalCost`, the dividend the share is justified by", async () => {
    await assertFails(
      (await as(RIDER)).doc("trip_offers/offer-1").update({ ...join, totalCost: 6.0 }),
    );
  });

  test("a rider cannot change the price without touching the manifest at all", async () => {
    await assertFails(
      (await as(RIDER)).doc("trip_offers/offer-1").update({ costPerRider: 0.01 }),
    );
  });

  test("the host may still reprice their own ride", async () => {
    // The host owns the document; the restriction above is on everyone else.
    await assertSucceeds(
      (await as(HOST)).doc("trip_offers/offer-1").update({ totalCost: 80.0, costPerRider: 20.0 }),
    );
  });
});
