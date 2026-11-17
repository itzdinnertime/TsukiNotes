import { useEffect, useState } from "react";
import { createItem, listItems } from "./api";
import ItemCard from "./ItemCard";
import UploadSection from "./UploadSection";

function AddItemForm({ onCreated, onCancel }) {
    const [title, setTitle] = useState("");
    const [type, setType] = useState("link");
    const [content, setContent] = useState("");
    const [tagsInput, setTagsInput] = useState("");
    const [error, setError] = useState("");
    const [saving, setSaving] = useState(false);

    async function handleSubmit(e) {
        e.preventDefault();
        setSaving(true);
        try {
            const tags = tagsInput.split(",").map(t => t.trim()).filter(Boolean);
            await createItem(title, type, content, tags);
            onCreated();
        } catch (err) {
            setError(err.message);
            setSaving(false);
        }
    }

    return (
        <form onSubmit={handleSubmit}>
            <h3>Add item</h3>
            <input required value={title} onChange={e => setTitle(e.target.value)} placeholder="Title" />
            <select value={type} onChange={e => setType(e.target.value)}>
                <option value="link">link</option>
                <option value="note">note</option>
                <option value="image">image</option>
                <option value="video">video</option>
            </select>
            <input required value={content} onChange={e => setContent(e.target.value)}
                   placeholder={type === "note" ? "Note" : "URL"} />
            <input value={tagsInput} onChange={e => setTagsInput(e.target.value)} placeholder="Tags, comma separated" />
            {error && <p className="error">{error}</p>}
            <div className="form-actions">
                <button type="submit" disabled={saving}>{saving ? "Saving…" : "Save"}</button>
                <button type="button" className="secondary" onClick={onCancel}>Cancel</button>
            </div>
        </form>
    );
}

export default function FilesTab() {
    const [items, setItems] = useState(null);
    const [error, setError] = useState("");
    const [adding, setAdding] = useState(false);

    function loadItems() {
        return listItems()
            .then(found => {
                setItems(found);
                setError("");
            })
            .catch(err => setError(err.message));
    }

    useEffect(() => {
        listItems()
            .then(setItems)
            .catch(err => setError(err.message));
    }, []);

    function handleCreated() {
        setAdding(false);
        loadItems();
    }

    return (
        <section>
            <UploadSection onUploaded={loadItems} />

            <div className="section-header">
                <h3>My files{items ? ` (${items.length})` : ""}</h3>
                {!adding && (
                    <button type="button" className="secondary" onClick={() => setAdding(true)}>
                        + Add link or note
                    </button>
                )}
            </div>

            {adding && <AddItemForm onCreated={handleCreated} onCancel={() => setAdding(false)} />}

            {error && <p className="error">{error}</p>}

            {items === null && !error && <p className="muted">Loading…</p>}
            {items && items.length === 0 && (
                <p className="empty">You haven't added anything yet.</p>
            )}
            {items && items.length > 0 && (
                <ul>
                    {items.map(item => <ItemCard key={item.id} item={item} />)}
                </ul>
            )}
        </section>
    );
}
