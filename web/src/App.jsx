import { useState } from "react";
import { createItem, searchItems, pickIdentity } from "./api";

function App() {
    const [identity, setIdentity] = useState(null);
    const [nameInput, setNameInput] = useState("");

    const [uniqueKey, setUniqueKey] = useState("");
    const [type, setType] = useState("link");
    const [content, setContent] = useState("");
    const [tagsInput, setTagsInput] = useState("");

    const [searchTagsInput, setSearchTagsInput] = useState("");
    const [results, setResults] = useState([]);
    const [error, setError] = useState("");

    async function handlePickName(e) {
        e.preventDefault();
        try {
            const id = await pickIdentity(nameInput);
            setIdentity(id);
            setError("");
        } catch (err) {
            setError(err.message);
        }
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
        return (
            <form onSubmit={handlePickName}>
                <h2>Pick a name</h2>
                <input value={nameInput} onChange={e => setNameInput(e.target.value)} placeholder="your name" />
                <button type="submit">Enter</button>
                {error && <p style={{ color: "red" }}>{error}</p>}
            </form>
        );
    }

    return (
        <div>
            <h2>Welcome, {identity.name}</h2>

            <form onSubmit={handleCreate}>
                <h3>Create item</h3>
                <input value={uniqueKey} onChange={e => setUniqueKey(e.target.value)} placeholder="unique key" />
                <select value={type} onChange={e => setType(e.target.value)}>
                    <option value="link">link</option>
                    <option value="note">note</option>
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
