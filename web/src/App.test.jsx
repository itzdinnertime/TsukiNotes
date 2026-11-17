import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("./api");

afterEach(() => {
    window.history.replaceState(null, "", "/");
});

/** App reads the reset link once, when its module first loads, so each test loads a fresh copy. */
async function loadApp() {
    vi.resetModules();
    const api = await import("./api");
    vi.mocked(api.getToken).mockReturnValue(null);
    return (await import("./App")).default;
}

describe("opening the app", () => {
    it("shows the log in page when nobody is logged in", async () => {
        const App = await loadApp();
        render(<App />);

        expect(screen.getByRole("heading", { name: "Log in" })).toBeInTheDocument();
    });

    it("opens 'Choose a new password' from a reset link, and removes the token from the address bar", async () => {
        window.history.replaceState(null, "", "/reset-password?token=secret-token-123");

        const App = await loadApp();
        render(<App />);

        expect(screen.getByRole("heading", { name: "Choose a new password" })).toBeInTheDocument();
        expect(window.location.pathname).toBe("/");
        expect(window.location.search).toBe("");
    });
});
