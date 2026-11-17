import { useRef, useState } from "react";
import { MAX_UPLOAD_BYTES, uploadFile } from "./api";
import { formatBytes } from "./format";

// Keep in sync with FileClassifier.java on the server.
const ACCEPTED = [
    ".png", ".jpg", ".jpeg", ".gif", ".webp",
    ".txt", ".md",
    ".py", ".js", ".jsx", ".mjs", ".ts", ".tsx", ".java", ".kt", ".c", ".h", ".cpp", ".cc", ".hpp",
    ".cs", ".go", ".rs", ".rb", ".php", ".swift", ".lua", ".sql", ".sh", ".bash", ".ps1",
    ".html", ".css", ".json", ".yaml", ".yml", ".xml",
].join(",");

export default function UploadSection({ folders, currentFolderId, onUploaded }) {
    const inputRef = useRef(null);
    const [file, setFile] = useState(null);
    const [title, setTitle] = useState("");
    const [tagsInput, setTagsInput] = useState("");
    const [folderIds, setFolderIds] = useState([]);
    const [dragging, setDragging] = useState(false);
    const [error, setError] = useState("");
    const [uploading, setUploading] = useState(false);

    function chooseFile(chosen) {
        if (!chosen) return;
        if (chosen.size > MAX_UPLOAD_BYTES) {
            setError(`${chosen.name} is ${formatBytes(chosen.size)}. Files can be at most 10 MB.`);
            return;
        }
        setFile(chosen);
        // Start with the folder that's open below, if any.
        if (!file) setFolderIds(currentFolderId != null ? [currentFolderId] : []);
        setError("");
    }

    function toggleFolder(id) {
        setFolderIds(ids => (ids.includes(id) ? ids.filter(x => x !== id) : [...ids, id]));
    }

    function reset() {
        setFile(null);
        setTitle("");
        setTagsInput("");
        setFolderIds([]);
        setError("");
        if (inputRef.current) inputRef.current.value = "";
    }

    function handleDrop(e) {
        e.preventDefault();
        setDragging(false);
        chooseFile(e.dataTransfer.files[0]);
    }

    async function handleSubmit(e) {
        e.preventDefault();
        if (!file) {
            setError("Choose a file first");
            return;
        }
        // Ignore folders that were deleted while the form was open.
        const validFolderIds = folderIds.filter(id => folders.some(f => f.id === id));
        // Every upload must go in a folder (the server enforces this too).
        if (validFolderIds.length === 0) {
            setError("Choose at least one folder for this file");
            return;
        }
        setUploading(true);
        try {
            const tags = tagsInput.split(",").map(t => t.trim()).filter(Boolean);
            await uploadFile(file, title.trim(), tags, validFolderIds);
            reset();
            onUploaded();
        } catch (err) {
            setError(err.message);
        } finally {
            setUploading(false);
        }
    }

    return (
        <form className="upload-section" onSubmit={handleSubmit}>
            <h3>Upload a file</h3>

            <label
                className={dragging ? "dropzone dragging" : "dropzone"}
                onDragOver={e => { e.preventDefault(); setDragging(true); }}
                onDragLeave={() => setDragging(false)}
                onDrop={handleDrop}
            >
                <input
                    ref={inputRef}
                    type="file"
                    accept={ACCEPTED}
                    className="visually-hidden"
                    onChange={e => chooseFile(e.target.files[0])}
                />
                {file ? (
                    <span><strong>{file.name}</strong> · {formatBytes(file.size)}</span>
                ) : (
                    <span>
                        <strong>Click to choose</strong> or drag a file here
                        <br />
                        <span className="muted">Images, text files and code files, up to 10 MB</span>
                    </span>
                )}
            </label>

            {file && (
                <>
                    <input value={title} onChange={e => setTitle(e.target.value)}
                           placeholder={`Title (defaults to ${file.name})`} />
                    <input value={tagsInput} onChange={e => setTagsInput(e.target.value)}
                           placeholder="Extra tags, comma separated (the file type is tagged automatically)" />
                    <fieldset className="folder-picker">
                        <legend>
                            Add to folders <span className="required-note">(required, choose at least one)</span>
                        </legend>
                        {folders.length === 0 ? (
                            <span className="muted">
                                No folders yet. Every upload goes in a folder, so create one in the
                                Folders section below first.
                            </span>
                        ) : (
                            <div className="folder-list">
                                {folders.map(folder => {
                                    const on = folderIds.includes(folder.id);
                                    return (
                                        <button
                                            key={folder.id}
                                            type="button"
                                            className={on ? "folder-chip active" : "folder-chip"}
                                            aria-pressed={on}
                                            onClick={() => toggleFolder(folder.id)}
                                        >
                                            {on ? "✓ " : ""}{folder.name}
                                        </button>
                                    );
                                })}
                            </div>
                        )}
                    </fieldset>
                </>
            )}

            {error && <p className="error">{error}</p>}

            {file && (
                <div className="form-actions">
                    <button type="submit" disabled={uploading}>{uploading ? "Uploading…" : "Upload"}</button>
                    <button type="button" className="secondary" onClick={reset} disabled={uploading}>Cancel</button>
                </div>
            )}
        </form>
    );
}
