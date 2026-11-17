import { useEffect, useState } from "react";
import { getCurrentIdentity, getToken, logOut, setUnauthorizedHandler } from "./api";
import AuthPage from "./AuthPage";
import HomeScreen from "./HomeScreen";

function App() {
    const [identity, setIdentity] = useState(null);
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

    async function handleLogOut() {
        try {
            await logOut();
        } catch {
            // Even if the server call fails, the token has been discarded locally.
        }
        setIdentity(null);
    }

    if (checkingSession) {
        return <p className="muted">Loading…</p>;
    }

    if (!identity) {
        return <AuthPage onAuthenticated={setIdentity} />;
    }

    return <HomeScreen identity={identity} onLogOut={handleLogOut} />;
}

export default App;
