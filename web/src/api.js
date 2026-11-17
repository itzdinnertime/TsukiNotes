const API_BASE = "http://localhost:8080";
const INVITE_TOKEN = "tsuki-secret-6767";
const TOKEN_STORAGE_KEY = "tsukinotes.token";

let onUnauthorized = () => {};

export function getToken() {
    try {
        return localStorage.getItem(TOKEN_STORAGE_KEY);
    } catch {
        return null;
    }
}

function setToken(token) {
    try {
        if (token) {
            localStorage.setItem(TOKEN_STORAGE_KEY, token);
        } else {
            localStorage.removeItem(TOKEN_STORAGE_KEY);
        }
    } catch {
        // Storage unavailable (e.g. private mode); the session just won't survive a refresh.
    }
}

/** Registers a callback that runs when the server rejects our session (expired or logged out elsewhere). */
export function setUnauthorizedHandler(handler) {
    onUnauthorized = handler;
}

async function apiFetch(path, options = {}) {
    const token = getToken();
    const response = await fetch(`${API_BASE}${path}`, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            "X-Invite-Token": INVITE_TOKEN,
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
            ...options.headers,
        },
    });

    if (response.status === 401 && token) {
        setToken(null);
        onUnauthorized();
    }

    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || `Request failed: ${response.status}`);
    }

    return response.status === 204 ? null : response.json();
}

export function createItem(uniqueKey, type, content, tags) {
    return apiFetch("/items", {
        method: "POST",
        body: JSON.stringify({ uniqueKey, type, content, tags }),
    });
}

export function searchItems(tags) {
    const params = tags.map(t => `tags=${encodeURIComponent(t)}`).join("&");
    return apiFetch(`/search?${params}`);
}

export async function signUp(name, email, password) {
    const { token, identity } = await apiFetch("/auth/signup", {
        method: "POST",
        body: JSON.stringify({ name, email, password }),
    });
    setToken(token);
    return identity;
}

export async function logIn(email, password) {
    const { token, identity } = await apiFetch("/auth/login", {
        method: "POST",
        body: JSON.stringify({ email, password }),
    });
    setToken(token);
    return identity;
}

export function getCurrentIdentity() {
    return apiFetch("/auth/me");
}

export async function logOut() {
    try {
        await apiFetch("/auth/logout", { method: "POST" });
    } finally {
        setToken(null);
    }
}
