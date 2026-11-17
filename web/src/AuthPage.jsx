import { useEffect, useState } from "react";
import { logIn, requestPasswordReset, resetPassword, signUp } from "./api";

function LoginForm({ notice, onAuthenticated, onShowSignUp, onShowForgot }) {
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);

    async function handleSubmit(e) {
        e.preventDefault();
        setSubmitting(true);
        try {
            onAuthenticated(await logIn(email, password));
        } catch (err) {
            setError(err.message);
            setSubmitting(false);
        }
    }

    return (
        <form className="auth-card" onSubmit={handleSubmit}>
            <h2>Log in</h2>
            {notice && <p className="success">{notice}</p>}
            <label>
                Email
                <input type="email" autoComplete="email" required
                       value={email} onChange={e => setEmail(e.target.value)} />
            </label>
            <label>
                Password
                <input type="password" autoComplete="current-password" required
                       value={password} onChange={e => setPassword(e.target.value)} />
            </label>
            {error && <p className="error">{error}</p>}
            <button type="submit" disabled={submitting}>
                {submitting ? "Logging in…" : "Log in"}
            </button>
            <div className="auth-links">
                <button type="button" className="link-button" onClick={onShowForgot}>
                    Forgot your password?
                </button>
                <span>
                    Don't have an account?{" "}
                    <button type="button" className="link-button" onClick={onShowSignUp}>Sign up</button>
                </span>
            </div>
        </form>
    );
}

function SignUpForm({ onAuthenticated, onShowLogin }) {
    const [name, setName] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [confirm, setConfirm] = useState("");
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);

    async function handleSubmit(e) {
        e.preventDefault();
        if (password !== confirm) {
            setError("Passwords don't match");
            return;
        }
        setSubmitting(true);
        try {
            onAuthenticated(await signUp(name, email, password));
        } catch (err) {
            setError(err.message);
            setSubmitting(false);
        }
    }

    return (
        <form className="auth-card" onSubmit={handleSubmit}>
            <h2>Create an account</h2>
            <label>
                Name
                <input autoComplete="name" required
                       value={name} onChange={e => setName(e.target.value)} />
            </label>
            <label>
                Email
                <input type="email" autoComplete="email" required
                       value={email} onChange={e => setEmail(e.target.value)} />
            </label>
            <label>
                Password
                <input type="password" autoComplete="new-password" required minLength={8} maxLength={72}
                       value={password} onChange={e => setPassword(e.target.value)} />
            </label>
            <label>
                Confirm password
                <input type="password" autoComplete="new-password" required
                       value={confirm} onChange={e => setConfirm(e.target.value)} />
            </label>
            {error && <p className="error">{error}</p>}
            <button type="submit" disabled={submitting}>
                {submitting ? "Creating account…" : "Sign up"}
            </button>
            <div className="auth-links">
                <span>
                    Already have an account?{" "}
                    <button type="button" className="link-button" onClick={onShowLogin}>Log in</button>
                </span>
            </div>
        </form>
    );
}

const RESEND_COOLDOWN_SECONDS = 60;
// The server sends at most this many reset emails per account per hour (and silently ignores more).
const MAX_EMAILS_PER_HOUR = 3;

