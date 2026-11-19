import { useEffect, useState } from "react";
import { createItem, listFolders, listItems } from "./api";
import FolderSection from "./FolderSection";
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

const SEARCH_DELAY_MS = 250;

export default function FilesTab() {
    const [items, setItems] = useState(null);
    const [folders, setFolders] = useState([]);
    const [selectedFolderId, setSelectedFolderId] = useState(null);
    const [query, setQuery] = useState("");
    const [error, setError] = useState("");
    const [adding, setAdding] = useState(false);
    // Bumped after any change (upload, new folder, ...) to re-fetch items and folder counts.
    const [version, setVersion] = useState(0);
    const refresh = () => setVersion(v => v + 1);

    useEffect(() => {
        let cancelled = false;
        listFolders()
            .then(found => { if (!cancelled) setFolders(found); })
            .catch(err => { if (!cancelled) setError(err.message); });
        return () => { cancelled = true; };
    }, [version]);

    useEffect(() => {
        let cancelled = false;
        // Wait for a pause in typing before searching; switching folders loads right away.
        const timer = setTimeout(() => {
            listItems({ query, folderId: selectedFolderId })
                .then(found => {
                    if (cancelled) return;
                    setItems(found);
                    setError("");
                })
                .catch(err => { if (!cancelled) setError(err.message); });
        }, query.trim() ? SEARCH_DELAY_MS : 0);
        return () => {
            cancelled = true;
            clearTimeout(timer);
        };
    }, [query, selectedFolderId, version]);

    function handleCreated() {
        setAdding(false);
        refresh();
    }

    const selectedFolder = folders.find(f => f.id === selectedFolderId);
    const filtering = query.trim() !== "" || selectedFolderId != null;
    const listTitle = selectedFolder ? selectedFolder.name : "All files";

    return (
        <section>
            <UploadSection folders={folders} currentFolderId={selectedFolderId} onUploaded={refresh} />

            <div className="files-search">
                <input
                    type="search"
                    value={query}
                    onChange={e => setQuery(e.target.value)}
                    placeholder="Search your files by title, filename, tag or the text inside them"
                    aria-label="Search your files"
                />
            </div>

            <FolderSection
                folders={folders}
                selectedId={selectedFolderId}
                onSelect={setSelectedFolderId}
                onChanged={refresh}
            />

            <div className="section-header">
                <h3>{listTitle}{items ? ` (${items.length})` : ""}</h3>
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
                <p className="empty">
                    {query.trim()
                        ? `Nothing matches "${query.trim()}"${selectedFolder ? ` in ${selectedFolder.name}` : ""}.`
                        : filtering
                            ? "This folder is empty. Choose it when uploading to add files here."
                            : "You haven't added anything yet."}
                </p>
            )}
            {items && items.length > 0 && (
                <ul>
                    {items.map(item => (
                        <ItemCard key={item.id} item={item} folders={folders} onChanged={refresh}
                                  highlight={query} />
                    ))}
                </ul>
            )}
        </section>
    );
}
