import { useEffect, useState } from "react";
import { getCurrentIdentity, getToken, logOut, setUnauthorizedHandler } from "./api";
import AuthPage from "./AuthPage";
import HomeScreen from "./HomeScreen";

/**
 * Password reset emails link to /reset-password?token=... Read the token once when the app loads,
 * then remove it from the address bar so it doesn't stay in the browser history.
 * (Done at module level, not in a component, so it runs exactly once even in React's StrictMode.)
 */
function takeResetTokenFromUrl() {
    if (window.location.pathname !== "/reset-password") return null;
    const token = new URLSearchParams(window.location.search).get("token");
    window.history.replaceState(null, "", "/");
    return token || null;
}

const initialResetToken = takeResetTokenFromUrl();

function App() {
    const [identity, setIdentity] = useState(null);
    const [resetToken, setResetToken] = useState(initialResetToken);
    // If a token is saved from an earlier visit, ask the server whether it's still valid before showing anything.
    const [checkingSession, setCheckingSession] = useState(() => Boolean(getToken()));

    useEffect(() => {
        setUnauthorizedHandler(() => setIdentity(null));

        if (getToken()) {
            getCurrentIdentity()
                .then(setIdentity)
                .catch(() => setIdentity(null))
                .finally(() => setCheckingSession(false));
        }
    }, []);

    function handleAuthenticated(id) {
        setResetToken(null);
        setIdentity(id);
    }

    async function handleLogOut() {
        try {
            await logOut();
        } catch {
            // Even if the server call fails, the token has been discarded locally.
        }
        setIdentity(null);
    }

    // A reset link always shows the reset form, even if someone is already logged in on this browser.
    if (resetToken) {
        return <AuthPage resetToken={resetToken} onAuthenticated={handleAuthenticated} />;
    }

    if (checkingSession) {
        return <p className="muted">Loading…</p>;
    }

    if (!identity) {
        return <AuthPage onAuthenticated={handleAuthenticated} />;
    }

    return <HomeScreen identity={identity} onLogOut={handleLogOut} />;
}

export default App;
