import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterAll, afterEach, beforeAll } from "vitest";
import { clearSession } from "../auth/session";
import { server } from "./server";

// Any request without a matching handler fails the test, so no test can silently hit a real network.
beforeAll(() => server.listen({ onUnhandledFrame: "error" }));
afterEach(() => {
  server.resetHandlers();
  cleanup();
  clearSession();
});
afterAll(() => server.close());
