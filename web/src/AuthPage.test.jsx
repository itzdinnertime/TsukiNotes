import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AuthPage from "./AuthPage";
import * as api from "./api";

vi.mock("./api");

beforeEach(() => {
    vi.mocked(api.logIn).mockReset();
    vi.mocked(api.signUp).mockReset();
    vi.mocked(api.requestPasswordReset).mockReset().mockResolvedValue(null);
    vi.mocked(api.resetPassword).mockReset().mockResolvedValue(undefined);
});

describe("log in", () => {
    it("sends the email and password and hands the user to the app", async () => {
        const user = userEvent.setup();
        const onAuthenticated = vi.fn();
        vi.mocked(api.logIn).mockResolvedValue({ id: 1, name: "Ada" });
        render(<AuthPage onAuthenticated={onAuthenticated} />);

        await user.type(screen.getByLabelText("Email"), "ada@example.com");
        await user.type(screen.getByLabelText("Password"), "password123");
        await user.click(screen.getByRole("button", { name: "Log in" }));

        expect(api.logIn).toHaveBeenCalledWith("ada@example.com", "password123");
        expect(onAuthenticated).toHaveBeenCalledWith({ id: 1, name: "Ada" });
    });

    it("shows the server's message when logging in fails", async () => {
        const user = userEvent.setup();
        vi.mocked(api.logIn).mockRejectedValue(new Error("Incorrect email or password. 2 attempts left before logins are paused for 15 minutes."));
        render(<AuthPage onAuthenticated={vi.fn()} />);

        await user.type(screen.getByLabelText("Email"), "ada@example.com");
        await user.type(screen.getByLabelText("Password"), "wrong-password");
        await user.click(screen.getByRole("button", { name: "Log in" }));

        expect(await screen.findByText(/2 attempts left/)).toBeInTheDocument();
    });
});

describe("sign up", () => {
    it("checks the two passwords match before calling the server", async () => {
        const user = userEvent.setup();
        render(<AuthPage onAuthenticated={vi.fn()} />);
        await user.click(screen.getByRole("button", { name: "Sign up" }));

        await user.type(screen.getByLabelText("Name"), "Ada");
        await user.type(screen.getByLabelText("Email"), "ada@example.com");
        await user.type(screen.getByLabelText("Password"), "password123");
        await user.type(screen.getByLabelText("Confirm password"), "password124");
        await user.click(screen.getByRole("button", { name: "Sign up" }));

        expect(screen.getByText("Passwords don't match")).toBeInTheDocument();
        expect(api.signUp).not.toHaveBeenCalled();
    });
});

describe("forgot password", () => {
    async function requestLinkFor(user, email) {
        await user.click(screen.getByRole("button", { name: "Forgot your password?" }));
        await user.type(screen.getByLabelText("Email"), email);
        await user.click(screen.getByRole("button", { name: "Send reset link" }));
    }

    it("asks the server for a link and shows a message that doesn't reveal whether the account exists", async () => {
        const user = userEvent.setup();
        render(<AuthPage onAuthenticated={vi.fn()} />);

        await requestLinkFor(user, "ada@example.com");

        expect(api.requestPasswordReset).toHaveBeenCalledWith("ada@example.com");
        expect(await screen.findByText(/If an account exists for/)).toBeInTheDocument();
    });

    it("lets you resend after a 60 second wait, and says only the newest link works", async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
        render(<AuthPage onAuthenticated={vi.fn()} />);
        await requestLinkFor(user, "ada@example.com");

        const resend = await screen.findByRole("button", { name: /Resend email in \d+s/ });
        expect(resend).toBeDisabled();

        await vi.advanceTimersByTimeAsync(60_000);
        const ready = await screen.findByRole("button", { name: "Resend email" });
        expect(ready).toBeEnabled();

        await user.click(ready);

        expect(api.requestPasswordReset).toHaveBeenCalledTimes(2);
        expect(await screen.findByText(/Only the newest link works/)).toBeInTheDocument();
    });

    it("stops offering to resend after three emails", async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
        render(<AuthPage onAuthenticated={vi.fn()} />);
        await requestLinkFor(user, "ada@example.com");

        for (let i = 0; i < 2; i++) {
            await vi.advanceTimersByTimeAsync(60_000);
            await user.click(await screen.findByRole("button", { name: "Resend email" }));
        }

        expect(api.requestPasswordReset).toHaveBeenCalledTimes(3);
        expect(screen.queryByRole("button", { name: /Resend email/ })).not.toBeInTheDocument();
        expect(screen.getByText(/we send at most 3 an hour/)).toBeInTheDocument();
    });
});

describe("choose a new password (from the email link)", () => {
    it("sets the password and returns to log in with a confirmation", async () => {
        const user = userEvent.setup();
        render(<AuthPage resetToken="token-from-email" onAuthenticated={vi.fn()} />);

        await user.type(screen.getByLabelText("New password"), "newpassword1");
        await user.type(screen.getByLabelText("Confirm new password"), "newpassword1");
        await user.click(screen.getByRole("button", { name: "Set new password" }));

        expect(api.resetPassword).toHaveBeenCalledWith("token-from-email", "newpassword1");
        expect(await screen.findByText(/Your password has been changed/)).toBeInTheDocument();
        expect(screen.getByRole("heading", { name: "Log in" })).toBeInTheDocument();
    });

    it("offers to send a new link when the link is expired or used", async () => {
        const user = userEvent.setup();
        vi.mocked(api.resetPassword).mockRejectedValue(
            new Error("This reset link is invalid or has expired. Please request a new one."));
        render(<AuthPage resetToken="old-token" onAuthenticated={vi.fn()} />);

        await user.type(screen.getByLabelText("New password"), "newpassword1");
        await user.type(screen.getByLabelText("Confirm new password"), "newpassword1");
        await user.click(screen.getByRole("button", { name: "Set new password" }));
        await user.click(await screen.findByRole("button", { name: "Send a new link" }));

        expect(screen.getByRole("heading", { name: "Reset your password" })).toBeInTheDocument();
    });
});
