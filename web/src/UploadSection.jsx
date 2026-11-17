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

export default function UploadSection({ onUploaded }) {
    const inputRef = useRef(null);
    const [file, setFile] = useState(null);
    const [title, setTitle] = useState("");
    const [tagsInput, setTagsInput] = useState("");
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
        setError("");
    }

    function reset() {
        setFile(null);
        setTitle("");
        setTagsInput("");
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
        setUploading(true);
        try {
            const tags = tagsInput.split(",").map(t => t.trim()).filter(Boolean);
            await uploadFile(file, title.trim(), tags);
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
