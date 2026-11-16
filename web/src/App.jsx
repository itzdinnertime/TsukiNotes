import { useState } from "react";
import { createItem, searchItems } from "./api";
import AuthPage from "./AuthPage";

const IDENTITY_STORAGE_KEY = "tsukinotes.identity";

function loadStoredIdentity() {
    try {
        return JSON.parse(localStorage.getItem(IDENTITY_STORAGE_KEY));
    } catch {
        return null;
    }
}

function App() {
    const [identity, setIdentity] = useState(loadStoredIdentity);

    const [uniqueKey, setUniqueKey] = useState("");
    const [type, setType] = useState("link");
    const [content, setContent] = useState("");
    const [tagsInput, setTagsInput] = useState("");

    const [searchTagsInput, setSearchTagsInput] = useState("");
    const [results, setResults] = useState([]);
    const [error, setError] = useState("");

    function handleAuthenticated(id) {
        localStorage.setItem(IDENTITY_STORAGE_KEY, JSON.stringify(id));
        setIdentity(id);
    }

    function handleLogOut() {
        localStorage.removeItem(IDENTITY_STORAGE_KEY);
        setIdentity(null);
        setResults([]);
        setError("");
    }

    async function handleCreate(e) {
        e.preventDefault();
        try {
            const tags = tagsInput.split(",").map(t => t.trim()).filter(Boolean);
            await createItem(identity.id, uniqueKey, type, content, tags);
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

    if (!identity) {
        return <AuthPage onAuthenticated={handleAuthenticated} />;
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
