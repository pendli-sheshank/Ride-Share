import { describe, test, expect } from "vitest";
import { deriveVerifiedTier, numberField } from "./reputation";

/**
 * `verifiedTier` renders as a trust badge on both platforms. Nothing wrote it before, so it stayed
 * at the "vouched" the client hardcoded at account creation and every account displayed the badge
 * from the moment it existed. These pin the thresholds that now decide it.
 */
describe("deriveVerifiedTier", () => {
  test("a brand-new account has earned nothing", () => {
    expect(deriveVerifiedTier({ ratingAvg: 0, ratingCount: 0, noShowCount: 0 })).toBe("guest");
  });

  test("one perfect rating is not enough to vouch for someone", () => {
    expect(deriveVerifiedTier({ ratingAvg: 5, ratingCount: 1, noShowCount: 0 })).toBe("guest");
    expect(deriveVerifiedTier({ ratingAvg: 5, ratingCount: 2, noShowCount: 0 })).toBe("guest");
  });

  test("three ratings averaging at least four earns the badge", () => {
    expect(deriveVerifiedTier({ ratingAvg: 4.0, ratingCount: 3, noShowCount: 0 })).toBe("vouched");
    expect(deriveVerifiedTier({ ratingAvg: 4.9, ratingCount: 12, noShowCount: 0 })).toBe("vouched");
  });

  test("a mediocre average does not, however many ratings", () => {
    expect(deriveVerifiedTier({ ratingAvg: 3.9, ratingCount: 100, noShowCount: 0 })).toBe("guest");
  });

  test("a single no-show drops the badge outright", () => {
    expect(deriveVerifiedTier({ ratingAvg: 5, ratingCount: 50, noShowCount: 1 })).toBe("guest");
  });
});

describe("numberField", () => {
  test("reads a numeric field", () => {
    expect(numberField({ ratingAvg: 4.5 }, "ratingAvg")).toBe(4.5);
  });

  test("treats a missing, undefined or wrongly-typed field as zero", () => {
    // A user document written before these fields existed simply has no value here, and the
    // aggregates must not become NaN and propagate into everyone's badge.
    expect(numberField(undefined, "ratingAvg")).toBe(0);
    expect(numberField({}, "ratingAvg")).toBe(0);
    expect(numberField({ ratingAvg: "4.5" }, "ratingAvg")).toBe(0);
    expect(numberField({ ratingAvg: null }, "ratingAvg")).toBe(0);
  });

  test("rejects non-finite values rather than propagating them", () => {
    expect(numberField({ ratingAvg: NaN }, "ratingAvg")).toBe(0);
    expect(numberField({ ratingAvg: Infinity }, "ratingAvg")).toBe(0);
  });
});
