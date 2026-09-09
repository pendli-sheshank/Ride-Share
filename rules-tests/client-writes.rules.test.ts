import { describe, test, beforeAll, afterAll, beforeEach } from "vitest";
import {
  as, seed, clear, teardown, assertFails, assertSucceeds,
  HOST, RIDER, STRANGER, offerWithOneSeatLeft,
} from "./helpers";

/**
 * The rule contracts behind four writes `SplitCruiserRepository` issues that a live Firestore
 * rejects.
 *
 * Each of these shipped because the repository's own tests run against a Ktor MockEngine that
 * accepts everything, and three of the four are wrapped in `runCatching {}` on the client, so the
 * denial is swallowed and nothing surfaces. The tests here pin the *rule* side of each contract;
 * the matching client-side assertions live in `SplitCruiserRepositoryTest`.
 */

beforeAll(async () => { await clear(); });
beforeEach(async () => { await clear(); });
afterAll(async () => { await teardown(); });

describe("messages: a delivered message's key fields are immutable", () => {
  const proposal = {
    id: "msg-1", matchId: "m1", senderId: HOST, participants: [HOST, RIDER],
    text: "Pickup at the library", timestamp: 1_700_000_000_000, contribution: 12.0,
  };

  test("re-sending `timestamp` on an update is denied — this is what a second 'confirm pickup' tap did", async () => {
    await seed("messages/msg-1", proposal);
    // confirmPickupProposal builds a deterministic document id, so a second tap is an UPDATE, and
    // it set `timestamp = nowMs()` unconditionally. The changed set therefore included `timestamp`.
    await assertFails((await as(HOST)).doc("messages/msg-1").update({
      text: "Pickup at the library", timestamp: Date.now(),
    }));
  });

  test("the same update without `timestamp` is allowed", async () => {
    await seed("messages/msg-1", proposal);
    await assertSucceeds((await as(HOST)).doc("messages/msg-1").update({ text: "Pickup out front" }));
  });

  test("the agreed contribution cannot be rewritten after both sides have seen it", async () => {
    await seed("messages/msg-1", proposal);
    await assertFails((await as(HOST)).doc("messages/msg-1").update({ contribution: 2.0 }));
  });

  test("a non-participant cannot read the thread", async () => {
    await seed("messages/msg-1", proposal);
    await assertFails((await as(STRANGER)).doc("messages/msg-1").get());
  });

  test("a message written with only the sender in `participants` is unreadable by the other party", async () => {
    // participantsFor() falls back to listOf(userId) on a cache miss, which produces exactly this
    // document — readable by its sender and nobody else, forever. The rule is correct; the client
    // must not write this shape.
    await seed("messages/msg-2", { ...proposal, id: "msg-2", participants: [HOST] });
    await assertSucceeds((await as(HOST)).doc("messages/msg-2").get());
    await assertFails((await as(RIDER)).doc("messages/msg-2").get());
  });
});

describe("trip_offers: a non-host cannot close an expired ride", () => {
  test("`status: closed` from a rider is denied — closeIfExpired issued this on every fetch", async () => {
    await seed("trip_offers/o1", offerWithOneSeatLeft({
      departureTime: Date.now() - 3_600_000, passengers: [RIDER], passengerNames: ["Ray"],
    }));
    // The non-host branch only permits status in ['active','full'], so this is always denied — and
    // closeIfExpired then returned copy(status="closed") anyway and cached the lie.
    await assertFails((await as(RIDER)).doc("trip_offers/o1").update({ status: "closed" }));
  });

  test("the host closing their own expired ride is allowed", async () => {
    await seed("trip_offers/o1", offerWithOneSeatLeft({ departureTime: Date.now() - 3_600_000 }));
    await assertSucceeds((await as(HOST)).doc("trip_offers/o1").update({ status: "closed" }));
  });

  test("a rider may close their own expired request — the asymmetry that hid the offer bug", async () => {
    await seed("ride_requests/r1", {
      id: "r1", riderId: RIDER, status: "active", departureTime: Date.now() - 3_600_000,
      origin: "A", destination: "B", seatsNeeded: 1,
    });
    await assertSucceeds((await as(STRANGER)).doc("ride_requests/r1").update({ status: "closed" }));
  });
});

describe("no_show_reports: immutable once filed", () => {
  const report = { id: "noshow-1", reporterId: RIDER, targetId: HOST, timestamp: Date.now() };

  test("filing a report is allowed", async () => {
    await assertSucceeds((await as(RIDER)).doc("no_show_reports/noshow-1").set(report));
  });

  test("re-filing against the same person is denied — the id is deterministic, so it is an update", async () => {
    await seed("no_show_reports/noshow-1", report);
    await assertFails((await as(RIDER)).doc("no_show_reports/noshow-1").set({
      ...report, timestamp: Date.now() + 1000,
    }));
  });

  test("a reporter cannot delete their report to re-file it", async () => {
    await seed("no_show_reports/noshow-1", report);
    await assertFails((await as(RIDER)).doc("no_show_reports/noshow-1").delete());
  });

  test("self-reporting is denied", async () => {
    await assertFails((await as(RIDER)).doc("no_show_reports/noshow-2").set({
      id: "noshow-2", reporterId: RIDER, targetId: RIDER, timestamp: Date.now(),
    }));
  });
});

describe("notifications: the timestamp is bound to server time", () => {
  const alert = (timestamp: number) => ({
    id: "n1", userId: RIDER, title: "Ride Request Accepted!", message: "Your ride was accepted.",
    type: "ride_accepted", timestamp, isRead: false,
  });

  test("a notification dated from an accurate clock is accepted", async () => {
    await assertSucceeds((await as(HOST)).doc("notifications/n1").set(alert(Date.now())));
  });

  test("a device clock 10 minutes fast has its notification denied", async () => {
    // sendNotificationAlert uses nowMs() — the device clock — and swallows the failure, so the
    // recipient is simply never told they were accepted.
    await assertFails((await as(HOST)).doc("notifications/n1").set(alert(Date.now() + 600_000)));
  });

  test("a device clock 10 minutes slow has its notification denied", async () => {
    await assertFails((await as(HOST)).doc("notifications/n1").set(alert(Date.now() - 600_000)));
  });

  test("a notification cannot be back-dated or future-dated to pin itself to the top of the list", async () => {
    await assertFails((await as(HOST)).doc("notifications/n1").set(alert(Date.now() + 86_400_000)));
  });

  test("only the recipient may read it, and only to mark it read", async () => {
    await seed("notifications/n1", alert(Date.now()));
    await assertSucceeds((await as(RIDER)).doc("notifications/n1").get());
    await assertFails((await as(STRANGER)).doc("notifications/n1").get());
    await assertSucceeds((await as(RIDER)).doc("notifications/n1").update({ isRead: true }));
    await assertFails((await as(RIDER)).doc("notifications/n1").update({ title: "Rewritten" }));
  });
});
