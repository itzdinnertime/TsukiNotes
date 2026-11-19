const API_BASE = "http://localhost:8080";
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
    const response = await request(path, options);
    return response.status === 204 ? null : response.json();
}

/** Sends a request with the session token (if logged in) and throws on errors. Returns the raw Response. */
async function request(path, options = {}) {
    const token = getToken();
    const response = await fetch(`${API_BASE}${path}`, {
        ...options,
        headers: {
            // FormData bodies need the browser to set their own multipart Content-Type.
            ...(typeof options.body === "string" ? { "Content-Type": "application/json" } : {}),
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

    return response;
}

export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

/** The user's items. `query` matches title, filename or tag; `folderId` limits to one folder. */
export function listItems({ query = "", folderId = null } = {}) {
    const params = new URLSearchParams();
    if (query.trim()) params.set("q", query.trim());
    if (folderId != null) params.set("folderId", folderId);
    const qs = params.toString();
    return apiFetch(qs ? `/items?${qs}` : "/items");
}

export function uploadFile(file, title, tags, folderIds = []) {
    const form = new FormData();
    form.append("file", file);
    if (title) form.append("title", title);
    if (tags.length > 0) form.append("tags", tags.join(","));
    folderIds.forEach(id => form.append("folderIds", id));
    return apiFetch("/items/upload", { method: "POST", body: form });
}

export function listFolders() {
    return apiFetch("/folders");
}

export function createFolder(name) {
    return apiFetch("/folders", { method: "POST", body: JSON.stringify({ name }) });
}

export function renameFolder(id, name) {
    return apiFetch(`/folders/${id}`, { method: "PATCH", body: JSON.stringify({ name }) });
}

export function deleteFolder(id) {
    return apiFetch(`/folders/${id}`, { method: "DELETE" });
}

export function deleteItem(id) {
    return apiFetch(`/items/${id}`, { method: "DELETE" });
}

/** Sets exactly which folders an item is in. */
export function setItemFolders(id, folderIds) {
    return apiFetch(`/items/${id}/folders`, { method: "PUT", body: JSON.stringify({ folderIds }) });
}

/** Downloads an uploaded file (with the session token, which <img src> can't send) as a Blob. */
export async function fetchFile(itemId) {
    const response = await request(`/items/${itemId}/file`);
    return response.blob();
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

/** Asks for a reset email. Succeeds whether or not the email has an account (by design). */
export function requestPasswordReset(email) {
    return apiFetch("/auth/password-reset/request", { method: "POST", body: JSON.stringify({ email }) });
}

/** Sets a new password using the token from the emailed link. Logs the user out everywhere. */
export async function resetPassword(token, password) {
    await apiFetch("/auth/password-reset/confirm", { method: "POST", body: JSON.stringify({ token, password }) });
    setToken(null); // the server ended every session, including any saved in this browser
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
