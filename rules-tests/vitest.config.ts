import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    // The emulator is a real process over HTTP; the default 5s is tight for the first few calls
    // while it warms up, and a timeout here looks exactly like a rule denial.
    testTimeout: 20000,
    hookTimeout: 60000,
    // Rules tests share one emulator and clear it between files; running files in parallel makes
    // one file's clearFirestore() wipe another's fixtures mid-test.
    fileParallelism: false,
  },
});
