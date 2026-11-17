import { useState } from "react";
import { createFolder, deleteFolder } from "./api";

/**
 * Shows "All files" plus the user's folders. Clicking one filters the file list;
 * folders can be created here, and the open folder can be deleted (its files are kept).
 */
export default function FolderSection({ folders, selectedId, onSelect, onChanged }) {
    const [creating, setCreating] = useState(false);
    const [name, setName] = useState("");
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);

    const selected = folders.find(f => f.id === selectedId) ?? null;

    async function handleCreate(e) {
        e.preventDefault();
        setBusy(true);
        try {
            const folder = await createFolder(name);
            setName("");
            setCreating(false);
            setError("");
            onChanged();
            onSelect(folder.id);
        } catch (err) {
            setError(err.message);
        } finally {
            setBusy(false);
        }
    }

    async function handleDelete() {
        if (!selected) return;
        const ok = window.confirm(`Delete the folder "${selected.name}"? The files in it won't be deleted.`);
        if (!ok) return;
        setBusy(true);
        try {
            await deleteFolder(selected.id);
            setError("");
            onSelect(null);
            onChanged();
        } catch (err) {
            setError(err.message);
        } finally {
            setBusy(false);
        }
    }

    function cancelCreate() {
        setCreating(false);
        setName("");
        setError("");
    }

    return (
        <section className="folder-section" aria-label="Folders">
            <div className="section-header">
                <h3>Folders</h3>
                {!creating && (
                    <button type="button" className="secondary" onClick={() => setCreating(true)}>+ New folder</button>
                )}
            </div>

            {creating && (
                <form className="new-folder-form" onSubmit={handleCreate}>
                    <input
                        autoFocus
                        required
                        maxLength={100}
                        value={name}
                        onChange={e => setName(e.target.value)}
                        placeholder="Folder name, e.g. CS 101"
                        aria-label="New folder name"
                    />
                    <button type="submit" disabled={busy}>Create</button>
                    <button type="button" className="secondary" onClick={cancelCreate}>Cancel</button>
                </form>
            )}

            <div className="folder-list">
                <button
                    type="button"
                    className={selectedId == null ? "folder-chip active" : "folder-chip"}
                    aria-pressed={selectedId == null}
                    onClick={() => onSelect(null)}
                >
                    All files
                </button>
                {folders.map(folder => (
                    <button
                        key={folder.id}
                        type="button"
                        className={folder.id === selectedId ? "folder-chip active" : "folder-chip"}
                        aria-pressed={folder.id === selectedId}
                        onClick={() => onSelect(folder.id)}
                    >
                        <FolderIcon />
                        {folder.name}
                        <span className="folder-count">{folder.itemCount}</span>
                    </button>
                ))}
            </div>

            {folders.length === 0 && !creating && (
                <p className="muted folder-hint">Create folders to group your files, for example one per class.</p>
            )}

            {selected && (
                <div className="folder-actions">
                    <span className="muted">Showing files in <strong>{selected.name}</strong></span>
                    <button type="button" className="link-button danger" onClick={handleDelete} disabled={busy}>
                        Delete folder
                    </button>
                </div>
            )}

            {error && <p className="error">{error}</p>}
        </section>
    );
}

export function FolderIcon() {
    return (
        <svg className="folder-icon" viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
            <path fill="currentColor" d="M1.5 3A1.5 1.5 0 0 1 3 1.5h3.4l1.6 1.6H13A1.5 1.5 0 0 1 14.5 4.6v7.9A1.5 1.5 0 0 1 13 14H3a1.5 1.5 0 0 1-1.5-1.5z" />
        </svg>
    );
}
