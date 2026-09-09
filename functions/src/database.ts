import { defineString } from "firebase-functions/params";
import { getFirestore, Firestore } from "firebase-admin/firestore";

/**
 * The Firestore database every trigger and job in this codebase binds to.
 *
 * This project's Firestore database was created with the id `splitcruiser`, **not** the
 * `(default)` database every project gets automatically. That distinction is load-bearing and has
 * bitten this repo twice:
 *
 * - The clients read `…/databases/splitcruiser/documents` (`FirebaseConfig.firestoreBase`), so a
 *   function bound to `(default)` sees an empty database and never fires. Every function here was
 *   in exactly that state — `getFirestore()` with no argument and `onDocumentWritten("path", …)`
 *   with no `database` option both resolve to `(default)`.
 * - `firebase.json` has to name the database too, or the rules and indexes deploy to `(default)`
 *   while the app reads `splitcruiser`.
 *
 * Defined once here so a future function cannot re-introduce the split by forgetting an argument.
 * Override per-environment with the `FIRESTORE_DATABASE_ID` parameter — the emulator and any
 * throwaway project that kept the default id need `(default)`.
 */
export const databaseId = defineString("FIRESTORE_DATABASE_ID", {
  default: "splitcruiser",
  description:
    "Firestore database id these functions bind to. Must match FIRESTORE_DATABASE_ID in the app build.",
});

/** The Admin SDK handle for [databaseId]. Never call `getFirestore()` without an argument here. */
export function db(): Firestore {
  return getFirestore(databaseId.value());
}