function ForgotPasswordForm({ onShowLogin }) {
    const [email, setEmail] = useState("");
    const [sentTo, setSentTo] = useState(null);
    const [sendCount, setSendCount] = useState(0);
    const [resent, setResent] = useState(false);
    const [cooldownUntil, setCooldownUntil] = useState(0);
    const [now, setNow] = useState(() => Date.now());
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);

    // Tick once a second while the resend button is cooling down, to update its countdown.
    useEffect(() => {
        if (cooldownUntil <= Date.now()) return undefined;
        const timer = setInterval(() => setNow(Date.now()), 1000);
        return () => clearInterval(timer);
    }, [cooldownUntil]);

    const secondsLeft = Math.max(0, Math.ceil((cooldownUntil - now) / 1000));
    const reachedLimit = sendCount >= MAX_EMAILS_PER_HOUR;

    async function send(address) {
        setSubmitting(true);
        try {
            await requestPasswordReset(address);
            setSentTo(address);
            setSendCount(n => n + 1);
            setCooldownUntil(Date.now() + RESEND_COOLDOWN_SECONDS * 1000);
            setNow(Date.now());
            setError("");
            return true;
        } catch (err) {
            setError(err.message);
            return false;
        } finally {
            setSubmitting(false);
        }
    }

    async function handleSubmit(e) {
        e.preventDefault();
        setResent(false);
        await send(email.trim());
    }

    async function handleResend() {
        if (await send(sentTo)) setResent(true);
    }

    function useDifferentEmail() {
        setSentTo(null);
        setSendCount(0);
        setResent(false);
        setCooldownUntil(0);
        setError("");
    }

    return (
        <form className="auth-card" onSubmit={handleSubmit}>
            <h2>Reset your password</h2>
            {sentTo ? (
                <>
                    <p className="notice">
                        If an account exists for <strong>{sentTo}</strong>, we've sent it a link to reset the
                        password. The link expires in 30 minutes. Check your spam folder if it doesn't arrive.
                    </p>
                    {resent && (
                        <p className="success">
                            We've sent another link. Only the newest link works, so use the latest email.
                        </p>
                    )}
                    {error && <p className="error">{error}</p>}
                    {reachedLimit ? (
                        <p className="muted small">
                            You've asked for {MAX_EMAILS_PER_HOUR} emails. To prevent spam we send at most
                            {" "}{MAX_EMAILS_PER_HOUR} an hour, so please wait before asking again.
                        </p>
                    ) : (
                        <button
                            type="button"
                            className="secondary"
                            onClick={handleResend}
                            disabled={submitting || secondsLeft > 0}
                        >
                            {submitting ? "Sending…" : secondsLeft > 0 ? `Resend email in ${secondsLeft}s` : "Resend email"}
                        </button>
                    )}
                    <button type="button" className="link-button" onClick={useDifferentEmail}>
                        Use a different email
                    </button>
                </>
            ) : (
                <>
                    <p className="muted">Enter the email linked to your account and we'll send you a reset link.</p>
                    <label>
                        Email
                        <input type="email" autoComplete="email" required autoFocus
                               value={email} onChange={e => setEmail(e.target.value)} />
                    </label>
                    {error && <p className="error">{error}</p>}
                    <button type="submit" disabled={submitting}>
                        {submitting ? "Sending…" : "Send reset link"}
                    </button>
                </>
            )}
            <div className="auth-links">
                <button type="button" className="link-button" onClick={onShowLogin}>Back to log in</button>
            </div>
        </form>
    );
}

/** Opened from the link in the reset email. */
function ResetPasswordForm({ token, onDone, onShowForgot }) {
    const [password, setPassword] = useState("");
    const [confirm, setConfirm] = useState("");
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);

    async function handleSubmit(e) {
        e.preventDefault();
        if (password !== confirm) {
            setError("Passwords don't match");
            return;
        }
        setSubmitting(true);
        try {
            await resetPassword(token, password);
            onDone();
        } catch (err) {
            setError(err.message);
            setSubmitting(false);
        }
    }

    const linkProblem = error.startsWith("This reset link");

    return (
        <form className="auth-card" onSubmit={handleSubmit}>
            <h2>Choose a new password</h2>
            <label>
                New password
                <input type="password" autoComplete="new-password" required minLength={8} maxLength={72} autoFocus
                       value={password} onChange={e => setPassword(e.target.value)} />
            </label>
            <label>
                Confirm new password
                <input type="password" autoComplete="new-password" required
                       value={confirm} onChange={e => setConfirm(e.target.value)} />
            </label>
            {error && <p className="error">{error}</p>}
            <button type="submit" disabled={submitting}>
                {submitting ? "Saving…" : "Set new password"}
            </button>
            <p className="muted small">This will log you out on every other device.</p>
            <div className="auth-links">
                {linkProblem && (
                    <button type="button" className="link-button" onClick={onShowForgot}>Send a new link</button>
                )}
            </div>
        </form>
    );
}

/**
 * Log in / sign up / forgot password. If the page was opened from a reset email,
 * `resetToken` is set and the "choose a new password" form is shown first.
 */
export default function AuthPage({ onAuthenticated, resetToken = null }) {
    const [view, setView] = useState(resetToken ? "reset" : "login");
    const [notice, setNotice] = useState("");

    function show(nextView) {
        setNotice("");
        setView(nextView);
    }

    if (view === "reset") {
        return (
            <ResetPasswordForm
                token={resetToken}
                onDone={() => {
                    setView("login");
                    setNotice("Your password has been changed. Log in with your new password.");
                }}
                onShowForgot={() => show("forgot")}
            />
        );
    }
    if (view === "signup") {
        return <SignUpForm onAuthenticated={onAuthenticated} onShowLogin={() => show("login")} />;
    }
    if (view === "forgot") {
        return <ForgotPasswordForm onShowLogin={() => show("login")} />;
    }
    return (
        <LoginForm
            notice={notice}
            onAuthenticated={onAuthenticated}
            onShowSignUp={() => show("signup")}
            onShowForgot={() => show("forgot")}
        />
    );
}
