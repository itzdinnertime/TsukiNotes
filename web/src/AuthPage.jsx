import { useState } from "react";
import { logIn, signUp } from "./api";

function LoginForm({ onAuthenticated, onShowSignUp, onShowForgot }) {
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

function ForgotPasswordForm({ onShowLogin }) {
    const [email, setEmail] = useState("");
    const [submitted, setSubmitted] = useState(false);

    function handleSubmit(e) {
        e.preventDefault();
        // TODO: call a password-reset endpoint once email sending is set up.
        setSubmitted(true);
    }

    return (
        <form className="auth-card" onSubmit={handleSubmit}>
            <h2>Reset your password</h2>
            {submitted ? (
                <p className="notice">
                    Password reset by email isn't available yet. Please contact the site owner to reset your password.
                </p>
            ) : (
                <>
                    <p className="muted">Enter the email linked to your account.</p>
                    <label>
                        Email
                        <input type="email" autoComplete="email" required
                               value={email} onChange={e => setEmail(e.target.value)} />
                    </label>
                    <button type="submit">Send reset link</button>
                </>
            )}
            <div className="auth-links">
                <button type="button" className="link-button" onClick={onShowLogin}>Back to log in</button>
            </div>
        </form>
    );
}

export default function AuthPage({ onAuthenticated }) {
    const [view, setView] = useState("login");

    if (view === "signup") {
        return <SignUpForm onAuthenticated={onAuthenticated} onShowLogin={() => setView("login")} />;
    }
    if (view === "forgot") {
        return <ForgotPasswordForm onShowLogin={() => setView("login")} />;
    }
    return (
        <LoginForm
            onAuthenticated={onAuthenticated}
            onShowSignUp={() => setView("signup")}
            onShowForgot={() => setView("forgot")}
        />
    );
}
