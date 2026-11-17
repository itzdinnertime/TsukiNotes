import { useState } from "react";
import { deleteItem, setItemFolders } from "./api";
import FilePreview from "./FilePreview";
import { FolderIcon } from "./FolderSection";
import { formatBytes } from "./format";

// Only allow http(s) URLs so a stored "javascript:..." value can't run when clicked.
function safeUrl(value) {
    try {
        const url = new URL(value);
        return url.protocol === "http:" || url.protocol === "https:" ? url.href : null;
    } catch {
        return null;
    }
}

function ItemContent({ type, content }) {
    const url = safeUrl(content);

    if (type === "image" && url) {
        return <img className="item-media" src={url} alt="" loading="lazy" />;
    }
    if (type === "video" && url) {
        return <video className="item-media" src={url} controls preload="metadata" />;
    }
    if (url) {
        return <a href={url} target="_blank" rel="noopener noreferrer">{content}</a>;
    }
    return <p className="item-text">{content}</p>;
}

function FolderEditor({ item, folders, onSaved, onCancel }) {
    const [selected, setSelected] = useState(item.folderIds ?? []);
    const [error, setError] = useState("");
    const [saving, setSaving] = useState(false);
    // Uploaded files must always be in a folder; links and notes don't have to be.
    const needsFolder = item.hasFile;

    function toggle(id) {
        setSelected(ids => (ids.includes(id) ? ids.filter(x => x !== id) : [...ids, id]));
    }

    async function save() {
        if (needsFolder && selected.length === 0) {
            setError("Uploaded files must be in at least one folder");
            return;
        }
        setSaving(true);
        try {
            await setItemFolders(item.id, selected);
            onSaved();
        } catch (err) {
            setError(err.message);
            setSaving(false);
        }
    }

    return (
        <div className="folder-editor">
            <span className="muted">
                Choose the folders for this {item.hasFile ? "file" : "item"}
                {needsFolder ? " (at least one)" : ""}:
            </span>
            {folders.length === 0 ? (
                <span className="muted">You don't have any folders yet.</span>
            ) : (
                <div className="folder-list">
                    {folders.map(folder => {
                        const on = selected.includes(folder.id);
                        return (
                            <button
                                key={folder.id}
                                type="button"
                                className={on ? "folder-chip active" : "folder-chip"}
                                aria-pressed={on}
                                onClick={() => toggle(folder.id)}
                            >
                                {on ? "✓ " : ""}{folder.name}
                            </button>
                        );
                    })}
                </div>
            )}
            {error && <p className="error">{error}</p>}
            <div className="form-actions">
                <button type="button" onClick={save} disabled={saving}>{saving ? "Saving…" : "Save"}</button>
                <button type="button" className="secondary" onClick={onCancel} disabled={saving}>Cancel</button>
            </div>
        </div>
    );
}

/**
 * One item. When `onChanged` is given (the My files tab), it also offers
 * moving the item between folders and deleting it.
 */
export default function ItemCard({ item, folders = [], onChanged }) {
    const [editingFolders, setEditingFolders] = useState(false);
    const [deleting, setDeleting] = useState(false);
    const [error, setError] = useState("");

    const created = item.createdAt ? new Date(item.createdAt).toLocaleDateString() : null;
    const details = [item.hasFile && item.sizeBytes != null ? formatBytes(item.sizeBytes) : null, created]
        .filter(Boolean)
        .join(" · ");
    const folderNames = (item.folderIds ?? [])
        .map(id => folders.find(f => f.id === id)?.name)
        .filter(Boolean);

    async function handleDelete() {
        const what = item.hasFile ? `the file "${item.uniqueKey}"` : `"${item.uniqueKey}"`;
        if (!window.confirm(`Delete ${what}? This can't be undone.`)) return;

        // Hide the card straight away; the list refreshes once the server confirms.
        setDeleting(true);
        setError("");
        try {
            await deleteItem(item.id);
        } catch (err) {
            // Already gone (e.g. deleted in another tab) is what we wanted anyway.
            if (err.message !== "Item not found") {
                setDeleting(false);
                setError(`Couldn't delete: ${err.message}`);
                return;
            }
        }
        onChanged();
    }

    if (deleting) {
        return null;
    }

    return (
        <li className="item-card">
            <div className="item-header">
                <strong>{item.uniqueKey}</strong>
                <span className="badge">{item.hasFile ? `${item.type} file` : item.type}</span>
            </div>
            {item.hasFile
                ? <FilePreview item={item} />
                : <ItemContent type={item.type} content={item.content} />}
            <div className="item-footer">
                <div className="tag-list">
                    {folderNames.map(name => (
                        <span key={`folder-${name}`} className="folder-tag"><FolderIcon />{name}</span>
                    ))}
                    {item.tags.map(tag => <span key={tag} className="tag">#{tag}</span>)}
                </div>
                {details && <span className="muted">{details}</span>}
            </div>

            {onChanged && editingFolders && (
                <FolderEditor
                    item={item}
                    folders={folders}
                    onSaved={() => { setEditingFolders(false); onChanged(); }}
                    onCancel={() => setEditingFolders(false)}
                />
            )}

            {onChanged && !editingFolders && (
                <div className="item-actions">
                    <button type="button" className="link-button" onClick={() => setEditingFolders(true)}>
                        <FolderIcon /> Move to folders
                    </button>
                    <button type="button" className="link-button danger" onClick={handleDelete}>
                        Delete
                    </button>
                </div>
            )}

            {error && <p className="error">{error}</p>}
        </li>
    );
}
