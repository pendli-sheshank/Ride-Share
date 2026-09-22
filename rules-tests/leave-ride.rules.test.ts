import { describe, test, beforeAll, afterAll, beforeEach } from "vitest";
import {
  as, seed, clear, teardown, assertFails, assertSucceeds,
  HOST, RIDER, STRANGER, offerWithOneSeatLeft,
} from "./helpers";

/**
 * The rule half of leaving a ride.
 *
 * `releaseSeat` has always been correct and, until now, unreachable: its only caller was
 * `declineMatch`, guarded on `status == "accepted"`, while every `declineMatch` control on both
 * platforms is a *host* declining a *pending* match. `cancelMatch` — the function that should have
 * been the way out of a booked ride — set the match status and never touched the manifest, and had
 * no caller on either platform at all.
 *
 * Now that both paths reach the manifest, the rules have to say who may take a rider off it. The
 * "Leaving" branch of the `trip_offers` update rule was written for exactly this and had never had
 * a test, because nothing in the product had ever exercised it.
 */

beforeAll(async () => { await clear(); });
beforeEach(async () => { await clear(); });
afterAll(async () => { await teardown(); });

/** A three-seat ride with RIDER and one other person aboard, one seat spare. */
function rideCarrying(overrides: Record<string, unknown> = {}) {
  return {
    ...offerWithOneSeatLeft(),
    totalSeats: 3,
    seatsLeft: 1,
    passengers: [RIDER, "other-rider"],
    passengerNames: ["Rae", "Sam"],
    status: "active",
    ...overrides,
  };
}

/** The manifest after RIDER steps off: one fewer passenger, one more seat. */
const riderLeaves = {
  passengers: ["other-rider"],
  passengerNames: ["Sam"],
  seatsLeft: 2,
  status: "active",
};

describe("a rider gives their own seat back", () => {
  beforeEach(async () => { await seed("trip_offers/offer-1", rideCarrying()); });

  test("the rider may take themselves off the manifest", async () => {
    await assertSucceeds((await as(RIDER)).doc("trip_offers/offer-1").update(riderLeaves));
  });

  test("a full ride reopens when the seat comes back", async () => {
    await seed("trip_offers/offer-1", rideCarrying({ seatsLeft: 0, status: "full" }));
    await assertSucceeds(
      (await as(RIDER)).doc("trip_offers/offer-1").update({ ...riderLeaves, seatsLeft: 1 }),
    );
  });

  test("the host may take a rider off their own ride", async () => {
    // The host cancelling an accepted match releases the *rider's* seat, which the leaving branch
    // does not cover — it is the host branch that allows this.
    await assertSucceeds((await as(HOST)).doc("trip_offers/offer-1").update(riderLeaves));
  });

  test("a stranger may not remove a passenger", async () => {
    await assertFails((await as(STRANGER)).doc("trip_offers/offer-1").update(riderLeaves));
  });

  test("a rider may not remove someone else while leaving", async () => {
    // `passengers.size() == resource.data.passengers.size() - 1` plus `hasAll` is what stops a
    // leaver from clearing the manifest on the way out.
    await assertFails(
      (await as(RIDER)).doc("trip_offers/offer-1").update({
        passengers: [], passengerNames: [], seatsLeft: 3, status: "active",
      }),
    );
  });

  test("leaving without returning the seat is denied", async () => {
    // Without the direction check a rider could step off and leave the ride reading as full,
    // which is how a seat silently disappears for everyone else.
    await assertFails(
      (await as(RIDER)).doc("trip_offers/offer-1").update({ ...riderLeaves, seatsLeft: 1 }),
    );
  });

  test("a rider may not hand back more seats than they took", async () => {
    await assertFails(
      (await as(RIDER)).doc("trip_offers/offer-1").update({ ...riderLeaves, seatsLeft: 9 }),
    );
  });

  test("the parallel name array must shrink with the id array", async () => {
    // The UI zips these two; a length mismatch pairs a name with the wrong rider.
    await assertFails(
      (await as(RIDER)).doc("trip_offers/offer-1").update({
        passengers: ["other-rider"], passengerNames: ["Rae", "Sam"], seatsLeft: 2, status: "active",
      }),
    );
  });
});

describe("settling the match itself", () => {
  const match = {
    id: "m1", offerId: "offer-1", requestId: "req-1",
    hostId: HOST, riderId: RIDER, participants: [HOST, RIDER],
    status: "accepted", contribution: 12.0, timestamp: 1_700_000_000_000,
  };

  beforeEach(async () => { await seed("trip_matches/m1", match); });

  test("a rider may cancel their own match", async () => {
    await assertSucceeds((await as(RIDER)).doc("trip_matches/m1").update({ status: "cancelled" }));
  });

  test("a host may mark the match complete", async () => {
    await assertSucceeds((await as(HOST)).doc("trip_matches/m1").update({ status: "completed" }));
  });

  test("a stranger may touch neither", async () => {
    await assertFails((await as(STRANGER)).doc("trip_matches/m1").update({ status: "completed" }));
  });

  /**
   * The rules cannot tell a host's `completed` from a rider's — both are participants, and
   * `trip_matches` allows a participant to update. The host-only restriction is enforced in
   * `SplitCruiserRepository.completeTrip` and covered by `onlyTheHostCanMarkARideComplete` in the
   * `:shared` suite. Recorded here so the gap is deliberate rather than assumed closed.
   */
  test("a rider can still write `completed` at the rules layer — the client enforces the rest", async () => {
    await assertSucceeds((await as(RIDER)).doc("trip_matches/m1").update({ status: "completed" }));
  });
});
