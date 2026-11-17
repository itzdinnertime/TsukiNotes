import { useEffect, useState } from "react";
import { createItem, getCurrentIdentity, getToken, logOut, searchItems, setUnauthorizedHandler } from "./api";
import AuthPage from "./AuthPage";

function App() {
    const [identity, setIdentity] = useState(null);
    // If a token is saved from an earlier visit, ask the server whether it's still valid before showing anything.
    const [checkingSession, setCheckingSession] = useState(() => Boolean(getToken()));

    const [uniqueKey, setUniqueKey] = useState("");
    const [type, setType] = useState("link");
    const [content, setContent] = useState("");
    const [tagsInput, setTagsInput] = useState("");

    const [searchTagsInput, setSearchTagsInput] = useState("");
    const [results, setResults] = useState([]);
    const [error, setError] = useState("");

    function resetToLoggedOut() {
        setIdentity(null);
        setResults([]);
        setError("");
    }

    useEffect(() => {
        setUnauthorizedHandler(resetToLoggedOut);

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
        resetToLoggedOut();
    }

    async function handleCreate(e) {
        e.preventDefault();
        try {
            const tags = tagsInput.split(",").map(t => t.trim()).filter(Boolean);
            await createItem(uniqueKey, type, content, tags);
            setUniqueKey("");
            setContent("");
            setTagsInput("");
            setError("");
        } catch (err) {
            setError(err.message);
        }
    }

    async function handleSearch(e) {
        e.preventDefault();
        try {
            const tags = searchTagsInput.split(",").map(t => t.trim()).filter(Boolean);
            const found = await searchItems(tags);
            setResults(found);
            setError("");
        } catch (err) {
            setError(err.message);
        }
    }

    if (checkingSession) {
        return <p className="muted">Loading…</p>;
    }

    if (!identity) {
        return <AuthPage onAuthenticated={setIdentity} />;
    }

    return (
        <div>
            <header className="app-header">
                <h2>Welcome, {identity.name}</h2>
                <button type="button" onClick={handleLogOut}>Log out</button>
            </header>

            <form onSubmit={handleCreate}>
                <h3>Create item</h3>
                <input value={uniqueKey} onChange={e => setUniqueKey(e.target.value)} placeholder="Title" />
                <select value={type} onChange={e => setType(e.target.value)}>
                    <option value="link">link</option>
                    <option value="note">note</option>
                    <option value="image">image</option>
                    <option value="video">video</option>
                </select>
                <input value={content} onChange={e => setContent(e.target.value)} placeholder="content / URL" />
                <input value={tagsInput} onChange={e => setTagsInput(e.target.value)} placeholder="tags, comma separated" />
                <button type="submit">Create</button>
            </form>

            <form onSubmit={handleSearch}>
                <h3>Search</h3>
                <input value={searchTagsInput} onChange={e => setSearchTagsInput(e.target.value)} placeholder="tags, comma separated" />
                <button type="submit">Search</button>
            </form>

            {error && <p style={{ color: "red" }}>{error}</p>}

            <ul>
                {results.map(item => (
                    <li key={item.id}>
                        <strong>{item.uniqueKey}</strong> ({item.type}): {item.content} — tags: {item.tags.join(", ")}
                    </li>
                ))}
            </ul>
        </div>
    );
}

export default App;
