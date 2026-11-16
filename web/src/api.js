const API_BASE = "http://localhost:8080";
const INVITE_TOKEN = "tsuki-secret-6767";

async function apiFetch(path, options = {}) {
    const response = await fetch(`${API_BASE}${path}`, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            "X-Invite-Token": INVITE_TOKEN,
            ...options.headers,
        },
    });

    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || `Request failed: ${response.status}`);
    }

    return response.json();
}

export function createItem(identityId, uniqueKey, type, content, tags) {
    return apiFetch("/items", {
        method: "POST",
        body: JSON.stringify({ identityId, uniqueKey, type, content, tags }),
    });
}

export function searchItems(tags) {
    const params = tags.map(t => `tags=${encodeURIComponent(t)}`).join("&");
    return apiFetch(`/search?${params}`);
}

export function signUp(name, email, password) {
    return apiFetch("/auth/signup", {
        method: "POST",
        body: JSON.stringify({ name, email, password }),
    });
}

export function logIn(email, password) {
    return apiFetch("/auth/login", {
        method: "POST",
        body: JSON.stringify({ email, password }),
    });
}
