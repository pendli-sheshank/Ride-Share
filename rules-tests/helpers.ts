import {
  initializeTestEnvironment,
  RulesTestEnvironment,
  assertFails,
  assertSucceeds,
} from "@firebase/rules-unit-testing";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

export { assertFails, assertSucceeds };

/** Matches the ids the app actually uses, so a fixture reads like a real document. */
export const HOST = "host-uid";
export const RIDER = "rider-uid";
export const STRANGER = "stranger-uid";

let env: RulesTestEnvironment | undefined;

export async function getEnv(): Promise<RulesTestEnvironment> {
  if (env) return env;
  env = await initializeTestEnvironment({
    projectId: "splitcruiser-rules-test",
    firestore: {
      rules: readFileSync(resolve(__dirname, "../firestore.rules"), "utf8"),
      // `firebase emulators:exec` exports FIRESTORE_EMULATOR_HOST as host:port.
      host: (process.env.FIRESTORE_EMULATOR_HOST ?? "127.0.0.1:8080").split(":")[0],
      port: Number((process.env.FIRESTORE_EMULATOR_HOST ?? "127.0.0.1:8080").split(":")[1]),
    },
  });
  return env;
}

/** A Firestore handle authenticated as [uid], subject to the rules. */
export async function as(uid: string) {
  return (await getEnv()).authenticatedContext(uid).firestore();
}

/** A signed-out Firestore handle, subject to the rules. */
export async function anon() {
  return (await getEnv()).unauthenticatedContext().firestore();
}

/**
 * Seeds a document bypassing the rules, the way the Admin SDK or a previous legitimate write would
 * have. Fixtures must not be created through the rules under test, or the test is asserting on its
 * own setup.
 */
export async function seed(path: string, data: Record<string, unknown>) {
  const e = await getEnv();
  await e.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc(path).set(data);
  });
}

export async function readBypassingRules(path: string) {
  const e = await getEnv();
  let out: Record<string, unknown> | undefined;
  await e.withSecurityRulesDisabled(async (ctx) => {
    out = (await ctx.firestore().doc(path).get()).data();
  });
  return out;
}

export async function clear() {
  await (await getEnv()).clearFirestore();
}

export async function teardown() {
  if (env) await env.cleanup();
  env = undefined;
}

/** A trip offer with one seat left, hosted by [HOST]. */
export function offerWithOneSeatLeft(overrides: Record<string, unknown> = {}) {
  return {
    id: "offer-1",
    hostId: HOST,
    hostName: "Dana",
    origin: "Back Bay",
    destination: "Providence",
    totalSeats: 3,
    seatsLeft: 1,
    passengers: ["earlier-rider"],
    passengerNames: ["Sam"],
    status: "active",
    costPerRider: 12.0,
    departureTime: Date.now() + 3_600_000,
    womenOnly: false,
    ...overrides,
  };
}
